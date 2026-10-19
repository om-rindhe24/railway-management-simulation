package com.railway.simulator.service;

import com.railway.simulator.algorithm.dto.ReallocationResult;
import com.railway.simulator.config.PlatformLineConfig;
import com.railway.simulator.domain.*;
import com.railway.simulator.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
public class PlatformClosureAndFleetTurnaroundIntegrationTest {

    @Autowired
    private SimulationService simulationService;

    @Autowired
    private AllocationService allocationService;

    @Autowired
    private PlatformService platformService;

    @Autowired
    private PlatformRepository platformRepository;

    @Autowired
    private TrainRepository trainRepository;

    @Autowired
    private PlatformAllocationRepository allocationRepository;

    @Autowired
    private AllocationHistoryRepository historyRepository;

    @Autowired
    private SimulationEventRepository eventRepository;

    @BeforeEach
    void setup() {
        simulationService.resetSimulation();
        allocationService.runStationAllocation("BVI");
        allocationService.runStationAllocation("AND");
        allocationService.runStationAllocation("DDR");
        simulationService.syncSignallingState();
    }

    @Test
    @DisplayName("Part 3a: Approaching train reallocated to best open compatible platform, signals and blocks updated")
    void test3a_approachingTrainReallocatedToBestCompatiblePlatform() {
        // Find train approaching BVI on Fast line
        Train train = trainRepository.findByTrainNumber("SIM-LOC-901").orElseThrow();
        assertEquals("BVI", train.getTargetStationCode());

        Platform p5 = platformRepository.findByStationCodeAndPlatformNumber("BVI", 5).orElseThrow();
        Platform p7 = platformRepository.findByStationCodeAndPlatformNumber("BVI", 7).orElseThrow();
        assertEquals(LineType.FAST_UP, p5.getLineType());
        assertEquals(LineType.FAST_UP, p7.getLineType());

        // Initially assign to P5
        train.setAssignedPlatformId(p5.getPlatformId());
        train.setAssignedPlatformNumber(5);
        train.setStatus(TrainStatus.APPROACHING);
        trainRepository.save(train);

        // Close P5 via the real close-platform service/endpoint used by UI
        ReallocationResult result = allocationService.handlePlatformClosure(p5.getPlatformId(), "Emergency maintenance on P5");
        assertNotNull(result);

        // Advance simulation tick
        simulationService.advanceTrainStates(5L);

        Train updated = trainRepository.findByTrainNumber("SIM-LOC-901").orElseThrow();
        // Must be reallocated to P7 (best open compatible platform: same direction UP and line type FAST_UP)
        assertEquals(7, updated.getAssignedPlatformNumber(), "Train should reallocate to P7 (same direction and FAST line type)");
        assertEquals(p7.getPlatformId(), updated.getAssignedPlatformId());

        // Check home signal, starter signal and expected block
        assertEquals("SIG-BVI-P7", updated.getNextSignalId());
        assertEquals(SignalAspect.GREEN, updated.getSignalAspect());

        // Check audit entry in AllocationHistory and SimulationEvents
        List<SimulationEvent> events = eventRepository.findAll();
        boolean hasReallocEvent = events.stream().anyMatch(e ->
                "TRAIN_REALLOCATED".equals(e.getEventType()) &&
                "SIM-LOC-901".equals(e.getAffectedTrainNumber()) &&
                Integer.valueOf(7).equals(e.getAffectedPlatformNumber()));
        assertTrue(hasReallocEvent, "Must log TRAIN_REALLOCATED event with from-platform and to-platform");

        List<AllocationHistory> hist = historyRepository.findAll();
        boolean hasReallocHist = hist.stream().anyMatch(h ->
                h.getChangeType() == ChangeType.TRAIN_REALLOCATED &&
                "SIM-LOC-901".equals(h.getTrainNumber()) &&
                Integer.valueOf(7).equals(h.getToPlatformNumber()));
        assertTrue(hasReallocHist, "Must log TRAIN_REALLOCATED in AllocationHistory table for UI audit log");

        // Now test fallback to another line type if ALL FAST_UP platforms are closed
        allocationService.handlePlatformClosure(p7.getPlatformId(), "Maintenance on P7 as well");
        simulationService.advanceTrainStates(5L);

        Train fallbackTrain = trainRepository.findByTrainNumber("SIM-LOC-901").orElseThrow();
        // With P5 and P7 closed, must fallback to open UP platform on slow line (P1 or P2)
        assertTrue(fallbackTrain.getAssignedPlatformNumber() == 1 || fallbackTrain.getAssignedPlatformNumber() == 2,
                "Must fallback to open UP platform on slow line (P1 or P2) when all FAST_UP are closed");
    }

    @Test
    @DisplayName("Part 3b: No open compatible platform holds train at home signal (RED, 0 km/h) with TRAIN_HELD, resumes on reopen")
    void test3b_noOpenCompatiblePlatform_trainHeldAtHomeSignal() {
        Train train = trainRepository.findByTrainNumber("SIM-LOC-901").orElseThrow();
        assertEquals("BVI", train.getTargetStationCode());

        // Close ALL UP platforms at BVI (Platforms 1, 2, 5, 7)
        List<Integer> upPlatforms = List.of(1, 2, 5, 7);
        for (Integer pNum : upPlatforms) {
            Platform p = platformRepository.findByStationCodeAndPlatformNumber("BVI", pNum).orElseThrow();
            platformService.closePlatform(p.getPlatformId(), "Complete UP throat maintenance");
        }

        // Trigger allocation handling
        allocationService.handlePlatformClosure(
                platformRepository.findByStationCodeAndPlatformNumber("BVI", 5).orElseThrow().getPlatformId(),
                "All platforms closed"
        );

        // Advance simulation tick
        simulationService.advanceTrainStates(25L);

        Train heldTrain = trainRepository.findByTrainNumber("SIM-LOC-901").orElseThrow();
        // Train must be HELD at home signal (RED, brakes to 0 km/h)
        assertEquals(TrainStatus.WAITING, heldTrain.getStatus(), "Train must be WAITING when no platforms available");
        assertEquals(0.0, heldTrain.getSpeedKmH(), "Train speed must be 0 km/h while held");
        assertEquals(SignalAspect.RED, heldTrain.getSignalAspect(), "Signal aspect must be RED");
        assertNotNull(heldTrain.getWaitingReason());

        // Verify TRAIN_HELD audit entry in SimulationEvents and AllocationHistory
        List<SimulationEvent> events = eventRepository.findAll();
        boolean hasHeldEvent = events.stream().anyMatch(e ->
                "TRAIN_HELD".equals(e.getEventType()) && "SIM-LOC-901".equals(e.getAffectedTrainNumber()));
        assertTrue(hasHeldEvent, "Must log TRAIN_HELD event when train is held");

        List<AllocationHistory> heldHist = historyRepository.findAll();
        boolean hasHeldHist = heldHist.stream().anyMatch(h ->
                h.getChangeType() == ChangeType.TRAIN_HELD &&
                "SIM-LOC-901".equals(h.getTrainNumber()));
        assertTrue(hasHeldHist, "Must log TRAIN_HELD in AllocationHistory table for UI audit log");

        // Reopen Platform 5
        Platform p5 = platformRepository.findByStationCodeAndPlatformNumber("BVI", 5).orElseThrow();
        Platform opened = platformService.openPlatform(p5.getPlatformId());
        assertFalse(opened.getIsClosed());

        // Next simulation tick: train should resume movement automatically
        simulationService.advanceTrainStates(5L);

        Train resumedTrain = trainRepository.findByTrainNumber("SIM-LOC-901").orElseThrow();
        assertEquals(TrainStatus.APPROACHING, resumedTrain.getStatus(), "Train must resume to APPROACHING when platform reopens");
        assertEquals(SignalAspect.GREEN, resumedTrain.getSignalAspect(), "Signal must clear to GREEN");
        assertNull(resumedTrain.getWaitingReason(), "Waiting reason must be cleared");
    }

    @Test
    @DisplayName("Part 3c: Cascading reallocation never assigns two trains to the same platform at overlapping times")
    void test3c_queuedTrainsCascadeWithoutOverlap() {
        LocalDateTime base = LocalDateTime.of(2026, 9, 18, 10, 10);

        Train t1 = new Train("SIM-QUEUE-01", "Queue 1", TrainType.LOCAL, "VR", "CCG", 5,
                base, base.plusMinutes(5), "BVI", "CCG");
        t1.setActualArrival(base);
        t1.setActualDeparture(base.plusMinutes(5));
        t1.setTrainCategory(TrainCategory.FAST_LOCAL);
        t1.setAssignedPlatformNumber(5);
        Platform p5 = platformRepository.findByStationCodeAndPlatformNumber("BVI", 5).orElseThrow();
        t1.setAssignedPlatformId(p5.getPlatformId());
        t1.setStatus(TrainStatus.APPROACHING);
        t1.setIsActive(true);
        trainRepository.save(t1);

        Train t2 = new Train("SIM-QUEUE-02", "Queue 2", TrainType.LOCAL, "VR", "CCG", 4,
                base.plusMinutes(2), base.plusMinutes(7), "BVI", "CCG");
        t2.setActualArrival(base.plusMinutes(2));
        t2.setActualDeparture(base.plusMinutes(7));
        t2.setTrainCategory(TrainCategory.FAST_LOCAL);
        t2.setAssignedPlatformNumber(5);
        t2.setAssignedPlatformId(p5.getPlatformId());
        t2.setStatus(TrainStatus.APPROACHING);
        t2.setIsActive(true);
        trainRepository.save(t2);

        // Close P5
        allocationService.handlePlatformClosure(p5.getPlatformId(), "Cascade test closure");

        Train resT1 = trainRepository.findByTrainNumber("SIM-QUEUE-01").orElseThrow();
        Train resT2 = trainRepository.findByTrainNumber("SIM-QUEUE-02").orElseThrow();

        // If both reallocated, they must NOT be on the same platform because their intervals overlap [10:10-10:15] and [10:12-10:17]
        if (resT1.getAssignedPlatformNumber() != null && resT2.getAssignedPlatformNumber() != null) {
            assertNotEquals(resT1.getAssignedPlatformNumber(), resT2.getAssignedPlatformNumber(),
                    "Overlapping trains must never be assigned to the same platform!");
        }

        // Clean up
        trainRepository.delete(t1);
        trainRepository.delete(t2);
    }

    @Test
    @DisplayName("Part 3d: Train already AT_PLATFORM when closed completes dwell and departs normally")
    void test3d_trainAlreadyAtPlatformWhenClosedDwellsAndDepartsNormally() {
        Train t = trainRepository.findByTrainNumber("SIM-LOC-904").orElseThrow();
        assertEquals(TrainStatus.AT_PLATFORM, t.getStatus());
        assertEquals("BVI", t.getTargetStationCode());
        assertEquals(2, t.getAssignedPlatformNumber());

        Platform p2 = platformRepository.findByStationCodeAndPlatformNumber("BVI", 2).orElseThrow();

        // Close Platform 2 while train is AT_PLATFORM
        platformService.closePlatform(p2.getPlatformId(), "Closure during train dwell");
        assertTrue(platformRepository.findById(p2.getPlatformId()).orElseThrow().getIsClosed());

        // Mid-dwell check: train is still AT_PLATFORM, 0 speed, not teleported or marked WAITING
        simulationService.advanceTrainStates(5L);
        Train midDwell = trainRepository.findByTrainNumber("SIM-LOC-904").orElseThrow();
        assertEquals(TrainStatus.AT_PLATFORM, midDwell.getStatus(), "Train must stay AT_PLATFORM during dwell");
        assertEquals(2, midDwell.getAssignedPlatformNumber());
        assertEquals("BLK-BVI-P2", midDwell.getCurrentBlockId());

        // Advance simulation past scheduled departure (10:08)
        simulationService.setSimulationTime(LocalDateTime.of(2026, 9, 18, 10, 8, 30));
        simulationService.advanceTrainStates(10L);
        simulationService.advanceTrainStates(10L);

        Train departed = trainRepository.findByTrainNumber("SIM-LOC-904").orElseThrow();
        // Train departs normally and progresses into next block or corridor
        assertNotEquals("BLK-BVI-P2", departed.getCurrentBlockId(), "Train must depart from platform block");
        assertTrue(departed.getStatus() == TrainStatus.APPROACHING || departed.getStatus() == TrainStatus.DEPARTED);

        // Platform is released (unoccupied) but remains CLOSED
        Platform p2After = platformRepository.findById(p2.getPlatformId()).orElseThrow();
        assertTrue(p2After.getIsClosed(), "Platform must remain closed");
        assertFalse(p2After.getIsOccupied(), "Platform must be unoccupied after departure");
    }

    @Test
    @DisplayName("Part 3e: Reopening platform lets held trains use it, signal colors match")
    void test3e_reopeningPlatformAllowsUsageAndRestoresSignals() {
        Platform p1 = platformRepository.findByStationCodeAndPlatformNumber("BVI", 1).orElseThrow();
        platformService.closePlatform(p1.getPlatformId(), "Maintenance");

        simulationService.recalculateSignalling();
        Signal homeSig = simulationService.getSignals().get("SIG-BVI-P1");
        assertNotNull(homeSig);
        assertEquals(SignalAspect.RED, homeSig.getCurrentAspect(), "Home signal must be RED when platform is closed");

        // Reopen platform
        platformService.openPlatform(p1.getPlatformId());
        Platform reopened = platformRepository.findById(p1.getPlatformId()).orElseThrow();
        assertFalse(reopened.getIsClosed(), "Platform must be marked not closed");

        simulationService.recalculateSignalling();
        Signal homeSigAfter = simulationService.getSignals().get("SIG-BVI-P1");
        assertEquals(SignalAspect.GREEN, homeSigAfter.getCurrentAspect(), "Home signal must be GREEN after reopening");
    }

    @Test
    @DisplayName("Part 4: Fleet turnaround keeps >= 24 active trains after 2 simulated hours, no wrong line type, no teleporting")
    void test4_fleetTurnaroundAnd2HourSimulation() {
        // Reset simulation to baseline (10:00:00)
        simulationService.resetSimulation();
        allocationService.runStationAllocation("BVI");
        allocationService.runStationAllocation("AND");
        allocationService.runStationAllocation("DDR");
        simulationService.syncSignallingState();

        int initialActive = trainRepository.findByIsActiveTrue().size();
        assertEquals(27, initialActive, "Initial active fleet must be 27");

        // Record initial categories
        Map<String, TrainCategory> categories = new HashMap<>();
        for (Train t : trainRepository.findByIsActiveTrue()) {
            categories.put(t.getTrainNumber(), t.getTrainCategory());
        }

        // Run 2 simulated hours: 120 minutes = 7200 seconds
        // Using ticks of 25 seconds = 288 ticks
        double maxProgressDeltaPerTick = 0.0;
        int ticks = 288;
        for (int i = 0; i < ticks; i++) {
            Map<String, Double> progressBefore = new HashMap<>();
            Map<String, String> blockBefore = new HashMap<>();
            for (Train t : trainRepository.findByIsActiveTrue()) {
                progressBefore.put(t.getTrainNumber(), t.getProgressPercent());
                blockBefore.put(t.getTrainNumber(), t.getCurrentBlockId());
            }

            simulationService.advanceTrainStates(25L);

            // Verify no train teleports: within the same block, max delta per 25s at 100 km/h on a 2km block is ~35%
            for (Train t : trainRepository.findByIsActiveTrue()) {
                String oldB = blockBefore.get(t.getTrainNumber());
                Double oldP = progressBefore.get(t.getTrainNumber());
                if (t.getStatus() == TrainStatus.APPROACHING && oldB != null && oldB.equals(t.getCurrentBlockId()) && oldP != null && t.getProgressPercent() != null) {
                    double delta = t.getProgressPercent() - oldP;
                    if (delta > maxProgressDeltaPerTick) {
                        maxProgressDeltaPerTick = delta;
                    }
                    assertTrue(delta <= 40.0, "Train " + t.getTrainNumber() + " moved " + delta + "% in 25s, exceeding physical limits!");
                }
            }
        }

        // Verify active fleet after 2 simulated hours
        List<Train> activeAfter2Hours = trainRepository.findByIsActiveTrue();
        int activeCount = activeAfter2Hours.size();
        System.out.println("Active fleet after 2 simulated hours: " + activeCount + " / " + initialActive);
        assertTrue(activeCount >= 24, "At least 24 of 27 trains must still be active after 2 simulated hours! Found: " + activeCount);

        // Verify no train on wrong line type (slow stays slow, fast stays fast)
        for (Train t : activeAfter2Hours) {
            TrainCategory initialCat = categories.get(t.getTrainNumber());
            assertEquals(initialCat, t.getTrainCategory(),
                    "Train " + t.getTrainNumber() + " category must be preserved: slow stays slow, fast stays fast");
            String blkId = t.getCurrentBlockId();
            if (blkId != null && blkId.length() >= 2) {
                String lineCode = blkId.substring(blkId.length() - 2);
                if (initialCat == TrainCategory.SLOW_LOCAL) {
                    assertFalse("FU".equals(lineCode) || "FD".equals(lineCode),
                            "Slow local " + t.getTrainNumber() + " must not run on fast line block " + blkId);
                }
            }
        }
    }

    @Test
    @DisplayName("Step 1: Every reallocated train across all stations strictly gets a platform of its own direction")
    void testDirectionPreservationAcrossAllStationsOnPlatformClosure() {
        // Specifically test SIM-EXP-19015 at Bandra (BA)
        Train simExp = trainRepository.findByTrainNumber("SIM-EXP-19015").orElseThrow();
        assertEquals("UP", simExp.getDirection());
        Platform baP5 = platformRepository.findByStationCodeAndPlatformNumber("BA", 5).orElseThrow();
        simExp.setTargetStationCode("BA");
        simExp.setAssignedPlatformId(baP5.getPlatformId());
        simExp.setAssignedPlatformNumber(5);
        simExp.setStatus(TrainStatus.APPROACHING);
        trainRepository.save(simExp);

        // Close BA P5 (FAST_UP)
        allocationService.handlePlatformClosure(baP5.getPlatformId(), "BA P5 closure");
        Train reallocatedExp = trainRepository.findByTrainNumber("SIM-EXP-19015").orElseThrow();
        if (reallocatedExp.getAssignedPlatformNumber() != null) {
            LineType assignedLine = PlatformLineConfig.getPlatformLineType("BA", reallocatedExp.getAssignedPlatformNumber());
            String assignedDir = PlatformLineConfig.getDirection(assignedLine);
            assertEquals("UP", assignedDir, "SIM-EXP-19015 (UP) must reallocate to an UP platform (e.g. P1 or P2), NEVER a DOWN platform like P6!");
            System.out.println("CORRECTED SIM-EXP-19015 result at BA: reallocated to Platform " + reallocatedExp.getAssignedPlatformNumber() + " (" + assignedLine + ", " + assignedDir + ")");
        }

        // Now test across every station: close each platform in turn and assert any reallocated train gets a platform of its own direction
        List<Platform> allPlatforms = platformRepository.findAll();
        for (Platform p : allPlatforms) {
            String stn = p.getStationCode();
            int pNum = p.getPlatformNumber();
            Long pId = p.getPlatformId();

            // Create test train approaching this station and assigned to this platform
            String trainDir = PlatformLineConfig.getDirection(p.getLineType());
            String src = "UP".equals(trainDir) ? "VR" : "MMCT";
            String dest = "UP".equals(trainDir) ? "MMCT" : "VR";
            LocalDateTime now = LocalDateTime.of(2026, 9, 18, 10, 0);

            Train testTrain = new Train("SIM-TEST-DIR-" + stn + "-P" + pNum, "Dir Test", TrainType.LOCAL, src, dest, 5,
                    now.plusMinutes(2), now.plusMinutes(6), stn, dest);
            testTrain.setDirection(trainDir);
            testTrain.setTargetStationCode(stn);
            testTrain.setAssignedPlatformId(pId);
            testTrain.setAssignedPlatformNumber(pNum);
            testTrain.setStatus(TrainStatus.APPROACHING);
            testTrain.setIsActive(true);
            testTrain = trainRepository.save(testTrain);

            try {
                allocationService.handlePlatformClosure(pId, "Test closure for direction check");
                Train updatedTestTrain = trainRepository.findById(testTrain.getTrainId()).orElseThrow();
                if (updatedTestTrain.getAssignedPlatformNumber() != null) {
                    LineType assignedLine = PlatformLineConfig.getPlatformLineType(stn, updatedTestTrain.getAssignedPlatformNumber());
                    String assignedDir = PlatformLineConfig.getDirection(assignedLine);
                    assertEquals(trainDir, assignedDir,
                            "Station " + stn + " Platform " + pNum + " closure: train " + updatedTestTrain.getTrainNumber() +
                            " direction (" + trainDir + ") must match assigned platform direction (" + assignedDir + ")");
                }
            } finally {
                // Reopen and cleanup
                platformService.openPlatform(pId);
                trainRepository.delete(testTrain);
            }
        }
    }

    @Test
    @DisplayName("Verify explicit reallocation reason and persistence of TRAIN_REALLOCATED and TRAIN_HELD in AllocationHistory and SimulationEvents")
    void testExplicitReasonAndAuditPersistence_BVI_P1_Closure() {
        Platform p1 = platformRepository.findByStationCodeAndPlatformNumber("BVI", 1).orElseThrow();
        Train train = new Train("SIM-TEST-P1", "Test Slow Local", TrainType.LOCAL, "VR", "CCG", 5,
                LocalDateTime.of(2026, 9, 18, 10, 5), LocalDateTime.of(2026, 9, 18, 10, 10), "BVI", "CCG");
        train.setDirection("UP");
        train.setTrainCategory(TrainCategory.SLOW_LOCAL);
        train.setTargetStationCode("BVI");
        train.setAssignedPlatformId(p1.getPlatformId());
        train.setAssignedPlatformNumber(1);
        train.setStatus(TrainStatus.APPROACHING);
        train.setCurrentBlockId("BLK-DIC-BVI-SU");
        train.setIsActive(true);
        train = trainRepository.save(train);

        try {
            // Close Platform 1
            allocationService.handlePlatformClosure(p1.getPlatformId(), "Defective track switch at P1");

            Train reallocated = trainRepository.findById(train.getTrainId()).orElseThrow();
            assertEquals(7, reallocated.getAssignedPlatformNumber(), "P1 closed, P2 occupied by SIM-LOC-904 -> must fall back to P7");

            // Verify TRAIN_REALLOCATED in AllocationHistory
            List<AllocationHistory> histList = historyRepository.findByStationCodeOrderByTimestampDesc("BVI");
            Optional<AllocationHistory> reallocEntry = histList.stream()
                    .filter(h -> "SIM-TEST-P1".equals(h.getTrainNumber()) && h.getChangeType() == ChangeType.TRAIN_REALLOCATED)
                    .findFirst();
            assertTrue(reallocEntry.isPresent(), "Must write TRAIN_REALLOCATED record in AllocationHistory");
            assertEquals(1, reallocEntry.get().getFromPlatformNumber());
            assertEquals(7, reallocEntry.get().getToPlatformNumber());
            assertTrue(reallocEntry.get().getReason().contains("P1 closed; P2 (same line) occupied; fell back to P7 (FAST_UP, same direction)"),
                    "Reason must be explicit: " + reallocEntry.get().getReason());

            // Verify TRAIN_REALLOCATED in SimulationEvent
            List<SimulationEvent> eventList = eventRepository.findAll();
            assertTrue(eventList.stream().anyMatch(e -> "TRAIN_REALLOCATED".equals(e.getEventType())
                    && "SIM-TEST-P1".equals(e.getAffectedTrainNumber())
                    && Integer.valueOf(7).equals(e.getAffectedPlatformNumber())));

            // Now close all remaining UP platforms (P2, P5, P7)
            for (int pNum : List.of(2, 5)) {
                Platform p = platformRepository.findByStationCodeAndPlatformNumber("BVI", pNum).orElseThrow();
                platformService.closePlatform(p.getPlatformId(), "Maintenance");
            }
            Platform p7 = platformRepository.findByStationCodeAndPlatformNumber("BVI", 7).orElseThrow();
            allocationService.handlePlatformClosure(p7.getPlatformId(), "All UP platforms closed");

            Train held = trainRepository.findById(train.getTrainId()).orElseThrow();
            assertEquals(TrainStatus.WAITING, held.getStatus());

            // Verify TRAIN_HELD in AllocationHistory
            List<AllocationHistory> heldHistList = historyRepository.findByStationCodeOrderByTimestampDesc("BVI");
            Optional<AllocationHistory> heldEntry = heldHistList.stream()
                    .filter(h -> "SIM-TEST-P1".equals(h.getTrainNumber()) && h.getChangeType() == ChangeType.TRAIN_HELD)
                    .findFirst();
            assertTrue(heldEntry.isPresent(), "Must write TRAIN_HELD record in AllocationHistory");
            assertNull(heldEntry.get().getToPlatformNumber());

            // Verify TRAIN_HELD in SimulationEvent
            assertTrue(eventRepository.findAll().stream().anyMatch(e -> "TRAIN_HELD".equals(e.getEventType())
                    && "SIM-TEST-P1".equals(e.getAffectedTrainNumber())));

        } finally {
            for (int pNum : List.of(1, 2, 5, 7)) {
                platformRepository.findByStationCodeAndPlatformNumber("BVI", pNum)
                        .ifPresent(p -> platformService.openPlatform(p.getPlatformId()));
            }
            trainRepository.delete(train);
        }
    }
}
