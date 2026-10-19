package com.railway.simulator.service;

import com.railway.simulator.algorithm.dto.ReallocationResult;
import com.railway.simulator.domain.*;
import com.railway.simulator.repository.AllocationHistoryRepository;
import com.railway.simulator.repository.PlatformAllocationRepository;
import com.railway.simulator.repository.PlatformRepository;
import com.railway.simulator.repository.TrainRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class PlatformInterlockingSignallingTest {

    @Autowired
    private AllocationService allocationService;

    @Autowired
    private PlatformService platformService;

    @Autowired
    private SimulationService simulationService;

    @Autowired
    private PlatformRepository platformRepository;

    @Autowired
    private TrainRepository trainRepository;

    @Autowired
    private PlatformAllocationRepository allocationRepository;

    @Autowired
    private AllocationHistoryRepository historyRepository;

    @BeforeEach
    void resetBaseline() {
        simulationService.resetSimulation();
        // Re-open all platforms
        List<Platform> platforms = platformRepository.findAll();
        for (Platform p : platforms) {
            p.setIsClosed(false);
            p.setIsOccupied(false);
            p.setClosureReason(null);
            p.setCurrentTrainId(null);
            p.setCurrentTrainNumber(null);
            platformRepository.save(p);
        }
        // Run fresh allocation for Borivali
        allocationService.runStationAllocation("BVI");
    }

    @Test
    @DisplayName("Requirement 1: Closing a platform makes its controlling signal RED with human-readable reason")
    void testClosingPlatformMakesSignalRed() {
        Platform p5 = platformRepository.findByStationCodeAndPlatformNumber("BVI", 5).orElseThrow();
        Signal sig5Before = simulationService.getSignals().get("SIG-BVI-P5");
        assertNotNull(sig5Before);
        assertEquals(SignalAspect.GREEN, sig5Before.getCurrentAspect(), "Initial signal must be GREEN when platform is open");

        // Close Platform 5
        platformService.closePlatform(p5.getPlatformId(), "Maintenance inspection");

        Signal sig5After = simulationService.getSignals().get("SIG-BVI-P5");
        assertEquals(SignalAspect.RED, sig5After.getCurrentAspect(), "Closing platform must turn controlling signal RED");
        assertTrue(sig5After.getAspectReason().contains("CLOSED"), "Aspect reason must indicate CLOSED");
        assertTrue(sig5After.getAspectReason().contains("5"), "Aspect reason must reference platform 5");
    }

    @Test
    @DisplayName("Requirement 2: Approaching train halts before closed platform with clear waiting reason")
    void testApproachingTrainStopsBecausePlatformIsClosed() {
        Platform p5 = platformRepository.findByStationCodeAndPlatformNumber("BVI", 5).orElseThrow();
        Train train = trainRepository.findByTrainNumber("SIM-VB-20901").orElseThrow();

        // Assign train to Platform 5 and set clock in its arrival window
        train.setAssignedPlatformId(p5.getPlatformId());
        train.setAssignedPlatformNumber(5);
        train.setCurrentBlockId("BLK-KND-BVI-SD"); // Block ending at target station BVI
        train.setProgressPercent(98.0);
        trainRepository.save(train);

        simulationService.setSimulationTime(train.getActualArrival().minusMinutes(2)); // Arriving phase

        // Close platform 5 directly in DB to test signal enforcement without auto-reallocation
        p5.setIsClosed(true);
        p5.setClosureReason("Signal maintenance");
        platformRepository.save(p5);

        simulationService.syncSignallingState();

        Train updated = trainRepository.findById(train.getTrainId()).orElseThrow();
        assertEquals(TrainStatus.WAITING, updated.getStatus(), "Approaching train targeting closed platform must become WAITING");
        assertEquals(SignalAspect.RED, updated.getSignalAspect(), "Train signal aspect must be RED");
        assertNotNull(updated.getWaitingReason(), "Waiting reason must be populated");
        assertTrue(updated.getWaitingReason().contains("SIG-BVI-P5"), "Waiting reason must identify signal SIG-BVI-P5");
        assertTrue(updated.getWaitingReason().contains("CLOSED"), "Waiting reason must explain platform is closed");
        assertNotEquals("BLK-BVI-P5", updated.getCurrentBlockId(), "Train must NOT enter closed platform block");
    }

    @Test
    @DisplayName("Requirement 3: Platform closure triggers dynamic reallocation cascade and audit history")
    void testPlatformClosureTriggersReallocation() {
        List<PlatformAllocation> activeAllocs = allocationRepository.findByStationCodeAndStatus("BVI", AllocationStatus.ACTIVE);
        assertFalse(activeAllocs.isEmpty(), "Active allocations must exist at BVI");
        PlatformAllocation target = activeAllocs.get(0);
        Long closedPlatId = target.getPlatformId();

        ReallocationResult result = allocationService.handlePlatformClosure(closedPlatId, "Track defect simulation");

        assertNotNull(result);
        assertTrue(result.getReallocatedCount() >= 1, "At least 1 train must be reallocated from closed platform");

        var history = historyRepository.findByStationCodeOrderByTimestampDesc("BVI");
        assertFalse(history.isEmpty(), "Audit history must record platform closure event");
        assertTrue(history.stream().anyMatch(h -> h.getChangeType() == ChangeType.PLATFORM_CLOSURE),
                "History must include PLATFORM_CLOSURE type");
    }

    @Test
    @DisplayName("Requirement 4: Reallocated train receives a valid, open alternative platform")
    void testReallocatedTrainReceivesValidNewPlatform() {
        List<PlatformAllocation> activeAllocs = allocationRepository.findByStationCodeAndStatus("BVI", AllocationStatus.ACTIVE);
        assertFalse(activeAllocs.isEmpty());
        PlatformAllocation alloc = activeAllocs.get(0);

        Train train = trainRepository.findById(alloc.getTrainId()).orElseThrow();
        Long originalPlatId = alloc.getPlatformId();
        int originalPlatNum = alloc.getPlatformNumber();

        allocationService.handlePlatformClosure(originalPlatId, "Emergency overhead inspection");

        Train updated = trainRepository.findById(train.getTrainId()).orElseThrow();
        assertNotEquals(originalPlatNum, updated.getAssignedPlatformNumber(), "Train must be moved away from closed Platform");
        assertNotNull(updated.getAssignedPlatformNumber(), "Train must have a new platform assignment");

        Platform newPlat = platformRepository.findByStationCodeAndPlatformNumber("BVI", updated.getAssignedPlatformNumber()).orElseThrow();
        assertFalse(newPlat.getIsClosed(), "Newly assigned platform must be open");

        var activeAlloc = allocationRepository.findByTrainIdAndStatus(train.getTrainId(), AllocationStatus.ACTIVE);
        assertTrue(activeAlloc.isPresent(), "An ACTIVE allocation must exist for the reallocated train");
        assertEquals(updated.getAssignedPlatformNumber(), activeAlloc.get().getPlatformNumber());
    }

    @Test
    @DisplayName("Requirement 5: Signal protecting newly reallocated platform evaluates to permissive")
    void testNewPlatformSignalBecomesPermissive() {
        List<PlatformAllocation> activeAllocs = allocationRepository.findByStationCodeAndStatus("BVI", AllocationStatus.ACTIVE);
        assertFalse(activeAllocs.isEmpty());
        PlatformAllocation alloc = activeAllocs.get(0);

        Train train = trainRepository.findById(alloc.getTrainId()).orElseThrow();
        Long originalPlatId = alloc.getPlatformId();

        allocationService.handlePlatformClosure(originalPlatId, "Power line overhaul");

        Train updated = trainRepository.findById(train.getTrainId()).orElseThrow();
        int newPlatNum = updated.getAssignedPlatformNumber();

        Signal newSignal = simulationService.getSignals().get("SIG-BVI-P" + newPlatNum);
        assertNotNull(newSignal, "Signal for reallocated platform must exist");
        assertTrue(newSignal.getCurrentAspect().isPermissive(), "Signal for reallocated open platform must be permissive");
    }

    @Test
    @DisplayName("Requirement 6: Train resumes movement and clears waitingReason after successful reallocation")
    void testTrainResumesAfterSuccessfulReallocation() {
        List<PlatformAllocation> activeAllocs = allocationRepository.findByStationCodeAndStatus("BVI", AllocationStatus.ACTIVE);
        assertFalse(activeAllocs.isEmpty());
        PlatformAllocation alloc = activeAllocs.get(0);

        Train train = trainRepository.findById(alloc.getTrainId()).orElseThrow();
        Platform closedPlatform = platformRepository.findById(alloc.getPlatformId()).orElseThrow();

        // Put train in waiting status on approach to this platform
        train.setCurrentBlockId("BLK-DIC-BVI-SU");
        simulationService.getTrackBlocks().get("BLK-DIC-BVI-SU").vacate();
        train.setStatus(TrainStatus.WAITING);
        train.setSignalAspect(SignalAspect.RED);
        train.setWaitingReason(String.format("Waiting at signal SIG-BVI-P%d: platform closed", closedPlatform.getPlatformNumber()));
        trainRepository.save(train);

        simulationService.setSimulationTime(train.getActualArrival().minusMinutes(2)); // Arrival window

        // Execute reallocation
        allocationService.handlePlatformClosure(closedPlatform.getPlatformId(), "Structural repair");

        Train resumed = trainRepository.findById(train.getTrainId()).orElseThrow();
        assertNotEquals(TrainStatus.WAITING, resumed.getStatus(), "Train must resume from WAITING status");
        assertNull(resumed.getWaitingReason(), "Waiting reason must be cleared once signal clears");
        assertTrue(resumed.getSignalAspect().isPermissive(), "Train signal aspect must be permissive");
    }

    @Test
    @DisplayName("Requirement 7: Occupied platform prevents entry of second approaching train")
    void testOccupiedPlatformPreventsEntry() {
        Platform p3 = platformRepository.findByStationCodeAndPlatformNumber("BVI", 3).orElseThrow();
        Train leadTrain = trainRepository.findByTrainNumber("SIM-LOC-902").orElseThrow();
        Train secondTrain = trainRepository.findByTrainNumber("SIM-LOC-901").orElseThrow();

        LocalDateTime clock = LocalDateTime.of(2026, 9, 18, 10, 6);
        simulationService.setSimulationTime(clock);

        // Park lead train on Platform 3
        leadTrain.setAssignedPlatformId(p3.getPlatformId());
        leadTrain.setAssignedPlatformNumber(3);
        leadTrain.setStatus(TrainStatus.AT_PLATFORM);
        leadTrain.setCurrentBlockId("BLK-BVI-P3");
        leadTrain.setActualArrival(clock.minusMinutes(1));
        leadTrain.setActualDeparture(clock.plusMinutes(5));
        trainRepository.save(leadTrain);

        p3.setIsOccupied(true);
        p3.setCurrentTrainId(leadTrain.getTrainId());
        p3.setCurrentTrainNumber(leadTrain.getTrainNumber());
        platformRepository.save(p3);

        // Configure second train targeting Platform 3 arriving at this time
        secondTrain.setAssignedPlatformId(p3.getPlatformId());
        secondTrain.setAssignedPlatformNumber(3);
        secondTrain.setStatus(TrainStatus.APPROACHING);
        secondTrain.setProgressPercent(98.0);
        secondTrain.setActualArrival(clock.plusMinutes(1));
        secondTrain.setActualDeparture(clock.plusMinutes(7));
        trainRepository.save(secondTrain);

        simulationService.syncSignallingState();

        Signal sig3 = simulationService.getSignals().get("SIG-BVI-P3");
        assertEquals(SignalAspect.RED, sig3.getCurrentAspect(), "Signal protecting occupied platform must be RED");
        assertTrue(sig3.getAspectReason().contains("OCCUPIED"), "Reason must mention occupied");
        assertTrue(sig3.getAspectReason().contains("SIM-LOC-902"), "Reason must identify occupying train");

        Train secondUpdated = trainRepository.findById(secondTrain.getTrainId()).orElseThrow();
        assertEquals(TrainStatus.WAITING, secondUpdated.getStatus(), "Second train must halt at RED signal");
        assertTrue(secondUpdated.getWaitingReason().contains("SIG-BVI-P3"), "Waiting reason must cite SIG-BVI-P3");
        assertNotEquals("BLK-BVI-P3", secondUpdated.getCurrentBlockId(), "Second train must NOT enter occupied block");

        // Now vacate Platform 3 (lead train departs)
        p3.setIsOccupied(false);
        p3.setCurrentTrainId(null);
        p3.setCurrentTrainNumber(null);
        platformRepository.save(p3);
        leadTrain.setStatus(TrainStatus.DEPARTED);
        leadTrain.setCurrentBlockId(null);
        trainRepository.save(leadTrain);

        simulationService.syncSignallingState();

        Train secondResumed = trainRepository.findById(secondTrain.getTrainId()).orElseThrow();
        assertNotEquals(TrainStatus.WAITING, secondResumed.getStatus(), "Second train must resume when platform clears");
        assertNull(secondResumed.getWaitingReason(), "Waiting reason must clear");
    }

    @Test
    @DisplayName("Requirement 8: Train delay shifts schedule and triggers reallocation if conflict arises")
    void testTrainDelayUpdatesAffectedSignallingState() {
        Train express = trainRepository.findByTrainNumber("SIM-SF-12953").orElseThrow();

        ReallocationResult result = allocationService.handleTrainDelay(express.getTrainId(), 20, "Signal issue at Dahisar");

        assertNotNull(result);
        Train updated = trainRepository.findById(express.getTrainId()).orElseThrow();
        assertEquals(20, updated.getDelayMinutes(), "Train delay minutes must be updated to 20");
        assertTrue(updated.getActualArrival().isAfter(updated.getScheduledArrival()), "Actual arrival must shift forward");

        // Verify signals are in consistent states across all Borivali platforms
        for (int p = 1; p <= 8; p++) {
            Signal sig = simulationService.getSignals().get("SIG-BVI-P" + p);
            assertNotNull(sig);
            assertNotNull(sig.getCurrentAspect());
        }
    }

    @Test
    @DisplayName("Requirement 9: Reallocation vacates old platform block with zero stale occupancy")
    void testNoStalePlatformBlockOccupancyRemainsAfterReallocation() {
        List<PlatformAllocation> activeAllocs = allocationRepository.findByStationCodeAndStatus("BVI", AllocationStatus.ACTIVE);
        assertFalse(activeAllocs.isEmpty());
        PlatformAllocation alloc = activeAllocs.get(0);

        Train train = trainRepository.findById(alloc.getTrainId()).orElseThrow();
        int originalPlatNum = alloc.getPlatformNumber();
        String originalBlockId = "BLK-BVI-P" + originalPlatNum;

        // Mark train as occupying this block
        TrackBlock tb = simulationService.getTrackBlocks().get(originalBlockId);
        assertNotNull(tb);
        tb.occupy(train.getTrainId(), train.getTrainNumber());

        allocationService.handlePlatformClosure(alloc.getPlatformId(), "Switch failure");

        TrackBlock tbAfter = simulationService.getTrackBlocks().get(originalBlockId);
        assertFalse(tbAfter.isOccupied(), "Old platform block must NOT remain occupied after train is reallocated");
        assertNull(tbAfter.getOccupyingTrainId(), "Old block must have null occupying train id");
        assertNull(tbAfter.getOccupyingTrainNumber(), "Old block must have null occupying train number");
    }

    @Test
    @DisplayName("Requirement 10: Platform reopening restores signal to GREEN and resumes waiting trains")
    void testPlatformReopeningRestoresSignalToGreen() {
        Platform p6 = platformRepository.findByStationCodeAndPlatformNumber("BVI", 6).orElseThrow();

        // Close then reopen Platform 6
        platformService.closePlatform(p6.getPlatformId(), "Temporary clearance");
        assertEquals(SignalAspect.RED, simulationService.getSignals().get("SIG-BVI-P6").getCurrentAspect());

        platformService.openPlatform(p6.getPlatformId());

        Signal sig6 = simulationService.getSignals().get("SIG-BVI-P6");
        assertEquals(SignalAspect.GREEN, sig6.getCurrentAspect(), "Reopening platform must restore signal to GREEN");
        assertTrue(sig6.getCurrentAspect().isPermissive());
    }
}
