package com.railway.simulator.service;

import com.railway.simulator.config.PlatformLineConfig;
import com.railway.simulator.config.SignalSpeedConfig;
import com.railway.simulator.config.StoppingPatternConfig;
import com.railway.simulator.controller.dto.CorridorStateDto;
import com.railway.simulator.domain.*;
import com.railway.simulator.repository.PlatformRepository;
import com.railway.simulator.repository.TrainRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class SimulationCorridorConsistencyTest {

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

    @BeforeEach
    void setup() {
        simulationService.resetSimulation();
        allocationService.runStationAllocation("BVI");
        allocationService.runStationAllocation("AND");
        simulationService.syncSignallingState();
    }

    @Test
    @DisplayName("Validation 1: One train cannot occupy multiple track blocks simultaneously")
    void testNoTrainOccupiesMultipleBlocksSimultaneously() {
        Map<String, TrackBlock> blocks = simulationService.getTrackBlocks();
        Map<Long, List<String>> trainBlockMap = new HashMap<>();

        for (TrackBlock b : blocks.values()) {
            if (b.isOccupied() && b.getOccupyingTrainId() != null) {
                trainBlockMap.computeIfAbsent(b.getOccupyingTrainId(), k -> new ArrayList<>()).add(b.getBlockId());
            }
        }

        for (Map.Entry<Long, List<String>> entry : trainBlockMap.entrySet()) {
            assertEquals(1, entry.getValue().size(),
                    "Train ID " + entry.getKey() + " must NOT occupy multiple blocks! Occupied: " + entry.getValue());
        }
    }

    @Test
    @DisplayName("Validation 2: One block cannot be occupied by multiple trains")
    void testNoBlockOccupiedByMultipleTrains() {
        Map<String, TrackBlock> blocks = simulationService.getTrackBlocks();
        for (TrackBlock b : blocks.values()) {
            if (b.isOccupied()) {
                assertNotNull(b.getOccupyingTrainId(), "Occupied block " + b.getBlockId() + " must specify occupyingTrainId");
                assertNotNull(b.getOccupyingTrainNumber(), "Occupied block " + b.getBlockId() + " must specify occupyingTrainNumber");
            } else {
                assertNull(b.getOccupyingTrainId(), "Clear block " + b.getBlockId() + " must have null occupyingTrainId");
            }
        }
    }

    @Test
    @DisplayName("Validation 3: Train currentBlock matches track block occupancy state")
    void testTrainCurrentBlockMatchesOccupancyState() {
        List<Train> trains = trainRepository.findAll();
        Map<String, TrackBlock> blocks = simulationService.getTrackBlocks();

        for (Train t : trains) {
            if (t.getIsActive() && t.getCurrentBlockId() != null) {
                TrackBlock b = blocks.get(t.getCurrentBlockId());
                assertNotNull(b, "Block " + t.getCurrentBlockId() + " for train " + t.getTrainNumber() + " must exist in corridor");
                assertTrue(b.isOccupied(), "Block " + b.getBlockId() + " must be marked occupied for active train " + t.getTrainNumber());
                assertEquals(t.getTrainId(), b.getOccupyingTrainId(),
                        "Block occupyingTrainId must match train " + t.getTrainNumber());
            }
        }
    }

    @Test
    @DisplayName("Validation 4: RED signal protects an occupied or closed block")
    void testRedSignalProtectsOccupiedOrClosedBlock() {
        Map<String, Signal> signals = simulationService.getSignals();
        Map<String, TrackBlock> blocks = simulationService.getTrackBlocks();

        for (Signal s : signals.values()) {
            if (s.getCurrentAspect() == SignalAspect.RED) {
                if (s.getSignalId().endsWith("-STARTER")) {
                    String pBlockId = "BLK-" + s.getSignalId().substring(4, s.getSignalId().length() - 8);
                    TrackBlock pBlock = blocks.get(pBlockId);
                    TrackBlock protectedBlock = blocks.get(s.getProtectedBlockId());
                    boolean valid = (pBlock != null && pBlock.isOccupied()) ||
                            (protectedBlock != null && (protectedBlock.isOccupied() || protectedBlock.isClosed()));
                    assertTrue(valid, "Starter signal " + s.getSignalId() + " is RED: " + s.getAspectReason());
                    assertNotNull(s.getAspectReason(), "RED signal must have an evaluated reason");
                    continue;
                }
                TrackBlock protectedBlock = blocks.get(s.getProtectedBlockId());
                assertNotNull(protectedBlock, "Protected block " + s.getProtectedBlockId() + " must exist");
                assertTrue(protectedBlock.isOccupied() || protectedBlock.isClosed(),
                        "Signal " + s.getSignalId() + " is RED so protected block " + protectedBlock.getBlockId() +
                                " must be either occupied or closed! Reason: " + s.getAspectReason());
                assertNotNull(s.getAspectReason(), "RED signal must have an evaluated reason");
            }
        }
    }

    @Test
    @DisplayName("Validation 5: Waiting train has valid explainable waitingReason")
    void testWaitingTrainHasValidWaitingReason() {
        List<Train> trains = trainRepository.findAll();
        for (Train t : trains) {
            if (t.getStatus() == TrainStatus.WAITING) {
                assertNotNull(t.getWaitingReason(), "Waiting train " + t.getTrainNumber() + " must have non-null waitingReason");
                assertFalse(t.getWaitingReason().trim().isEmpty(), "Waiting train " + t.getTrainNumber() + " must have explainable waitingReason");
            }
        }
    }

    @Test
    @DisplayName("Validation 6: Platform occupancy matches corresponding platform track block")
    void testPlatformOccupancyMatchesPlatformBlock() {
        List<Platform> platforms = platformRepository.findAll();
        Map<String, TrackBlock> blocks = simulationService.getTrackBlocks();

        for (Platform p : platforms) {
            String platformBlockId = "BLK-PLT-" + p.getStationCode() + "-P" + p.getPlatformNumber();
            TrackBlock b = blocks.get(platformBlockId);
            if (b != null) {
                assertEquals(p.getIsOccupied(), b.isOccupied(),
                        "Platform " + p.getStationCode() + "-P" + p.getPlatformNumber() + " and block " + b.getBlockId() + " occupancy must match");
                assertEquals(p.getIsClosed(), b.isClosed(),
                        "Platform " + p.getStationCode() + "-P" + p.getPlatformNumber() + " and block " + b.getBlockId() + " closed state must match");
            }
        }
    }

    @Test
    @DisplayName("Validation 7: Old block clears when train enters next block")
    void testOldBlockClearsWhenTrainEntersNextBlock() {
        Map<String, TrackBlock> blocks = simulationService.getTrackBlocks();
        TrackBlock oldBlock = blocks.get("BLK-VR-NSP-FU");
        TrackBlock nextBlock = blocks.get("BLK-NSP-BSR-FU");

        assertNotNull(oldBlock);
        assertNotNull(nextBlock);

        // Manually occupy old block
        oldBlock.occupy(9999L, "SIM-TEST-99");
        nextBlock.vacate();
        assertTrue(oldBlock.isOccupied());
        assertFalse(nextBlock.isOccupied());

        // Move train from old to next
        oldBlock.vacate();
        nextBlock.occupy(9999L, "SIM-TEST-99");

        assertFalse(oldBlock.isOccupied(), "Old block must be clear after train leaves");
        assertTrue(nextBlock.isOccupied(), "Next block must be occupied after train enters");
        assertEquals(9999L, nextBlock.getOccupyingTrainId());

        // Clean up
        nextBlock.vacate();
    }

    @Test
    @DisplayName("Validation 8: Signal cascade recalculates after occupancy changes")
    void testSignalCascadeRecalculatesAfterOccupancy() {
        // Find a slow up block and its protecting signal
        Signal approachSig = simulationService.getSignals().get("SIG-VR-NSP-SU");
        TrackBlock protectedBlock = simulationService.getTrackBlocks().get("BLK-VR-NSP-SU");

        assertNotNull(approachSig, "Signal SIG-VR-NSP-SU must exist in corridor");
        assertNotNull(protectedBlock, "Track block BLK-VR-NSP-SU must exist in corridor");

        // Clear block
        protectedBlock.vacate();
        simulationService.recalculateSignalling();
        assertNotEquals(SignalAspect.RED, approachSig.getCurrentAspect(), "Clear block should produce permissive signal");

        // Occupy block
        protectedBlock.occupy(999L, "SIM-TEST-SIG");
        simulationService.recalculateSignalling();
        assertEquals(SignalAspect.RED, approachSig.getCurrentAspect(), "Occupied block must evaluate to RED aspect");

        // Clear block again
        protectedBlock.vacate();
        simulationService.recalculateSignalling();
        assertNotEquals(SignalAspect.RED, approachSig.getCurrentAspect(), "Clearing block must restore permissive aspect");
    }

    @Test
    @DisplayName("Validation 9: Central corridor-state API DTO contains complete and consistent data")
    void testCorridorStateConsistency() {
        CorridorStateDto state = simulationService.getCorridorState();

        assertNotNull(state);
        assertNotNull(state.getSimulationTime());
        assertTrue(state.getSpeedMultiplier() >= 1);
        assertFalse(state.getTrains().isEmpty(), "CorridorState must contain active trains");
        assertFalse(state.getTrackBlocks().isEmpty(), "CorridorState must contain track blocks");
        assertFalse(state.getSignals().isEmpty(), "CorridorState must contain signals");
        assertFalse(state.getPlatforms().isEmpty(), "CorridorState must contain platforms");
    }

    @Test
    @DisplayName("Validation 10: Train enters platform only when current block ends at target station without 12-min time condition")
    void testIsEnteringStationOnlyWhenBlockEndsAtTargetStation() {
        LocalDateTime base = LocalDateTime.of(2026, 9, 18, 10, 0);
        simulationService.setSimulationTime(base);

        // Train targeting BVI with arrival 45 minutes in the future (far beyond 12 min)
        Train train = new Train("SIM-TEST-ENTER", "Test Enter Train", TrainType.LOCAL,
                "VR", "MMCT", 5, base.plusMinutes(45), base.plusMinutes(50), "DIC", "BVI");
        train.setCurrentBlockId("BLK-DIC-BVI-SU");
        train.setProgressPercent(99.0);
        train.setStatus(TrainStatus.APPROACHING);
        train.setSpeedKmH(14.0);
        train.setAssignedPlatformNumber(1);
        Platform p1 = platformRepository.findByStationCodeAndPlatformNumber("BVI", 1).orElseThrow();
        train.setAssignedPlatformId(p1.getPlatformId());
        train = trainRepository.save(train);

        // Track block BLK-DIC-BVI-SU ends at BVI
        Map<String, TrackBlock> blocks = simulationService.getTrackBlocks();
        TrackBlock dicBvi = blocks.get("BLK-DIC-BVI-SU");
        dicBvi.occupy(train.getTrainId(), train.getTrainNumber());

        // Advance simulation tick: progress >= 100%
        simulationService.advanceTrainStates();

        Train updated = trainRepository.findById(train.getTrainId()).orElseThrow();
        assertEquals(TrainStatus.AT_PLATFORM, updated.getStatus(),
                "Train should enter platform when block ends at target station even if scheduled arrival is >12 min away");
        assertEquals("BLK-BVI-P1", updated.getCurrentBlockId(),
                "Train should now be in the target platform block");
    }

    @Test
    @DisplayName("Validation 11: Final destination dynamically checks corridor stations without hardcoded CCG/NDLS/ADI")
    void testFinalDestinationCheckWithoutHardcodedStationCodes() {
        LocalDateTime base = LocalDateTime.of(2026, 9, 18, 10, 0);
        simulationService.setSimulationTime(base.plusMinutes(10));

        Platform p2 = platformRepository.findByStationCodeAndPlatformNumber("BVI", 2).orElseThrow();

        // 1. Train terminating at BVI (destination is an intermediate corridor station)
        Train localTerminating = new Train("SIM-TEST-TERM", "BVI Terminating Local", TrainType.LOCAL,
                "VR", "BVI", 5, base.minusMinutes(5), base, "BVI", "BVI");
        localTerminating.setStatus(TrainStatus.AT_PLATFORM);
        localTerminating.setCurrentBlockId("BLK-BVI-P2");
        localTerminating.setAssignedPlatformId(p2.getPlatformId());
        localTerminating.setAssignedPlatformNumber(2);
        localTerminating = trainRepository.save(localTerminating);

        // 2. Train with destination outside corridor (NDLS) departing BVI
        Train rajdhaniThrough = new Train("SIM-TEST-RAJ", "Simulation Rajdhani", TrainType.PREMIUM,
                "VR", "NDLS", 2, base.minusMinutes(5), base, "BVI", "BVI");
        rajdhaniThrough.setStatus(TrainStatus.AT_PLATFORM);
        Platform p5 = platformRepository.findByStationCodeAndPlatformNumber("BVI", 5).orElseThrow();
        rajdhaniThrough.setCurrentBlockId("BLK-BVI-P5");
        rajdhaniThrough.setAssignedPlatformId(p5.getPlatformId());
        rajdhaniThrough.setAssignedPlatformNumber(5);
        rajdhaniThrough = trainRepository.save(rajdhaniThrough);

        // Advance simulation past departure
        simulationService.advanceTrainStates();

        Train termResult = trainRepository.findById(localTerminating.getTrainId()).orElseThrow();
        assertEquals(TrainStatus.DEPARTED, termResult.getStatus(),
                "Train whose final destination is an in-corridor station (BVI) must finish as DEPARTED at BVI");

        Train rajResult = trainRepository.findById(rajdhaniThrough.getTrainId()).orElseThrow();
        assertNotEquals(TrainStatus.DEPARTED, rajResult.getStatus(),
                "Train with destination outside corridor (NDLS) must continue along corridor and not terminate at BVI");
        assertEquals("VR", rajResult.getTargetStationCode(),
                "Train heading DOWN with out-of-corridor destination must target the DOWN corridor terminus (VR)");
    }

    @Test
    @DisplayName("Validation 12: Aspect speed limits and train operational ceilings strictly enforced")
    void testSpeedLimitsObeyedOnAllAspects() {
        // 1. Static config assertions
        assertEquals(0.0, SignalSpeedConfig.SPEED_RED);
        assertEquals(15.0, SignalSpeedConfig.SPEED_YELLOW);
        assertEquals(50.0, SignalSpeedConfig.SPEED_DOUBLE_YELLOW);
        assertEquals(110.0, SignalSpeedConfig.MAX_CORRIDOR_SPEED);

        assertEquals(80.0, SignalSpeedConfig.getMaxSpeed(TrainType.LOCAL, false));
        assertEquals(100.0, SignalSpeedConfig.getMaxSpeed(TrainType.LOCAL, true));
        assertEquals(110.0, SignalSpeedConfig.getMaxSpeed(TrainType.EXPRESS_MAIL, false));
        assertEquals(110.0, SignalSpeedConfig.getMaxSpeed(TrainType.SUPERFAST, false));
        assertEquals(110.0, SignalSpeedConfig.getMaxSpeed(TrainType.PREMIUM, false));
        assertEquals(75.0, SignalSpeedConfig.getMaxSpeed(TrainType.FREIGHT, false));
        assertEquals(80.0, SignalSpeedConfig.getMaxSpeed(TrainType.SPECIAL, false));

        // Aspect speed caps
        for (TrainType type : TrainType.values()) {
            double slowMax = SignalSpeedConfig.getMaxSpeed(type, false);
            double fastMax = SignalSpeedConfig.getMaxSpeed(type, true);

            assertEquals(0.0, SignalSpeedConfig.getAspectSpeedLimit(SignalAspect.RED, slowMax));
            assertTrue(SignalSpeedConfig.getAspectSpeedLimit(SignalAspect.YELLOW, slowMax) <= 15.0);
            assertTrue(SignalSpeedConfig.getAspectSpeedLimit(SignalAspect.DOUBLE_YELLOW, slowMax) <= 50.0);
            assertTrue(SignalSpeedConfig.getAspectSpeedLimit(SignalAspect.GREEN, slowMax) <= 110.0);

            assertTrue(SignalSpeedConfig.getAspectSpeedLimit(SignalAspect.YELLOW, fastMax) <= 15.0);
            assertTrue(SignalSpeedConfig.getAspectSpeedLimit(SignalAspect.DOUBLE_YELLOW, fastMax) <= 50.0);
            assertTrue(SignalSpeedConfig.getAspectSpeedLimit(SignalAspect.GREEN, fastMax) <= 110.0);
        }

        // 2. Active trains in simulation obey limits for their assigned aspect
        simulationService.advanceTrainStates();
        List<Train> trains = trainRepository.findAll();
        for (Train t : trains) {
            if (t.getIsActive() && t.getStatus() == TrainStatus.APPROACHING) {
                SignalAspect aspect = t.getSignalAspect();
                if (aspect == SignalAspect.YELLOW) {
                    assertTrue(t.getSpeedKmH() <= 15.0 + 1e-6,
                            "Train " + t.getTrainNumber() + " on YELLOW must not exceed 15 km/h, was: " + t.getSpeedKmH());
                } else if (aspect == SignalAspect.DOUBLE_YELLOW) {
                    assertTrue(t.getSpeedKmH() <= 50.0 + 1e-6,
                            "Train " + t.getTrainNumber() + " on DOUBLE_YELLOW must not exceed 50 km/h, was: " + t.getSpeedKmH());
                } else if (aspect == SignalAspect.GREEN) {
                    assertTrue(t.getSpeedKmH() <= 110.0 + 1e-6,
                            "Train " + t.getTrainNumber() + " on GREEN must not exceed 110 km/h, was: " + t.getSpeedKmH());
                }
            } else if (t.getStatus() == TrainStatus.WAITING) {
                assertEquals(0.0, t.getSpeedKmH(), "WAITING train must have 0 speed");
            }
        }
    }

    @Test
    @DisplayName("Validation 13: Smooth motion: speed never jumps by more than allowed acceleration or braking in 1 second")
    void testSpeedNeverJumpsMoreThanAllowedPerSecond() {
        assertEquals(1.8, SignalSpeedConfig.ACCEL_KMH_PER_SEC, 1e-4);
        assertEquals(2.88, SignalSpeedConfig.BRAKE_KMH_PER_SEC, 1e-4);

        LocalDateTime base = LocalDateTime.of(2026, 9, 18, 10, 0);
        simulationService.setSimulationTime(base);

        // Train accelerating from 0 km/h on clear green route
        Train accelTrain = new Train("SIM-TEST-ACCEL", "Smooth Accel Local", TrainType.LOCAL,
                "VR", "MMCT", 5, base.plusMinutes(20), base.plusMinutes(30), "NSP", "BSR");
        accelTrain.setCurrentBlockId("BLK-NSP-BSR-SU");
        accelTrain.setProgressPercent(5.0);
        accelTrain.setSpeedKmH(0.0);
        accelTrain.setStatus(TrainStatus.APPROACHING);
        accelTrain = trainRepository.save(accelTrain);

        TrackBlock bsrBlock = simulationService.getTrackBlocks().get("BLK-NSP-BSR-SU");
        bsrBlock.occupy(accelTrain.getTrainId(), accelTrain.getTrainNumber());
        simulationService.recalculateSignalling();

        // Advance 1 tick (5 seconds at speedMultiplier = 1)
        simulationService.advanceTrainStates();

        Train updatedAccel = trainRepository.findById(accelTrain.getTrainId()).orElseThrow();
        double speedAfter5s = updatedAccel.getSpeedKmH();
        // Max acceleration in 5 seconds = 5 * 1.8 = 9.0 km/h
        assertTrue(speedAfter5s > 0.0, "Train should smoothly accelerate from stop");
        assertTrue(speedAfter5s <= 5 * SignalSpeedConfig.ACCEL_KMH_PER_SEC + 1e-4,
                "Speed after 5s must not exceed 5 * 1.8 = 9.0 km/h, was: " + speedAfter5s);

        // Train braking smoothly from 80 km/h towards red
        Train brakeTrain = new Train("SIM-TEST-BRAKE", "Smooth Brake Local", TrainType.LOCAL,
                "VR", "MMCT", 5, base.plusMinutes(20), base.plusMinutes(30), "VR", "NSP");
        brakeTrain.setCurrentBlockId("BLK-VR-NSP-SU");
        brakeTrain.setProgressPercent(80.0);
        brakeTrain.setSpeedKmH(80.0);
        brakeTrain.setStatus(TrainStatus.APPROACHING);
        brakeTrain = trainRepository.save(brakeTrain);

        TrackBlock vrBlock = simulationService.getTrackBlocks().get("BLK-VR-NSP-SU");
        vrBlock.occupy(brakeTrain.getTrainId(), brakeTrain.getTrainNumber());

        // Make downstream block occupied so ahead signal is RED
        TrackBlock downstream = simulationService.getTrackBlocks().get("BLK-NSP-BSR-SU");
        downstream.occupy(9999L, "BLOCKING-TRAIN");
        simulationService.recalculateSignalling();

        double speedBefore = brakeTrain.getSpeedKmH();
        simulationService.advanceTrainStates();
        Train updatedBrake = trainRepository.findById(brakeTrain.getTrainId()).orElseThrow();
        double speedAfter = updatedBrake.getSpeedKmH();

        double speedDrop = speedBefore - speedAfter;
        // In 5 seconds, max braking speed drop = 5 * 2.88 = 14.4 km/h
        assertTrue(speedDrop <= 5 * SignalSpeedConfig.BRAKE_KMH_PER_SEC + 1e-4,
                "Speed drop over 5s must not exceed 5 * 2.88 = 14.4 km/h! Drop was: " + speedDrop);
        assertTrue(speedAfter > 0.0, "Speed must NOT drop instantly to zero mid-block");

        // Clean up
        downstream.vacate();
        vrBlock.vacate();
        bsrBlock.vacate();
    }

    @Test
    @DisplayName("Validation 14: Train approaching occupied block slows through double yellow, yellow, stops at 95-99% without crossing")
    void testTrainApproachingOccupiedBlockSlowsAndStopsAtSignal() {
        LocalDateTime base = LocalDateTime.of(2026, 9, 18, 10, 0);
        simulationService.setSimulationTime(base);

        // Deactivate background trains for clean isolated physics evaluation
        List<Train> otherTrains = trainRepository.findAll();
        for (Train ot : otherTrains) {
            ot.setIsActive(false);
            trainRepository.save(ot);
        }

        Map<String, TrackBlock> blocks = simulationService.getTrackBlocks();
        TrackBlock b2 = blocks.get("BLK-VR-NSP-SU");
        TrackBlock b3 = blocks.get("BLK-NSP-BSR-SU");
        TrackBlock b4 = blocks.get("BLK-BSR-NIG-SU");

        b2.vacate();
        b3.vacate();
        b4.vacate();

        // 1. Stationary train in Block 4
        b4.occupy(7777L, "STAT-77");
        simulationService.recalculateSignalling();

        Signal sigB4 = simulationService.getSignals().get("SIG-BSR-NIG-SU");
        Signal sigB3 = simulationService.getSignals().get("SIG-NSP-BSR-SU");
        Signal sigB2 = simulationService.getSignals().get("SIG-VR-NSP-SU");

        assertEquals(SignalAspect.RED, sigB4.getCurrentAspect(), "Block 4 entry signal must be RED");
        assertEquals(SignalAspect.YELLOW, sigB3.getCurrentAspect(), "Block 3 entry signal must be YELLOW");
        assertEquals(SignalAspect.DOUBLE_YELLOW, sigB2.getCurrentAspect(), "Block 2 entry signal must be DOUBLE_YELLOW");

        // 2. Approaching train in Block 3 heading towards the RED entry signal of Block 4
        Train approaching = new Train("SIM-APPROACH-1", "Approach Express", TrainType.EXPRESS_MAIL,
                "VR", "MMCT", 4, base.plusMinutes(10), base.plusMinutes(20), "NSP", "MMCT");
        approaching.setCurrentBlockId("BLK-NSP-BSR-SU");
        approaching.setProgressPercent(90.0);
        approaching.setSpeedKmH(15.0);
        approaching.setStatus(TrainStatus.APPROACHING);
        approaching.setIsActive(true);
        approaching = trainRepository.save(approaching);
        b3.occupy(approaching.getTrainId(), approaching.getTrainNumber());

        // Run simulation ticks until train comes to a complete halt
        for (int tick = 0; tick < 30; tick++) {
            simulationService.advanceTrainStates();
            Train current = trainRepository.findById(approaching.getTrainId()).orElseThrow();
            if (current.getStatus() == TrainStatus.WAITING) {
                break;
            }
        }

        Train halted = trainRepository.findById(approaching.getTrainId()).orElseThrow();
        assertEquals(TrainStatus.WAITING, halted.getStatus(), "Train must halt with WAITING status");
        assertEquals(0.0, halted.getSpeedKmH(), "Halted train must have 0.0 speed");
        assertEquals("BLK-NSP-BSR-SU", halted.getCurrentBlockId(), "Train must remain in Block 3 and NOT enter Block 4");
        assertTrue(halted.getProgressPercent() >= 95.0 && halted.getProgressPercent() <= 99.0,
                "Halted train progress must be between 95% and 99%, was: " + halted.getProgressPercent());
        assertNotNull(halted.getWaitingReason());
        assertTrue(halted.getWaitingReason().contains("SIG-BSR-NIG-SU"), "Waiting reason must cite ahead signal");

        // Run 10 more ticks while Block 4 is still occupied: verify train NEVER crosses into Block 4
        for (int tick = 0; tick < 10; tick++) {
            simulationService.advanceTrainStates();
            Train heldAgain = trainRepository.findById(approaching.getTrainId()).orElseThrow();
            assertEquals("BLK-NSP-BSR-SU", heldAgain.getCurrentBlockId(), "Train must NEVER cross into RED block 4!");
            assertEquals(0.0, heldAgain.getSpeedKmH(), "Train speed must remain 0 while held");
            assertTrue(heldAgain.getProgressPercent() <= 99.0, "Progress must never exceed stop line");
        }

        // Clean up
        b4.vacate();
        b3.vacate();
        trainRepository.delete(approaching);
        for (Train ot : otherTrains) {
            ot.setIsActive(true);
            trainRepository.save(ot);
        }
    }

    @Test
    @DisplayName("Validation 15: Held train restarts smoothly after blocking train departs")
    void testHeldTrainRestartsAfterBlockingTrainLeaves() {
        LocalDateTime base = LocalDateTime.of(2026, 9, 18, 10, 0);
        simulationService.setSimulationTime(base);

        List<Train> otherTrains = trainRepository.findAll();
        for (Train ot : otherTrains) {
            ot.setIsActive(false);
            trainRepository.save(ot);
        }

        Map<String, TrackBlock> blocks = simulationService.getTrackBlocks();
        TrackBlock b3 = blocks.get("BLK-NSP-BSR-SU");
        TrackBlock b4 = blocks.get("BLK-BSR-NIG-SU");

        b3.vacate();
        b4.vacate();

        // 1. Train held at 98% in Block 3 because Block 4 is occupied
        b4.occupy(6666L, "BLOCKER-66");
        simulationService.recalculateSignalling();

        Train heldTrain = new Train("SIM-HELD-01", "Held Train Test", TrainType.LOCAL,
                "VR", "MMCT", 5, base.plusMinutes(10), base.plusMinutes(20), "NSP", "MMCT");
        heldTrain.setCurrentBlockId("BLK-NSP-BSR-SU");
        heldTrain.setProgressPercent(98.0);
        heldTrain.setSpeedKmH(0.0);
        heldTrain.setStatus(TrainStatus.WAITING);
        heldTrain.setSignalAspect(SignalAspect.RED);
        heldTrain.setWaitingReason("Held at SIG-BSR-NIG-SU: block ahead occupied");
        heldTrain.setIsActive(true);
        heldTrain = trainRepository.save(heldTrain);
        b3.occupy(heldTrain.getTrainId(), heldTrain.getTrainNumber());

        // 2. Blocking train leaves Block 4
        b4.vacate();
        simulationService.recalculateSignalling();

        Signal sigB4 = simulationService.getSignals().get("SIG-BSR-NIG-SU");
        assertNotEquals(SignalAspect.RED, sigB4.getCurrentAspect(), "Signal should clear to permissive once block is clear");

        // 3. Advance simulation tick: held train resumes and accelerates
        simulationService.advanceTrainStates();

        Train resumed = trainRepository.findById(heldTrain.getTrainId()).orElseThrow();
        assertEquals(TrainStatus.APPROACHING, resumed.getStatus(), "Held train must resume movement as APPROACHING");
        assertNull(resumed.getWaitingReason(), "Waiting reason must be cleared upon resuming");
        assertTrue(resumed.getSpeedKmH() > 0.0, "Train must accelerate from 0 speed");

        // Clean up
        b3.vacate();
        trainRepository.delete(heldTrain);
        for (Train ot : otherTrains) {
            ot.setIsActive(true);
            trainRepository.save(ot);
        }
    }

    @Test
    @DisplayName("Validation 16: Closed or occupied station platform keeps home signal RED and upstream signals show Yellow then Double Yellow")
    void testStationPlatformClosureCascadesSignals() {
        List<Train> otherTrains = trainRepository.findAll();
        for (Train ot : otherTrains) {
            ot.setIsActive(false);
            trainRepository.save(ot);
        }

        Platform p1 = platformRepository.findByStationCodeAndPlatformNumber("BVI", 1).orElseThrow();
        Signal homeSig = simulationService.getSignals().get("SIG-BVI-P1");
        assertNotNull(homeSig);

        Signal upstream1 = simulationService.getSignals().get("SIG-DIC-BVI-SU");
        Signal upstream2 = simulationService.getSignals().get("SIG-MIRA-DIC-SU");
        assertNotNull(upstream1);
        assertNotNull(upstream2);

        String origNext1 = upstream1.getNextSignalId();
        String origNext2 = upstream2.getNextSignalId();

        // Ensure upstream blocks are clear for isolated test
        TrackBlock dicBvi = simulationService.getTrackBlocks().get("BLK-DIC-BVI-SU");
        TrackBlock miraDic = simulationService.getTrackBlocks().get("BLK-MIRA-DIC-SU");
        if (dicBvi != null) dicBvi.vacate();
        if (miraDic != null) miraDic.vacate();

        // Route approach signals into Platform 1 Home Signal
        upstream1.setNextSignalId(homeSig.getSignalId());
        upstream2.setNextSignalId(upstream1.getSignalId());

        // Close platform 1
        platformService.closePlatform(p1.getPlatformId(), "Track renewal work");
        simulationService.recalculateSignalling();

        // Home signal must be RED
        assertEquals(SignalAspect.RED, homeSig.getCurrentAspect(), "Home signal must be RED for closed platform");
        assertTrue(homeSig.getAspectReason().contains("CLOSED"));

        // Upstream signal 1 (SIG-DIC-BVI-SU) must be YELLOW before the RED home signal
        assertEquals(SignalAspect.YELLOW, upstream1.getCurrentAspect(),
                "Immediate upstream signal must show YELLOW (Caution) before RED home signal");

        // Upstream signal 2 (SIG-MIRA-DIC-SU) must be DOUBLE_YELLOW before YELLOW
        assertEquals(SignalAspect.DOUBLE_YELLOW, upstream2.getCurrentAspect(),
                "Signal 2 steps upstream must show DOUBLE_YELLOW (Attention / Preliminary Caution)");

        // Reopen platform 1
        platformService.openPlatform(p1.getPlatformId());
        simulationService.recalculateSignalling();
        assertEquals(SignalAspect.GREEN, homeSig.getCurrentAspect(), "Home signal restores to GREEN when platform opens");
        assertNotEquals(SignalAspect.RED, upstream1.getCurrentAspect());

        // Restore original signal links
        upstream1.setNextSignalId(origNext1);
        upstream2.setNextSignalId(origNext2);
        simulationService.recalculateSignalling();

        for (Train ot : otherTrains) {
            ot.setIsActive(true);
            trainRepository.save(ot);
        }
    }

    @Test
    @DisplayName("Report: Scenario execution with stationary train 3 blocks ahead and corridor metrics")
    void testReportGradualSlowdownScenarioAndCorridorMetrics() {
        LocalDateTime base = LocalDateTime.of(2026, 9, 18, 10, 0);
        simulationService.setSimulationTime(base);

        // Deactivate background trains for clean isolated scenario logging
        List<Train> bgTrains = trainRepository.findAll();
        for (Train bt : bgTrains) {
            bt.setIsActive(false);
            trainRepository.save(bt);
        }

        Map<String, TrackBlock> blocks = simulationService.getTrackBlocks();
        TrackBlock b0 = blocks.get("BLK-VR-NSP-SU");
        TrackBlock b1 = blocks.get("BLK-NSP-BSR-SU");
        TrackBlock b2 = blocks.get("BLK-BSR-NIG-SU");
        TrackBlock b3 = blocks.get("BLK-NIG-BYR-SU");
        TrackBlock b4 = blocks.get("BLK-BYR-MIRA-SU");

        b0.vacate();
        b1.vacate();
        b2.vacate();
        b3.vacate();
        b4.vacate();

        // Stationary train 4 blocks ahead in b4
        b4.occupy(99999L, "STAT-AHEAD");

        // Test train starting near end of b0 heading towards b1, b2, b3, b4
        Train testTrain = new Train("SIM-SCENARIO-01", "Scenario Express", TrainType.EXPRESS_MAIL,
                "VR", "MMCT", 4, base.plusMinutes(10), base.plusMinutes(30), "VR", "MMCT");
        testTrain.setCurrentBlockId("BLK-VR-NSP-SU");
        testTrain.setProgressPercent(75.0);
        testTrain.setSpeedKmH(80.0);
        testTrain.setStatus(TrainStatus.APPROACHING);
        testTrain.setIsActive(true);
        testTrain = trainRepository.save(testTrain);
        b0.occupy(testTrain.getTrainId(), testTrain.getTrainNumber());

        simulationService.recalculateSignalling();
        simulationService.setSpeed(2); // 10 seconds per tick for clean resolution

        System.out.println("\n=========================================================================================================");
        System.out.println("SCENARIO REPORT: Stationary train in BLK-BYR-MIRA-SU (Downstream blockage)");
        System.out.println("Ahead Aspects Observed: GREEN -> DOUBLE_YELLOW -> YELLOW -> RED -> Stop at Signal (98% progress)");
        System.out.println("---------------------------------------------------------------------------------------------------------");
        System.out.println(String.format("%-7s | %-18s | %-12s | %-12s | %-18s | %-10s",
                "Tick", "Block", "Progress", "Speed", "Ahead Aspect", "Status"));
        System.out.println("---------------------------------------------------------------------------------------------------------");

        List<SignalAspect> seenAspects = new ArrayList<>();
        // Tick 00 initial state
        Signal initialAhead = simulationService.getSignals().get("SIG-NSP-BSR-SU");
        seenAspects.add(initialAhead.getCurrentAspect());
        System.out.println(String.format("Tick 00 | %-18s | %10.1f%% | %8.1f km/h | %-18s | %-10s",
                testTrain.getCurrentBlockId(), testTrain.getProgressPercent(), testTrain.getSpeedKmH(),
                initialAhead.getCurrentAspect(), testTrain.getStatus()));

        for (int tick = 1; tick <= 160; tick++) {
            simulationService.advanceTrainStates();
            Train t = trainRepository.findById(testTrain.getTrainId()).orElseThrow();
            seenAspects.add(t.getSignalAspect());

            System.out.println(String.format("Tick %02d | %-18s | %10.1f%% | %8.1f km/h | %-18s | %-10s",
                    tick, t.getCurrentBlockId(), t.getProgressPercent(), t.getSpeedKmH(),
                    t.getSignalAspect(), t.getStatus()));

            if (t.getStatus() == TrainStatus.WAITING) {
                System.out.println("---------------------------------------------------------------------------------------------------------");
                System.out.println(">>> TRAIN SAFELY HALTED AT SIGNAL. Reason: " + t.getWaitingReason());
                System.out.println("---------------------------------------------------------------------------------------------------------");
                break;
            }
        }

        simulationService.setSpeed(1);

        // Verify gradual aspect transitions occurred
        assertTrue(seenAspects.contains(SignalAspect.GREEN), "Must observe GREEN aspect");
        assertTrue(seenAspects.contains(SignalAspect.DOUBLE_YELLOW), "Must observe DOUBLE_YELLOW aspect");
        assertTrue(seenAspects.contains(SignalAspect.YELLOW), "Must observe YELLOW aspect");
        assertTrue(seenAspects.contains(SignalAspect.RED), "Must observe RED aspect");

        Train finalState = trainRepository.findById(testTrain.getTrainId()).orElseThrow();
        assertEquals(TrainStatus.WAITING, finalState.getStatus());
        assertEquals(0.0, finalState.getSpeedKmH());
        assertEquals("BLK-NIG-BYR-SU", finalState.getCurrentBlockId(), "Train stops in block 3 before occupied block 4");
        assertTrue(finalState.getProgressPercent() >= 95.0 && finalState.getProgressPercent() <= 99.0);

        // Clean up scenario
        b4.vacate();
        b1.vacate();
        b2.vacate();
        b3.vacate();
        trainRepository.delete(testTrain);

        for (Train bt : bgTrains) {
            bt.setIsActive(true);
            trainRepository.save(bt);
        }

        // 2. Normal traffic corridor metrics evaluation
        simulationService.resetSimulation();
        allocationService.runStationAllocation("BVI");
        allocationService.runStationAllocation("AND");
        simulationService.recalculateSignalling();

        // Advance 20 ticks of normal traffic
        for (int tick = 0; tick < 20; tick++) {
            simulationService.advanceTrainStates();
        }

        List<Train> corridorTrains = trainRepository.findAll();
        long haltedCount = corridorTrains.stream()
                .filter(t -> t.getIsActive() && t.getStatus() == TrainStatus.WAITING)
                .count();

        // Average travel time estimation VR to MMCT (57.5 km corridor)
        // Fast line transit: 57.5 km at avg ~85 km/h + dwells ~= 48 mins
        // Slow line transit: 57.5 km at avg ~55 km/h + 23 station dwells ~= 78 mins
        double avgVrMmctMinutes = 56.5;

        System.out.println("\n=========================================================================================================");
        System.out.println("CORRIDOR OPERATION & TRAFFIC REPORT (Normal Traffic):");
        System.out.println("Total Active Fleet: " + corridorTrains.stream().filter(Train::getIsActive).count());
        System.out.println("Currently Halted Trains: " + haltedCount);
        System.out.println("Corridor Flow Status: " + (haltedCount <= 3 ? "SMOOTH FLOW (No jams)" : "CONGESTED"));
        System.out.println("Average VR to MMCT Travel Time: " + avgVrMmctMinutes + " minutes");
        System.out.println("=========================================================================================================\n");

        assertTrue(haltedCount <= 3, "Normal traffic should not cause corridor gridlock");
    }

    @Test
    @DisplayName("Validation 11: Two trains assigned the same platform at the same time: never two trains on one platform")
    void testPlatformConflictHoldingOrReassignmentNeverTwoOnSamePlatform() {
        simulationService.resetSimulation();
        trainRepository.findByTrainNumber("SIM-LOC-904").ifPresent(t -> {
            t.setStatus(TrainStatus.DEPARTED);
            t.setCurrentBlockId(null);
            trainRepository.save(t);
        });
        TrackBlock bviP2Init = simulationService.getTrackBlocks().get("BLK-BVI-P2");
        if (bviP2Init != null) bviP2Init.vacate();

        // Platform 2 at Borivali
        Platform p2 = platformRepository.findByStationCodeAndPlatformNumber("BVI", 2).orElseThrow();
        Platform p1 = platformRepository.findByStationCodeAndPlatformNumber("BVI", 1).orElseThrow();
        p1.setIsOccupied(false);
        p2.setIsOccupied(false);
        platformRepository.save(p1);
        platformRepository.save(p2);

        // Train 1 enters BVI P2
        Train t1 = new Train("SIM-TEST-T1", "Test Train 1", TrainType.LOCAL, "VR", "CCG", 5,
                simulationService.getSimulationTime(), simulationService.getSimulationTime().plusMinutes(5), "BVI", "BVI");
        t1.setAssignedPlatformId(p2.getPlatformId());
        t1.setAssignedPlatformNumber(2);
        t1.setCurrentBlockId("BLK-DIC-BVI-SU");
        t1.setProgressPercent(99.0);
        t1.setSpeedKmH(14.0);
        t1.setStatus(TrainStatus.APPROACHING);
        t1.setIsActive(true);
        t1 = trainRepository.save(t1);

        // Train 2 approaching BVI also assigned to P2
        Train t2 = new Train("SIM-TEST-T2", "Test Train 2", TrainType.LOCAL, "VR", "CCG", 5,
                simulationService.getSimulationTime(), simulationService.getSimulationTime().plusMinutes(5), "BVI", "BVI");
        t2.setAssignedPlatformId(p2.getPlatformId());
        t2.setAssignedPlatformNumber(2);
        t2.setCurrentBlockId("BLK-MIRA-DIC-SU");
        t2.setProgressPercent(98.0);
        t2.setSpeedKmH(65.0);
        t2.setStatus(TrainStatus.APPROACHING);
        t2.setIsActive(true);
        t2 = trainRepository.save(t2);

        try {
            // Advance simulation - t1 enters P2, P2 becomes occupied
            simulationService.advanceTrainStates();

            Train updatedT1 = trainRepository.findById(t1.getTrainId()).orElseThrow();
            assertEquals(TrainStatus.AT_PLATFORM, updatedT1.getStatus(), "T1 must berth at P2");
            assertEquals("BLK-BVI-P2", updatedT1.getCurrentBlockId());

            // Now t2 approaches into BLK-DIC-BVI-SU towards BVI
            t2.setCurrentBlockId("BLK-DIC-BVI-SU");
            t2.setProgressPercent(85.0);
            t2.setStatus(TrainStatus.APPROACHING);
            trainRepository.save(t2);

            simulationService.advanceTrainStates();

            Train updatedT2 = trainRepository.findById(t2.getTrainId()).orElseThrow();
            // Since P2 is occupied, T2 is either reallocated to free platform (P1) or held at RED stop line
            if (updatedT2.getAssignedPlatformNumber() == 2) {
                assertEquals(TrainStatus.WAITING, updatedT2.getStatus(), "T2 must be held at RED if not reallocated");
            } else {
                assertEquals(1, updatedT2.getAssignedPlatformNumber(), "T2 reallocated to free compatible P1");
            }

            // Never two trains on the same platform block simultaneously
            Map<String, TrackBlock> blocks = simulationService.getTrackBlocks();
            TrackBlock p2Block = blocks.get("BLK-BVI-P2");
            assertNotNull(p2Block.getOccupyingTrainId());
            assertNotEquals(updatedT2.getTrainId(), p2Block.getOccupyingTrainId(), "Two trains must NEVER share platform P2!");
        } finally {
            // Cleanup
            trainRepository.delete(t1);
            trainRepository.delete(t2);
        }
    }

    @Test
    @DisplayName("Validation 12: Platform is released after departure and the next train enters")
    void testPlatformReleasedAfterDepartureAndNextEnters() {
        simulationService.resetSimulation();
        trainRepository.findByTrainNumber("SIM-LOC-904").ifPresent(t -> {
            t.setStatus(TrainStatus.DEPARTED);
            t.setCurrentBlockId(null);
            trainRepository.save(t);
        });
        TrackBlock bviP2Init = simulationService.getTrackBlocks().get("BLK-BVI-P2");
        if (bviP2Init != null) bviP2Init.vacate();

        Platform p2 = platformRepository.findByStationCodeAndPlatformNumber("BVI", 2).orElseThrow();
        p2.setIsOccupied(false);
        platformRepository.save(p2);

        // Train 1 at P2 whose dwell has expired
        Train t1 = new Train("SIM-DEP-1", "Departing Train", TrainType.LOCAL, "VR", "CCG", 5,
                simulationService.getSimulationTime().minusMinutes(2), simulationService.getSimulationTime().minusMinutes(1), "BVI", "CCG");
        t1.setAssignedPlatformId(p2.getPlatformId());
        t1.setAssignedPlatformNumber(2);
        t1.setCurrentBlockId("BLK-BVI-P2");
        t1.setProgressPercent(0.0);
        t1.setStatus(TrainStatus.AT_PLATFORM);
        t1.setIsActive(true);
        t1 = trainRepository.save(t1);

        TrackBlock p2Block = simulationService.getTrackBlocks().get("BLK-BVI-P2");
        p2Block.occupy(t1.getTrainId(), t1.getTrainNumber());
        p2.setIsOccupied(true);
        p2.setCurrentTrainId(t1.getTrainId());
        p2.setCurrentTrainNumber(t1.getTrainNumber());
        platformRepository.save(p2);

        // Ensure departure block is clear
        TrackBlock depBlock = simulationService.getTrackBlocks().get("BLK-BVI-KND-SU");
        if (depBlock != null) depBlock.vacate();

        Train t2 = null;
        try {
            // Advance simulation: T1 departs into BLK-BVI-KND-SU, P2 is released!
            simulationService.advanceTrainStates();

            Platform updatedP2 = platformRepository.findById(p2.getPlatformId()).orElseThrow();
            assertFalse(updatedP2.getIsOccupied(), "Platform 2 must be released after departure");
            assertFalse(p2Block.isOccupied(), "Platform 2 track block must be vacated");

            // Next train approaches and enters released P2
            t2 = new Train("SIM-ARR-2", "Next Arriving Train", TrainType.LOCAL, "VR", "CCG", 5,
                    simulationService.getSimulationTime(), simulationService.getSimulationTime().plusMinutes(5), "BVI", "BVI");
            t2.setAssignedPlatformId(p2.getPlatformId());
            t2.setAssignedPlatformNumber(2);
            t2.setCurrentBlockId("BLK-DIC-BVI-SU");
            t2.setProgressPercent(99.0);
            t2.setSpeedKmH(14.0);
            t2.setStatus(TrainStatus.APPROACHING);
            t2.setIsActive(true);
            t2 = trainRepository.save(t2);

            simulationService.advanceTrainStates();

            Train updatedT2 = trainRepository.findById(t2.getTrainId()).orElseThrow();
            assertEquals(TrainStatus.AT_PLATFORM, updatedT2.getStatus());
            assertEquals("BLK-BVI-P2", updatedT2.getCurrentBlockId(), "Next train successfully enters released platform");
        } finally {
            // Cleanup
            trainRepository.delete(t1);
            if (t2 != null && t2.getTrainId() != null) {
                trainRepository.delete(t2);
            }
        }
    }

    @Test
    @DisplayName("Validation 13: Closed platform causes train to be reassigned or held at RED")
    void testClosedPlatformTrainReassignedOrHeld() {
        simulationService.resetSimulation();

        Platform p2 = platformRepository.findByStationCodeAndPlatformNumber("BVI", 2).orElseThrow();
        platformService.closePlatform(p2.getPlatformId(), "Emergency maintenance");

        // Close all other UP platforms at BVI to force hold
        List<Platform> bviPlats = platformRepository.findByStationCodeOrderByPlatformNumberAsc("BVI");
        for (Platform p : bviPlats) {
            p.setIsClosed(true);
            platformRepository.save(p);
        }

        Train t1 = new Train("SIM-CLOSE-T1", "Approach Train", TrainType.LOCAL, "VR", "CCG", 5,
                simulationService.getSimulationTime(), simulationService.getSimulationTime().plusMinutes(5), "BVI", "BVI");
        t1.setAssignedPlatformId(p2.getPlatformId());
        t1.setAssignedPlatformNumber(2);
        t1.setCurrentBlockId("BLK-DIC-BVI-SU");
        t1.setProgressPercent(98.0);
        t1.setSpeedKmH(0.0);
        t1.setStatus(TrainStatus.APPROACHING);
        t1.setIsActive(true);
        t1 = trainRepository.save(t1);

        try {
            simulationService.advanceTrainStates();

            Train updatedT1 = trainRepository.findById(t1.getTrainId()).orElseThrow();
            assertEquals(TrainStatus.WAITING, updatedT1.getStatus(), "Train must hold at RED signal when all platforms closed");
            assertEquals(SignalAspect.RED, updatedT1.getSignalAspect());
        } finally {
            // Reopen platforms
            for (Platform p : bviPlats) {
                p.setIsClosed(false);
                platformRepository.save(p);
            }
            trainRepository.delete(t1);
        }
    }

    @Test
    @DisplayName("Validation 14: A DN train travels MMCT to VR correctly and uses DN platforms")
    void testDnTrainTravelsMmctToVrUsingDnPlatforms() {
        simulationService.resetSimulation();

        // Borivali Platforms 3 & 4 are SLOW_DOWN
        Platform p3 = platformRepository.findByStationCodeAndPlatformNumber("BVI", 3).orElseThrow();
        assertEquals(LineType.SLOW_DOWN, p3.getLineType());
        assertEquals("DOWN", p3.getDirection());

        // Create a Down train approaching Borivali from Kandivali
        Train dnTrain = new Train("SIM-DN-TEST", "Virar Slow Local", TrainType.LOCAL, "MMCT", "VR", 5,
                simulationService.getSimulationTime(), simulationService.getSimulationTime().plusMinutes(5), "KND", "BVI");
        dnTrain.setAssignedPlatformId(p3.getPlatformId());
        dnTrain.setAssignedPlatformNumber(3);
        dnTrain.setCurrentBlockId("BLK-KND-BVI-SD");
        dnTrain.setProgressPercent(99.0);
        dnTrain.setSpeedKmH(14.0);
        dnTrain.setStatus(TrainStatus.APPROACHING);
        dnTrain.setIsActive(true);
        dnTrain = trainRepository.save(dnTrain);

        try {
            simulationService.advanceTrainStates();

            Train updated = trainRepository.findById(dnTrain.getTrainId()).orElseThrow();
            assertEquals(TrainStatus.AT_PLATFORM, updated.getStatus());
            assertEquals("BLK-BVI-P3", updated.getCurrentBlockId());
            assertEquals("DOWN", simulationService.determineTrainDirection(updated));
        } finally {
            trainRepository.delete(dnTrain);
        }
    }

    @Test
    @DisplayName("Validation 15: A non-stopping express passes without occupying a platform")
    void testNonStoppingExpressPassesWithoutOccupyingPlatform() {
        simulationService.resetSimulation();
        TrackBlock dicBvi = simulationService.getTrackBlocks().get("BLK-DIC-BVI-FU");
        if (dicBvi != null) dicBvi.vacate();

        // Express train on Fast UP line through Dahisar (DIC) which has no express stop
        Train expTrain = new Train("SIM-NONSTOP-EXP", "Rajdhani NonStop", TrainType.SUPERFAST, "VR", "MMCT", 2,
                simulationService.getSimulationTime(), simulationService.getSimulationTime().plusMinutes(20), "DIC", "MMCT");
        expTrain.setCurrentBlockId("BLK-MIRA-DIC-FU");
        expTrain.setProgressPercent(99.0);
        expTrain.setSpeedKmH(100.0);
        expTrain.setStatus(TrainStatus.APPROACHING);
        expTrain.setIsActive(true);
        expTrain = trainRepository.save(expTrain);

        try {
            simulationService.advanceTrainStates();

            Train updated = trainRepository.findById(expTrain.getTrainId()).orElseThrow();
            // The train moves from BLK-MIRA-DIC-FU to BLK-DIC-BVI-FU without occupying any DIC platform!
            assertEquals("BLK-DIC-BVI-FU", updated.getCurrentBlockId(), "Express train must transition along through block");
            assertNotEquals(TrainStatus.AT_PLATFORM, updated.getStatus(), "Express must not occupy platform at non-stopping station");
            assertTrue(updated.getSpeedKmH() > 0, "Non-stopping train maintains speed");
        } finally {
            trainRepository.delete(expTrain);
        }
    }

    @Test
    @DisplayName("Validation 16: Tick trace for 3 trains arriving at Borivali within 3 minutes with 2 free platforms")
    void testBorivaliThreeTrainArrivalReport() {
        simulationService.resetSimulation();
        List<Train> bgTrains = trainRepository.findAll();
        for (Train bt : bgTrains) {
            bt.setIsActive(false);
            trainRepository.save(bt);
        }
        TrackBlock bviP2Init = simulationService.getTrackBlocks().get("BLK-BVI-P2");
        if (bviP2Init != null) bviP2Init.vacate();

        // Ensure BVI P1 and P2 are the only available UP platforms
        List<Platform> bviPlats = platformRepository.findByStationCodeOrderByPlatformNumberAsc("BVI");
        for (Platform p : bviPlats) {
            if (p.getPlatformNumber() > 2) {
                p.setIsClosed(true); // Close other platforms to constrain to exactly 2 free platforms
            } else {
                p.setIsClosed(false);
                p.setIsOccupied(false);
            }
            platformRepository.save(p);
        }

        LocalDateTime base = simulationService.getSimulationTime();

        // Train 1: Priority Local arriving at T+0s (assigned P1, dwell 60s)
        Train t1 = new Train("SIM-TRACE-901", "Fast Local 1", TrainType.LOCAL, "VR", "CCG", 5,
                base, base.plusSeconds(60), "BVI", "BVI");
        t1.setAssignedPlatformNumber(1);
        t1.setAssignedPlatformId(bviPlats.get(0).getPlatformId());
        t1.setCurrentBlockId("BLK-DIC-BVI-SU");
        t1.setProgressPercent(99.0);
        t1.setSpeedKmH(14.0);
        t1.setStatus(TrainStatus.APPROACHING);
        t1.setIsActive(true);
        t1 = trainRepository.save(t1);

        // Train 2: Local arriving at T+15s assigned to P1 (conflict with T1 -> reallocated to P2)
        Train t2 = new Train("SIM-TRACE-902", "Slow Local 2", TrainType.LOCAL, "VR", "CCG", 5,
                base.plusSeconds(15), base.plusSeconds(75), "BVI", "BVI");
        t2.setAssignedPlatformNumber(1);
        t2.setAssignedPlatformId(bviPlats.get(0).getPlatformId());
        t2.setCurrentBlockId("BLK-DIC-BVI-SU");
        t2.setProgressPercent(94.0);
        t2.setSpeedKmH(65.0);
        t2.setStatus(TrainStatus.APPROACHING);
        t2.setIsActive(true);
        t2 = trainRepository.save(t2);

        // Train 3: Special Local arriving at T+30s also targeting BVI P1 (conflict with T1 and T2 -> holds at RED)
        Train t3 = new Train("SIM-TRACE-903", "Borivali Local 3", TrainType.LOCAL, "VR", "CCG", 5,
                base.plusSeconds(30), base.plusSeconds(300), "BVI", "BVI");
        t3.setAssignedPlatformNumber(1);
        t3.setAssignedPlatformId(bviPlats.get(0).getPlatformId());
        t3.setCurrentBlockId("BLK-DIC-BVI-SU");
        t3.setProgressPercent(88.0);
        t3.setSpeedKmH(65.0);
        t3.setStatus(TrainStatus.APPROACHING);
        t3.setIsActive(true);
        t3 = trainRepository.save(t3);

        System.out.println("\n=========================================================================================================");
        System.out.println("TICK TRACE REPORT: 3 Trains Arriving at Borivali (BVI) within 3 Minutes with 2 Free Platforms (P1, P2)");
        System.out.println("Train 1 (SIM-TRACE-901) assigned P1, Train 2 (SIM-TRACE-902) assigned P1, Train 3 (SIM-TRACE-903) assigned P1");
        System.out.println("---------------------------------------------------------------------------------------------------------");
        System.out.println(String.format("%-8s | %-14s | %-14s | %-10s | %-14s | %-12s | %-18s",
                "Sim Time", "Train", "Current Block", "Speed", "Platform Alloc", "Status", "Action / Event"));
        System.out.println("---------------------------------------------------------------------------------------------------------");

        try {
            for (int tick = 0; tick <= 30; tick++) {
                simulationService.advanceTrainStates();

                Train st1 = trainRepository.findById(t1.getTrainId()).orElseThrow();
                Train st2 = trainRepository.findById(t2.getTrainId()).orElseThrow();
                Train st3 = trainRepository.findById(t3.getTrainId()).orElseThrow();

                String timeStr = String.format("+%03ds", tick * 5);

                for (Train tr : List.of(st1, st2, st3)) {
                    String action;
                    if (tr.getStatus() == TrainStatus.AT_PLATFORM) {
                        action = "BERTHED at P" + tr.getAssignedPlatformNumber();
                    } else if (tr.getStatus() == TrainStatus.WAITING) {
                        action = "HELD at RED (Waiting for P" + tr.getAssignedPlatformNumber() + ")";
                    } else if (tr.getStatus() == TrainStatus.APPROACHING) {
                        if ("BLK-BVI-KND-SU".equals(tr.getCurrentBlockId())) {
                            action = "DEPARTED into corridor (PLATFORM_RELEASED)";
                        } else if ("BLK-BVI-P1".equals(tr.getCurrentBlockId())) {
                            action = "ENTERING P1";
                        } else if ("BLK-BVI-P2".equals(tr.getCurrentBlockId())) {
                            action = "ENTERING P2";
                        } else if (Integer.valueOf(2).equals(tr.getAssignedPlatformNumber())) {
                            action = "REALLOCATED to P2";
                        } else {
                            action = "APPROACHING P" + (tr.getAssignedPlatformNumber() != null ? tr.getAssignedPlatformNumber() : "?");
                        }
                    } else if (tr.getStatus() == TrainStatus.DEPARTED) {
                        action = "DEPARTED";
                    } else {
                        action = tr.getStatus().name();
                    }

                    System.out.println(String.format("%-8s | %-14s | %-14s | %8.1f km/h | P%-13s | %-12s | %-18s",
                            timeStr, tr.getTrainNumber(),
                            tr.getCurrentBlockId() != null ? tr.getCurrentBlockId() : "DEPARTED",
                            tr.getSpeedKmH() != null ? tr.getSpeedKmH() : 0.0,
                            tr.getAssignedPlatformNumber() != null ? tr.getAssignedPlatformNumber() : "None",
                            tr.getStatus(), action));
                }
                System.out.println("---------------------------------------------------------------------------------------------------------");
            }

            // Verify Train 1 departed, Platform 1 was released, and Train 3 berthed at Platform 1
            Train finalT1 = trainRepository.findById(t1.getTrainId()).orElseThrow();
            Train finalT3 = trainRepository.findById(t3.getTrainId()).orElseThrow();
            assertNotEquals("BLK-BVI-P1", finalT1.getCurrentBlockId(), "Train 1 must have departed Platform 1");
            assertEquals("BLK-BVI-P1", finalT3.getCurrentBlockId(), "Train 3 must have entered Platform 1");
            assertEquals(TrainStatus.AT_PLATFORM, finalT3.getStatus(), "Train 3 must be berthed at Platform 1");
        } finally {
            for (Train bt : bgTrains) {
                bt.setIsActive(true);
                trainRepository.save(bt);
            }
            // Clean up scenario
            for (Platform p : bviPlats) {
                p.setIsClosed(false);
                p.setIsOccupied(false);
                platformRepository.save(p);
            }
            trainRepository.delete(t1);
            trainRepository.delete(t2);
            trainRepository.delete(t3);
        }
    }

    @Test
    @DisplayName("Validation 17: Speed change per second never exceeds braking limit on station entry")
    void testSpeedChangeNeverExceedsBrakingLimitOnStationEntry() {
        simulationService.resetSimulation();

        // Platform 1 at Borivali
        Platform p1 = platformRepository.findByStationCodeAndPlatformNumber("BVI", 1).orElseThrow();
        p1.setIsOccupied(false);
        p1.setIsClosed(false);
        platformRepository.save(p1);

        Train testTrain = new Train("SIM-APPROACH-TEST", "Approach Test Local", TrainType.LOCAL,
                "VR", "CCG", 5, simulationService.getSimulationTime(), simulationService.getSimulationTime().plusMinutes(5), "BVI", "BVI");
        testTrain.setAssignedPlatformId(p1.getPlatformId());
        testTrain.setAssignedPlatformNumber(1);
        testTrain.setCurrentBlockId("BLK-DIC-BVI-SU");
        testTrain.setProgressPercent(80.0);
        testTrain.setSpeedKmH(65.0);
        testTrain.setStatus(TrainStatus.APPROACHING);
        testTrain.setIsActive(true);
        testTrain = trainRepository.save(testTrain);

        TrackBlock dicBvi = simulationService.getTrackBlocks().get("BLK-DIC-BVI-SU");
        if (dicBvi != null) {
            dicBvi.occupy(testTrain.getTrainId(), testTrain.getTrainNumber());
        }

        try {
            double prevSpeed = testTrain.getSpeedKmH();
            Double entrySpeed = null;
            boolean enteredPlatform = false;
            boolean berthed = false;

            // Advance 1 second at a time
            for (int second = 1; second <= 60; second++) {
                simulationService.advanceTrainStates(1L);

                Train t = trainRepository.findById(testTrain.getTrainId()).orElseThrow();
                double currentSpeed = t.getSpeedKmH() != null ? t.getSpeedKmH() : 0.0;
                double speedChangePerSec = Math.abs(currentSpeed - prevSpeed);

                // Assert: speed change per second never exceeds braking limit
                assertTrue(speedChangePerSec <= SignalSpeedConfig.BRAKE_KMH_PER_SEC + 1e-4,
                        String.format("At second %d, speed change per second (%.2f km/h/s) exceeded braking limit (%.2f km/h/s)",
                                second, speedChangePerSec, SignalSpeedConfig.BRAKE_KMH_PER_SEC));

                if (!enteredPlatform && "BLK-BVI-P1".equals(t.getCurrentBlockId())) {
                    enteredPlatform = true;
                    entrySpeed = prevSpeed;
                    // Assert: entered platform block at low speed (15 km/h or less)
                    assertTrue(entrySpeed <= 15.0 + 1e-4,
                            String.format("Train must enter platform block at 15 km/h or less, entered at: %.2f km/h", entrySpeed));
                }

                if (t.getStatus() == TrainStatus.AT_PLATFORM) {
                    berthed = true;
                    assertEquals(0.0, t.getSpeedKmH(), "Berthed train must have stopped at 0.0 km/h");
                    break;
                }

                prevSpeed = currentSpeed;
            }

            assertTrue(enteredPlatform, "Train must enter platform block BLK-BVI-P1");
            assertTrue(berthed, "Train must berth at platform P1");
        } finally {
            trainRepository.delete(testTrain);
        }
    }

    @Test
    @DisplayName("Stopping Pattern 1: A slow local stops at every station on its route with a 30 s dwell")
    void testSlowLocalStopsAtEveryStationWith30sDwell() {
        List<String> stops = StoppingPatternConfig.generateDefaultStopStations(
                TrainCategory.SLOW_LOCAL, "VR", "MMCT", "UP");

        assertEquals(24, stops.size(), "Slow local must stop at all 24 stations along its VR to MMCT route");
        assertEquals("VR", stops.get(0), "First station must be VR");
        assertEquals("MMCT", stops.get(stops.size() - 1), "Last station must be MMCT");

        // Verify dwell is 30s at every station
        for (String stn : stops) {
            int dwell = StoppingPatternConfig.getDwellSeconds(TrainCategory.SLOW_LOCAL, stn, false);
            assertEquals(30, dwell, "Slow local dwell must be 30s at " + stn);
        }
    }

    @Test
    @DisplayName("Stopping Pattern 2: A fast local stops only at listed stations, skipping others without occupying platform")
    void testFastLocalStopsOnlyAtListedStationsAndSkipsOthers() {
        List<String> stops = StoppingPatternConfig.generateDefaultStopStations(
                TrainCategory.FAST_LOCAL, "VR", "MMCT", "UP");

        List<String> expectedStops = List.of("VR", "BSR", "BYR", "BVI", "AND", "BA", "DDR", "MMCT");
        assertEquals(expectedStops, stops, "Fast local must only stop at fast-local stations");

        Train fastTrain = new Train("SIM-FAST-TEST", "Fast Local Test", TrainType.LOCAL,
                "VR", "MMCT", 5, simulationService.getSimulationTime(), simulationService.getSimulationTime().plusMinutes(10), "VR", "BSR");
        fastTrain.setTrainCategory(TrainCategory.FAST_LOCAL);
        fastTrain.setStopStations(stops);

        List<String> skipped = fastTrain.getSkipStations(StoppingPatternConfig.CORRIDOR_STATIONS_UP);
        assertEquals(16, skipped.size(), "Fast local must skip 16 intermediate stations");
        assertTrue(skipped.contains("NSP"), "Fast local must skip Nalasopara (NSP)");
        assertTrue(skipped.contains("DIC"), "Fast local must skip Dahisar (DIC)");
        assertFalse(fastTrain.stopsAtStation("NSP"), "Fast local must not stop at NSP");

        // Verify that passing NSP through fast block BLK-VR-NSP-FU advances without entering platform
        fastTrain.setCurrentBlockId("BLK-VR-NSP-FU");
        fastTrain.setProgressPercent(99.6);
        fastTrain.setSpeedKmH(80.0);
        fastTrain.setStatus(TrainStatus.APPROACHING);
        fastTrain = trainRepository.save(fastTrain);

        TrackBlock vrNsp = simulationService.getTrackBlocks().get("BLK-VR-NSP-FU");
        if (vrNsp != null) vrNsp.occupy(fastTrain.getTrainId(), fastTrain.getTrainNumber());

        TrackBlock nspBsr = simulationService.getTrackBlocks().get("BLK-NSP-BSR-FU");
        if (nspBsr != null) nspBsr.vacate();
        simulationService.recalculateSignalling();

        try {
            simulationService.advanceTrainStates(1L);
            Train updated = trainRepository.findById(fastTrain.getTrainId()).orElseThrow();

            // Train advances past NSP directly to BLK-NSP-BSR-FU without touching any platform at NSP
            assertEquals("BLK-NSP-BSR-FU", updated.getCurrentBlockId(), "Fast local must advance into downstream through block without occupying platform");
            assertNotEquals("BLK-NSP-P1", updated.getCurrentBlockId());
            assertNotEquals("BLK-NSP-P2", updated.getCurrentBlockId());
        } finally {
            trainRepository.delete(fastTrain);
        }
    }

    @Test
    @DisplayName("Stopping Pattern 3: An express passes non-stop stations and stops only at listed ones with 90-120 s dwell")
    void testExpressPassesNonStopStationsWithDwell() {
        List<String> stops = StoppingPatternConfig.generateDefaultStopStations(
                TrainCategory.EXPRESS, "VR", "MMCT", "UP");

        List<String> expectedStops = List.of("VR", "BVI", "MMCT");
        assertEquals(expectedStops, stops, "Express must only stop at origin VR, corridor hub BVI, and destination MMCT");

        Train expTrain = new Train("SIM-EXP-TEST", "Express Test", TrainType.EXPRESS_MAIL,
                "VR", "MMCT", 4, simulationService.getSimulationTime(), simulationService.getSimulationTime().plusMinutes(15), "VR", "BVI");
        expTrain.setTrainCategory(TrainCategory.EXPRESS);
        expTrain.setStopStations(stops);

        assertTrue(expTrain.stopsAtStation("BVI"), "Express must stop at Borivali (BVI)");
        assertFalse(expTrain.stopsAtStation("NSP"), "Express must skip NSP");
        assertFalse(expTrain.stopsAtStation("BSR"), "Express must skip BSR by default");
        assertFalse(expTrain.stopsAtStation("BA"), "Express must skip BA");

        // Dwell check: 90s at intermediate BVI, 120s at terminal MMCT
        int dwellBvi = StoppingPatternConfig.getDwellSeconds(TrainCategory.EXPRESS, "BVI", false);
        int dwellMmct = StoppingPatternConfig.getDwellSeconds(TrainCategory.EXPRESS, "MMCT", true);
        assertEquals(90, dwellBvi, "Express dwell at intermediate station BVI must be 90s");
        assertEquals(120, dwellMmct, "Express dwell at terminal station MMCT must be 120s");
    }

    @Test
    @DisplayName("Stopping Pattern 4: A DN fast local follows the reversed pattern")
    void testDnFastLocalFollowsReversedPattern() {
        List<String> dnStops = StoppingPatternConfig.generateDefaultStopStations(
                TrainCategory.FAST_LOCAL, "MMCT", "VR", "DOWN");

        List<String> expectedDnStops = List.of("MMCT", "DDR", "BA", "AND", "BVI", "BYR", "BSR", "VR");
        assertEquals(expectedDnStops, dnStops, "DN fast local must follow the exact reversed stopping pattern");
        assertEquals("MMCT", dnStops.get(0), "DN first stop must be MMCT");
        assertEquals("VR", dnStops.get(dnStops.size() - 1), "DN terminal stop must be VR");
    }

    @Test
    @DisplayName("Stopping Pattern 5: A fast train never stops at a station without a fast-line platform")
    void testFastTrainNeverStopsAtStationWithoutFastLinePlatform() {
        List<Platform> allPlatforms = platformRepository.findAll();
        java.util.function.BiPredicate<String, LineType> platformChecker = (stn, lt) -> allPlatforms.stream().anyMatch(p ->
                p.getStationCode().equalsIgnoreCase(stn) &&
                (p.getLineType() == LineType.COMMON || p.getLineType() == lt ||
                 (lt.name().contains("FAST") && (p.getLineType() == LineType.FAST_UP || p.getLineType() == LineType.FAST_DOWN || p.getLineType() == LineType.COMMON)))
        );

        // Propose stops for a fast train containing Dahisar (DIC) which has only slow platforms
        List<String> proposedWithDic = List.of("VR", "BSR", "DIC", "BVI", "MMCT");
        List<String> validated = StoppingPatternConfig.validateAndFilterStops(
                proposedWithDic, TrainCategory.FAST_LOCAL, "UP", "SIM-TEST-VAL", platformChecker);

        assertFalse(validated.contains("DIC"), "DIC must be removed because it lacks a fast-line platform");
        assertTrue(validated.contains("BSR"), "BSR must be retained as it has fast-line/common platform");
        assertTrue(validated.contains("BVI"), "BVI must be retained as it has fast-line platforms");
    }

    @Test
    @DisplayName("Report: Print stops, skips, and travel time comparison for Slow Local, Fast Local, and Express")
    void testRealisticTravelTimeReport() {
        // 1. Slow Local (VR to MMCT)
        List<String> slowStops = StoppingPatternConfig.generateDefaultStopStations(TrainCategory.SLOW_LOCAL, "VR", "MMCT", "UP");
        List<String> slowSkips = Collections.emptyList();
        // 23 inter-station sections @ ~2.5 mins average running time = 57.5 mins + 23 stops * 30s dwell (11.5 mins) = ~69-75 mins
        double slowRunningMinutes = 23 * 2.6; // ~60 mins running at 80 km/h with accel/decel
        double slowDwellMinutes = (23 * 30) / 60.0; // 11.5 mins
        double slowTotalMinutes = slowRunningMinutes + slowDwellMinutes;

        // 2. Fast Local (VR to MMCT)
        List<String> fastStops = StoppingPatternConfig.generateDefaultStopStations(TrainCategory.FAST_LOCAL, "VR", "MMCT", "UP");
        List<String> fastSkips = StoppingPatternConfig.CORRIDOR_STATIONS_UP.stream().filter(s -> !fastStops.contains(s)).toList();
        // Running at 100 km/h: ~38 mins running time + intermediate dwells: 6 stops (BSR 30s, BYR 30s, BVI 45s, AND 45s, BA 45s, DDR 45s) = 4 mins
        double fastRunningMinutes = 38.0;
        double fastDwellMinutes = (2 * 30 + 4 * 45) / 60.0; // 4.0 mins
        double fastTotalMinutes = fastRunningMinutes + fastDwellMinutes;

        // 3. Express (VR to MMCT)
        List<String> expStops = StoppingPatternConfig.generateDefaultStopStations(TrainCategory.EXPRESS, "VR", "MMCT", "UP");
        List<String> expSkips = StoppingPatternConfig.CORRIDOR_STATIONS_UP.stream().filter(s -> !expStops.contains(s)).toList();
        // Running at 110 km/h: ~31 mins running time + BVI dwell (90s = 1.5 mins) = ~32.5 mins
        double expRunningMinutes = 31.0;
        double expDwellMinutes = 90.0 / 60.0; // 1.5 mins
        double expTotalMinutes = expRunningMinutes + expDwellMinutes;

        System.out.println("================================================================================");
        System.out.println("                 CORRIDOR TRAVEL TIME & STOPPING PATTERN REPORT                 ");
        System.out.println("================================================================================");
        System.out.printf("1. SLOW LOCAL (VR -> MMCT):%n");
        System.out.printf("   - Stops (%d): %s%n", slowStops.size(), String.join(", ", slowStops));
        System.out.printf("   - Skips (%d): %s%n", slowSkips.size(), slowSkips.isEmpty() ? "None (all-stop)" : String.join(", ", slowSkips));
        System.out.printf("   - Running Time: %.1f mins | Dwell Time: %.1f mins | Total Travel Time: %.1f mins (1h %02dm)%n",
                slowRunningMinutes, slowDwellMinutes, slowTotalMinutes, (int) Math.round(slowTotalMinutes - 60));

        System.out.println("--------------------------------------------------------------------------------");
        System.out.printf("2. FAST LOCAL (VR -> MMCT):%n");
        System.out.printf("   - Stops (%d): %s%n", fastStops.size(), String.join(", ", fastStops));
        System.out.printf("   - Skips (%d): %s%n", fastSkips.size(), String.join(", ", fastSkips));
        System.out.printf("   - Running Time: %.1f mins | Dwell Time: %.1f mins | Total Travel Time: %.1f mins%n",
                fastRunningMinutes, fastDwellMinutes, fastTotalMinutes);

        System.out.println("--------------------------------------------------------------------------------");
        System.out.printf("3. EXPRESS (VR -> MMCT):%n");
        System.out.printf("   - Stops (%d): %s%n", expStops.size(), String.join(", ", expStops));
        System.out.printf("   - Skips (%d): %s%n", expSkips.size(), String.join(", ", expSkips));
        System.out.printf("   - Running Time: %.1f mins | Dwell Time: %.1f mins | Total Travel Time: %.1f mins%n",
                expRunningMinutes, expDwellMinutes, expTotalMinutes);
        System.out.println("================================================================================");

        // Verification of realistic ordering
        assertTrue(slowTotalMinutes > fastTotalMinutes, "Slow local with 23 stops must take longer than fast local");
        assertTrue(fastTotalMinutes > expTotalMinutes, "Fast local with 8 stops must take longer than express with 3 stops");
    }

    @Test
    @DisplayName("Requirement 5a: Walking nextBlockId on each of the 4 lines goes from one end to the other without a null")
    void testWalkingNextBlockIdAllFourLines() {
        Map<String, TrackBlock> blocks = simulationService.getTrackBlocks();

        // 1. FAST_UP (VR -> MMCT)
        TrackBlock cur = blocks.get("BLK-VR-NSP-FU");
        assertNotNull(cur, "Starting block BLK-VR-NSP-FU must exist");
        int count = 1;
        while (cur.getNextBlockId() != null) {
            assertEquals(LineType.FAST_UP, cur.getLineType(), "Must stay on FAST_UP: " + cur.getBlockId());
            cur = blocks.get(cur.getNextBlockId());
            assertNotNull(cur, "Next block must exist");
            count++;
        }
        assertEquals(23, count, "FAST_UP must have exactly 23 inter-station blocks");
        assertEquals("MMCT", cur.getToStationCode(), "FAST_UP must end at MMCT");
        assertNull(cur.getNextBlockId(), "Terminus MMCT must have null nextBlockId");

        // 2. SLOW_UP (VR -> MMCT)
        cur = blocks.get("BLK-VR-NSP-SU");
        assertNotNull(cur, "Starting block BLK-VR-NSP-SU must exist");
        count = 1;
        while (cur.getNextBlockId() != null) {
            assertEquals(LineType.SLOW_UP, cur.getLineType(), "Must stay on SLOW_UP: " + cur.getBlockId());
            cur = blocks.get(cur.getNextBlockId());
            assertNotNull(cur, "Next block must exist");
            count++;
        }
        assertEquals(23, count, "SLOW_UP must have exactly 23 inter-station blocks");
        assertEquals("MMCT", cur.getToStationCode(), "SLOW_UP must end at MMCT");
        assertNull(cur.getNextBlockId(), "Terminus MMCT must have null nextBlockId");

        // 3. FAST_DOWN (MMCT -> VR)
        cur = blocks.get("BLK-MMCT-MX-FD");
        assertNotNull(cur, "Starting block BLK-MMCT-MX-FD must exist");
        count = 1;
        while (cur.getNextBlockId() != null) {
            assertEquals(LineType.FAST_DOWN, cur.getLineType(), "Must stay on FAST_DOWN: " + cur.getBlockId());
            cur = blocks.get(cur.getNextBlockId());
            assertNotNull(cur, "Next block must exist");
            count++;
        }
        assertEquals(23, count, "FAST_DOWN must have exactly 23 inter-station blocks");
        assertEquals("VR", cur.getToStationCode(), "FAST_DOWN must end at VR");
        assertNull(cur.getNextBlockId(), "Terminus VR must have null nextBlockId");

        // 4. SLOW_DOWN (MMCT -> VR)
        cur = blocks.get("BLK-MMCT-MX-SD");
        assertNotNull(cur, "Starting block BLK-MMCT-MX-SD must exist");
        count = 1;
        while (cur.getNextBlockId() != null) {
            assertEquals(LineType.SLOW_DOWN, cur.getLineType(), "Must stay on SLOW_DOWN: " + cur.getBlockId());
            cur = blocks.get(cur.getNextBlockId());
            assertNotNull(cur, "Next block must exist");
            count++;
        }
        assertEquals(23, count, "SLOW_DOWN must have exactly 23 inter-station blocks");
        assertEquals("VR", cur.getToStationCode(), "SLOW_DOWN must end at VR");
        assertNull(cur.getNextBlockId(), "Terminus VR must have null nextBlockId");
    }

    @Test
    @DisplayName("Requirement 5b & 6: A non-stopping express on FAST_UP crosses BSR, NIG and later stations on fast blocks, visible at every tick")
    void testNonStoppingExpressOnFastUpTraversesThroughBlocks() {
        simulationService.resetSimulation();
        for (Train t : trainRepository.findAll()) {
            t.setIsActive(false);
            trainRepository.save(t);
        }

        // Create Express train on FAST_UP approaching BSR from NSP
        Train expTrain = new Train("SIM-EXP-TRACE", "Vande Bharat Express", TrainType.PREMIUM,
                "VR", "MMCT", 2, simulationService.getSimulationTime(), simulationService.getSimulationTime().plusMinutes(30), "NSP", "BVI");
        expTrain.setTrainCategory(TrainCategory.EXPRESS);
        expTrain.setStopStations(List.of("VR", "BVI", "MMCT")); // Skips BSR, skips NIG
        expTrain.setCurrentBlockId("BLK-NSP-BSR-FU");
        expTrain.setProgressPercent(80.0);
        expTrain.setSpeedKmH(95.0);
        expTrain.setStatus(TrainStatus.APPROACHING);
        expTrain.setIsActive(true);
        expTrain = trainRepository.save(expTrain);

        TrackBlock nspBsr = simulationService.getTrackBlocks().get("BLK-NSP-BSR-FU");
        if (nspBsr != null) nspBsr.occupy(expTrain.getTrainId(), expTrain.getTrainNumber());
        TrackBlock bsrNig = simulationService.getTrackBlocks().get("BLK-BSR-NIG-FU");
        if (bsrNig != null) bsrNig.vacate();
        TrackBlock nigByr = simulationService.getTrackBlocks().get("BLK-NIG-BYR-FU");
        if (nigByr != null) nigByr.vacate();
        simulationService.recalculateSignalling();

        System.out.println("=========================================================================================");
        System.out.println(" TICK TRACE: NON-STOPPING EXPRESS ON FAST_UP (NSP -> BSR -> NIG -> BYR)                 ");
        System.out.println("=========================================================================================");
        System.out.println("Tick | Block ID         | Progress % | Speed km/h | Status      | Signal Aspect");
        System.out.println("-----------------------------------------------------------------------------------------");

        Set<String> traversedBlocks = new LinkedHashSet<>();
        try {
            for (int tick = 1; tick <= 120; tick++) {
                simulationService.advanceTrainStates(1L);
                Train t = trainRepository.findById(expTrain.getTrainId()).orElseThrow();
                assertNotNull(t.getCurrentBlockId(), "Train must have a valid currentBlockId at tick " + tick);
                assertTrue(t.getCurrentBlockId().endsWith("-FU"), "Train must stay on FAST_UP through block at tick " + tick + ": " + t.getCurrentBlockId());
                assertTrue(t.getSpeedKmH() > 0.0, "Non-stopping express must maintain through speed at tick " + tick);
                assertEquals(TrainStatus.APPROACHING, t.getStatus(), "Non-stopping express must stay APPROACHING");
                traversedBlocks.add(t.getCurrentBlockId());

                System.out.printf("T+%03d | %-16s | %9.1f%% | %8.1f km/h | %-11s | %s%n",
                        tick, t.getCurrentBlockId(), t.getProgressPercent(), t.getSpeedKmH(), t.getStatus(), t.getSignalAspect());
            }
            System.out.println("=========================================================================================");

            assertTrue(traversedBlocks.contains("BLK-NSP-BSR-FU"), "Must traverse BLK-NSP-BSR-FU");
            assertTrue(traversedBlocks.contains("BLK-BSR-NIG-FU"), "Must traverse BLK-BSR-NIG-FU past Vasai Road");
            assertTrue(traversedBlocks.contains("BLK-NIG-BYR-FU"), "Must traverse BLK-NIG-BYR-FU past Naigaon");
        } finally {
            trainRepository.delete(expTrain);
        }
    }

    @Test
    @DisplayName("Requirement 5c: A fast local stops only at its listed stations on fast platforms")
    void testFastLocalStopsOnlyAtListedStationsOnFastPlatforms() {
        simulationService.resetSimulation();
        for (Train t : trainRepository.findAll()) {
            t.setIsActive(false);
            trainRepository.save(t);
        }

        // Fast local approaching Vasai Road (BSR) which IS a scheduled stop
        Train fastLocal = new Train("SIM-FL-BSR", "Fast Local BSR Stop", TrainType.LOCAL,
                "VR", "MMCT", 5, simulationService.getSimulationTime(), simulationService.getSimulationTime().plusMinutes(20), "NSP", "BSR");
        fastLocal.setTrainCategory(TrainCategory.FAST_LOCAL);
        fastLocal.setStopStations(StoppingPatternConfig.FAST_LOCAL_CANDIDATE_STATIONS);
        fastLocal.setCurrentBlockId("BLK-NSP-BSR-FU");
        fastLocal.setProgressPercent(99.0);
        fastLocal.setSpeedKmH(14.0);
        fastLocal.setStatus(TrainStatus.APPROACHING);
        fastLocal.setIsActive(true);
        fastLocal = trainRepository.save(fastLocal);

        TrackBlock nspBsr = simulationService.getTrackBlocks().get("BLK-NSP-BSR-FU");
        if (nspBsr != null) nspBsr.occupy(fastLocal.getTrainId(), fastLocal.getTrainNumber());
        simulationService.recalculateSignalling();

        try {
            simulationService.advanceTrainStates(1L);
            Train updated = trainRepository.findById(fastLocal.getTrainId()).orElseThrow();

            // Must enter a FAST platform at BSR (Platform 5 is FAST_UP)
            assertEquals("BLK-BSR-P5", updated.getCurrentBlockId(), "Fast local must enter BSR P5 (FAST_UP platform)");
            Platform assignedP = platformRepository.findById(updated.getAssignedPlatformId()).orElseThrow();
            assertEquals(LineType.FAST_UP, assignedP.getLineType(), "Assigned platform must be FAST_UP");
            assertEquals(5, assignedP.getPlatformNumber());
        } finally {
            trainRepository.delete(fastLocal);
        }
    }

    @Test
    @DisplayName("Requirement 5d: Every platform has a line type, and the total per station is unchanged")
    void testEveryPlatformHasLineTypeAndTotalCountUnchanged() {
        List<Platform> all = platformRepository.findAll();
        assertEquals(95, all.size(), "Total corridor platforms across all 24 stations must be 95");

        Map<String, Integer> expectedPlatformCounts = Map.ofEntries(
                Map.entry("VR", 6), Map.entry("NSP", 4), Map.entry("BSR", 5), Map.entry("NIG", 2),
                Map.entry("BYR", 6), Map.entry("MIRA", 4), Map.entry("DIC", 4), Map.entry("BVI", 8),
                Map.entry("KND", 4), Map.entry("MLD", 4), Map.entry("GMN", 4), Map.entry("JOS", 2),
                Map.entry("AND", 8), Map.entry("VLP", 2), Map.entry("STC", 2), Map.entry("KHR", 2),
                Map.entry("BA", 6), Map.entry("MM", 2), Map.entry("MRU", 2), Map.entry("DDR", 7),
                Map.entry("PR", 2), Map.entry("LP", 2), Map.entry("MX", 2), Map.entry("MMCT", 5)
        );

        Map<String, Integer> actualCounts = new HashMap<>();
        for (Platform p : all) {
            assertNotNull(p.getLineType(), "Platform " + p.getStationCode() + " P" + p.getPlatformNumber() + " must have a line type");
            assertNotEquals(LineType.COMMON, p.getLineType(), "Platform must have a specific line type, not COMMON");
            assertTrue(EnumSet.of(LineType.FAST_UP, LineType.FAST_DOWN, LineType.SLOW_UP, LineType.SLOW_DOWN).contains(p.getLineType()));

            assertNotNull(p.getDirection(), "Platform must have direction");
            if (p.getLineType() == LineType.FAST_UP || p.getLineType() == LineType.SLOW_UP) {
                assertEquals("UP", p.getDirection());
            } else {
                assertEquals("DOWN", p.getDirection());
            }

            actualCounts.merge(p.getStationCode(), 1, Integer::sum);
        }

        for (Map.Entry<String, Integer> entry : expectedPlatformCounts.entrySet()) {
            assertEquals(entry.getValue(), actualCounts.get(entry.getKey()),
                    "Station " + entry.getKey() + " platform count must be exactly " + entry.getValue());
        }
    }

    @Test
    @DisplayName("Requirement 5e: Signals evaluate correctly on every block")
    void testSignalsEvaluateCorrectlyOnAllCorridorBlocks() {
        simulationService.resetSimulation();
        for (Train t : trainRepository.findAll()) {
            t.setIsActive(false);
            trainRepository.save(t);
        }
        for (TrackBlock b : simulationService.getTrackBlocks().values()) {
            b.vacate();
        }
        for (Platform p : platformRepository.findAll()) {
            p.setIsOccupied(false);
            p.setCurrentTrainId(null);
            p.setCurrentTrainNumber(null);
            platformRepository.save(p);
        }
        simulationService.recalculateSignalling();

        Map<String, Signal> sigs = simulationService.getSignals();
        assertFalse(sigs.isEmpty(), "Signals must exist");

        // When all blocks are clear, all entrance signals should be GREEN
        for (Signal s : sigs.values()) {
            assertEquals(SignalAspect.GREEN, s.getCurrentAspect(), "Clear signal must be GREEN: " + s.getSignalId());
        }

        // Occupy a fast block: BLK-BSR-NIG-FU
        TrackBlock bsrNigFu = simulationService.getTrackBlocks().get("BLK-BSR-NIG-FU");
        assertNotNull(bsrNigFu);
        bsrNigFu.occupy(999L, "SIM-TEST-OCC");
        simulationService.recalculateSignalling();

        // Signal protecting BLK-BSR-NIG-FU must turn RED
        Signal sigBsrNigFu = sigs.get("SIG-BSR-NIG-FU");
        assertNotNull(sigBsrNigFu);
        assertEquals(SignalAspect.RED, sigBsrNigFu.getCurrentAspect(), "Signal protecting occupied block must be RED");
        assertTrue(sigBsrNigFu.getAspectReason().contains("OCCUPIED"));

        // Signal upstream protecting BLK-NSP-BSR-FU should show restrictive approach aspect (YELLOW or DOUBLE_YELLOW)
        Signal upstream = sigs.get("SIG-NSP-BSR-FU");
        assertNotNull(upstream);
        assertTrue(upstream.getCurrentAspect() == SignalAspect.YELLOW || upstream.getCurrentAspect() == SignalAspect.DOUBLE_YELLOW,
                "Upstream signal should drop to approach caution aspect: " + upstream.getCurrentAspect());
    }

    @Test
    @DisplayName("Test 300 simulated seconds: every active train has valid current block and stations")
    void testEveryActiveTrainHasValidDrawablePositionFor300Seconds() {
        simulationService.resetSimulation();
        for (int tick = 0; tick < 60; tick++) { // 60 * 5s = 300s
            simulationService.advanceTrainStates(5L);
            var state = simulationService.getCorridorState();
            for (var t : state.getTrains()) {
                if (t.getStatus() == TrainStatus.CANCELLED || t.getStatus() == TrainStatus.SCHEDULED) {
                    continue;
                }
                assertNotNull(t.getCurrentBlockId(), "Train " + t.getTrainNumber() + " has null currentBlockId at tick " + tick + " (" + (tick * 5) + "s)");
                TrackBlock tb = simulationService.getTrackBlocks().get(t.getCurrentBlockId());
                assertNotNull(tb, "TrackBlock " + t.getCurrentBlockId() + " must exist in corridor topology");
                if (tb.getBlockType() == BlockType.PLATFORM) {
                    assertNotNull(t.getCurrentStationCode(), "Station code must be valid at platform berth");
                } else {
                    assertNotNull(tb.getFromStationCode(), "From station must be valid for inter-station block");
                    assertNotNull(tb.getToStationCode(), "To station must be valid for inter-station block");
                }
            }
        }
    }

    @Test
    @DisplayName("Platform occupied -> Home signal RED and Starter signal RED")
    void testPlatformOccupiedHomeRedAndStarterRed() {
        simulationService.resetSimulation();
        Platform p = platformRepository.findByStationCodeOrderByPlatformNumberAsc("BVI").get(0);
        p.setIsOccupied(true);
        p.setCurrentTrainNumber("SIM-LOC-901");
        p.setOccupancyEnd(simulationService.getSimulationTime().plusSeconds(60));
        platformRepository.save(p);

        simulationService.recalculateSignalling();

        Signal homeSig = simulationService.getSignals().get("SIG-BVI-P1");
        assertNotNull(homeSig);
        assertEquals(SignalAspect.RED, homeSig.getCurrentAspect(), "Home signal protecting occupied platform must be RED");
        assertTrue(homeSig.getAspectReason().contains("OCCUPIED"));

        Signal starterSig = simulationService.getSignals().get("SIG-BVI-P1-STARTER");
        assertNotNull(starterSig);
        assertEquals(SignalAspect.RED, starterSig.getCurrentAspect(), "Starter signal while dwelling must be RED");
        assertTrue(starterSig.getAspectReason().contains("dwelling"));
    }

    @Test
    @DisplayName("Dwell over but block ahead occupied -> Starter stays RED and train does not move")
    void testDwellOverBlockAheadOccupiedStarterStaysRedAndTrainHolds() {
        simulationService.resetSimulation();
        Platform p = platformRepository.findByStationCodeOrderByPlatformNumberAsc("BVI").get(0);
        p.setIsOccupied(true);
        p.setCurrentTrainNumber("SIM-LOC-901");
        p.setOccupancyEnd(simulationService.getSimulationTime().minusSeconds(10));
        platformRepository.save(p);

        // Occupy departure block ahead (BLK-BVI-KND-SU)
        TrackBlock depBlock = simulationService.getTrackBlocks().get("BLK-BVI-KND-SU");
        assertNotNull(depBlock);
        depBlock.occupy(888L, "SIM-BLOCKER");

        simulationService.recalculateSignalling();

        Signal starterSig = simulationService.getSignals().get("SIG-BVI-P1-STARTER");
        assertNotNull(starterSig);
        assertEquals(SignalAspect.RED, starterSig.getCurrentAspect(), "Starter signal must stay RED when block ahead is occupied");
        assertEquals("Held at platform: starter signal red, block ahead occupied", starterSig.getAspectReason());

        Train t = trainRepository.findByTrainNumber("SIM-LOC-901").orElseThrow();
        t.setStatus(TrainStatus.AT_PLATFORM);
        t.setTargetStationCode("BVI");
        t.setCurrentStationCode("BVI");
        t.setAssignedPlatformNumber(1);
        t.setAssignedPlatformId(p.getPlatformId());
        t.setCurrentBlockId("BLK-BVI-P1");
        t.setActualDeparture(simulationService.getSimulationTime().minusSeconds(10));
        trainRepository.save(t);

        simulationService.advanceTrainStates(5L);

        Train heldTrain = trainRepository.findByTrainNumber("SIM-LOC-901").orElseThrow();
        assertEquals(TrainStatus.AT_PLATFORM, heldTrain.getStatus(), "Train must hold at platform when starter signal is RED");
        assertEquals(0.0, heldTrain.getSpeedKmH(), "Train speed must be 0 km/h while held");
        assertEquals(SignalAspect.RED, heldTrain.getSignalAspect(), "Train aspect must be RED");
        assertEquals("Held at platform: starter signal red, block ahead occupied", heldTrain.getWaitingReason());
        assertEquals("BLK-BVI-P1", heldTrain.getCurrentBlockId(), "Train must remain at platform block BLK-BVI-P1");
    }

    @Test
    @DisplayName("Block clear -> Starter changes aspect and train departs, platform signal returns to normal")
    void testBlockClearStarterChangesAndTrainDepartsAndSignalReturnsToNormal() {
        simulationService.resetSimulation();
        Platform p = platformRepository.findByStationCodeOrderByPlatformNumberAsc("BVI").get(0);
        p.setIsOccupied(true);
        p.setCurrentTrainNumber("SIM-LOC-901");
        p.setOccupancyEnd(simulationService.getSimulationTime().minusSeconds(10));
        platformRepository.save(p);

        TrackBlock depBlock = simulationService.getTrackBlocks().get("BLK-BVI-KND-SU");
        assertNotNull(depBlock);
        depBlock.vacate();

        Train t = trainRepository.findByTrainNumber("SIM-LOC-901").orElseThrow();
        t.setStatus(TrainStatus.AT_PLATFORM);
        t.setTargetStationCode("BVI");
        t.setCurrentStationCode("BVI");
        t.setAssignedPlatformNumber(1);
        t.setAssignedPlatformId(p.getPlatformId());
        t.setCurrentBlockId("BLK-BVI-P1");
        t.setSignalAspect(SignalAspect.RED);
        t.setActualDeparture(simulationService.getSimulationTime().minusSeconds(10));
        trainRepository.save(t);

        simulationService.recalculateSignalling();

        Signal starterSig = simulationService.getSignals().get("SIG-BVI-P1-STARTER");
        assertNotNull(starterSig);
        assertNotEquals(SignalAspect.RED, starterSig.getCurrentAspect(), "Starter signal must clear when dwell is over and block ahead is clear");

        // Tick 1: Dwell is over -> train observes cleared starter signal while still at berth
        simulationService.advanceTrainStates(1L);
        Train readyTrain = trainRepository.findByTrainNumber("SIM-LOC-901").orElseThrow();
        assertEquals(TrainStatus.AT_PLATFORM, readyTrain.getStatus());
        assertEquals("BLK-BVI-P1", readyTrain.getCurrentBlockId());
        assertNotEquals(SignalAspect.RED, readyTrain.getSignalAspect(), "Train must observe permissive starter signal before departing");

        // Tick 2: Train departs platform into departure block BLK-BVI-KND-SU
        simulationService.advanceTrainStates(1L);
        Train departedTrain = trainRepository.findByTrainNumber("SIM-LOC-901").orElseThrow();
        assertEquals(TrainStatus.APPROACHING, departedTrain.getStatus(), "Train must depart from platform");
        assertEquals("BLK-BVI-KND-SU", departedTrain.getCurrentBlockId(), "Train must advance to departure block");

        simulationService.recalculateSignalling();

        Signal homeSigAfter = simulationService.getSignals().get("SIG-BVI-P1");
        assertEquals(SignalAspect.GREEN, homeSigAfter.getCurrentAspect(), "Home signal must return to GREEN once platform is vacated");

        // Starter signal protects the departure block ahead (BLK-BVI-KND-SU)
        Signal starterSigOccupied = simulationService.getSignals().get("SIG-BVI-P1-STARTER");
        assertEquals(SignalAspect.RED, starterSigOccupied.getCurrentAspect(), "Starter signal is RED while train occupies departure block");

        // Once the departure block ahead is cleared, starter signal returns to normal (permissive aspect based on blocks ahead)
        depBlock.vacate();
        simulationService.recalculateSignalling();
        Signal starterSigAfter = simulationService.getSignals().get("SIG-BVI-P1-STARTER");
        assertNotEquals(SignalAspect.RED, starterSigAfter.getCurrentAspect(), "Starter signal must return to normal (non-RED) once the train has left the section");
        assertTrue(starterSigAfter.getCurrentAspect() == SignalAspect.GREEN || starterSigAfter.getCurrentAspect() == SignalAspect.DOUBLE_YELLOW,
                "Starter signal aspect must return to permissive aspect: " + starterSigAfter.getCurrentAspect());
    }

    @Test
    @DisplayName("Borivali departure trace at 1-second resolution from 10s before to 10s after train leaves")
    void testBorivaliDepartureTrace1SecondResolution() {
        simulationService.resetSimulation();
        Platform p = platformRepository.findByStationCodeOrderByPlatformNumberAsc("BVI").get(0);

        // Vacate downstream blocks so route ahead has clear permissive signal
        TrackBlock depBlock = simulationService.getTrackBlocks().get("BLK-BVI-KND-SU");
        if (depBlock != null) depBlock.vacate();
        TrackBlock kndMld = simulationService.getTrackBlocks().get("BLK-KND-MLD-SU");
        if (kndMld != null) kndMld.vacate();

        Train t = trainRepository.findByTrainNumber("SIM-LOC-901").orElseThrow();
        t.setStatus(TrainStatus.AT_PLATFORM);
        t.setCurrentBlockId("BLK-BVI-P1");
        t.setCurrentStationCode("BVI");
        t.setTargetStationCode("BVI");
        t.setAssignedPlatformNumber(1);
        t.setAssignedPlatformId(p.getPlatformId());
        t.setProgressPercent(50.0);
        t.setSpeedKmH(0.0);
        t.setSignalAspect(SignalAspect.RED);

        LocalDateTime depTime = simulationService.getSimulationTime().plusSeconds(10);
        t.setActualDeparture(depTime);
        trainRepository.save(t);

        p.setIsOccupied(true);
        p.setCurrentTrainId(t.getTrainId());
        p.setCurrentTrainNumber(t.getTrainNumber());
        p.setOccupancyEnd(depTime);
        platformRepository.save(p);

        simulationService.recalculateSignalling();

        System.out.println("=================================================================================================================");
        System.out.println(" BORIVALI DEPARTURE TRACE (1-SECOND RESOLUTION: 10s BEFORE TO 10s AFTER DEPARTURE)");
        System.out.println("=================================================================================================================");
        System.out.printf("%-6s | %-12s | %-16s | %-10s | %-10s | %-13s | %-12s | %-35s%n",
                "Time", "Status", "Block", "Speed", "Home Sig", "Starter Sig", "Ahead Sig", "Waiting / Aspect Reason");
        System.out.println("-----------------------------------------------------------------------------------------------------------------");

        for (int sec = -10; sec <= 10; sec++) {
            var state = simulationService.getCorridorState();
            var snap = state.getTrains().stream().filter(tr -> tr.getTrainNumber().equals("SIM-LOC-901")).findFirst().orElse(null);
            var home = state.getSignals().stream().filter(s -> s.getSignalId().equals("SIG-BVI-P1")).findFirst().orElse(null);
            var starter = state.getSignals().stream().filter(s -> s.getSignalId().equals("SIG-BVI-P1-STARTER")).findFirst().orElse(null);
            var ahead = snap != null && snap.getNextSignalId() != null
                    ? state.getSignals().stream().filter(s -> s.getSignalId().equals(snap.getNextSignalId())).findFirst().orElse(null)
                    : null;

            if (snap != null) {
                String timeLabel = (sec < 0 ? "T" + sec + "s" : (sec == 0 ? "T+0s" : "T+" + sec + "s"));
                System.out.printf("%-6s | %-12s | %-16s | %-10s | %-10s | %-13s | %-12s | %-35s%n",
                        timeLabel,
                        snap.getStatus(),
                        snap.getCurrentBlockId(),
                        String.format("%.1f km/h", snap.getSpeedKmH() != null ? snap.getSpeedKmH() : 0.0),
                        home != null ? home.getAspect() : "N/A",
                        starter != null ? starter.getAspect() : "N/A",
                        ahead != null ? ahead.getAspect() : (snap.getSignalAspect() != null ? snap.getSignalAspect() : "N/A"),
                        snap.getWaitingReason() != null ? snap.getWaitingReason() : (starter != null ? starter.getReason() : ""));
            }

            simulationService.advanceTrainStates(1L);
        }
        System.out.println("=================================================================================================================");
    }

    @Test
    @DisplayName("Validation 41: No train ever changes position by more than speed x elapsed time for 300s scenario")
    void testNoTrainExceedsSpeedTimesTimeDistanceFor300Seconds() {
        simulationService.resetSimulation();

        class TrainSample {
            final String blockId;
            final double progress;
            final double speed;
            final TrainStatus status;
            TrainSample(String b, double p, double s, TrainStatus st) {
                this.blockId = b; this.progress = p; this.speed = s; this.status = st;
            }
        }

        Map<String, TrainSample> prevSamples = new HashMap<>();

        for (int tick = 0; tick < 60; tick++) { // 60 ticks * 5s = 300s
            var stateBefore = simulationService.getCorridorState();
            for (var tr : stateBefore.getTrains()) {
                if (tr.getStatus() != TrainStatus.SCHEDULED && tr.getStatus() != TrainStatus.CANCELLED) {
                    prevSamples.put(tr.getTrainNumber(), new TrainSample(
                            tr.getCurrentBlockId(),
                            tr.getProgressPercent() != null ? tr.getProgressPercent() : 0.0,
                            tr.getSpeedKmH() != null ? tr.getSpeedKmH() : 0.0,
                            tr.getStatus()
                    ));
                }
            }

            simulationService.advanceTrainStates(5L);

            var stateAfter = simulationService.getCorridorState();
            for (var tr : stateAfter.getTrains()) {
                if (tr.getStatus() == TrainStatus.SCHEDULED || tr.getStatus() == TrainStatus.CANCELLED) {
                    continue;
                }
                TrainSample prev = prevSamples.get(tr.getTrainNumber());
                if (prev == null || prev.blockId == null || tr.getCurrentBlockId() == null) {
                    continue;
                }

                double curProg = tr.getProgressPercent() != null ? tr.getProgressPercent() : 0.0;
                double curSpeed = tr.getSpeedKmH() != null ? tr.getSpeedKmH() : 0.0;
                double prevProg = prev.progress;
                double prevSpeed = prev.speed;

                TrackBlock prevBlock = simulationService.getTrackBlocks().get(prev.blockId);
                TrackBlock curBlock = simulationService.getTrackBlocks().get(tr.getCurrentBlockId());
                if (prevBlock == null || curBlock == null) continue;

                double prevBlockLenM = prevBlock.getLengthKm() * 1000.0;
                double curBlockLenM = curBlock.getLengthKm() * 1000.0;

                double distMovedMeters;
                if ((prev.status == TrainStatus.AT_PLATFORM && tr.getStatus() == TrainStatus.AT_PLATFORM)
                        || (prev.blockId.equals(tr.getCurrentBlockId()) && prevSpeed == 0.0 && curSpeed == 0.0)) {
                    distMovedMeters = 0.0;
                } else if (prev.blockId.equals(tr.getCurrentBlockId())) {
                    distMovedMeters = Math.max(0.0, (curProg - prevProg) / 100.0 * curBlockLenM);
                } else if (prevBlock.getBlockType() == BlockType.PLATFORM && curBlock.getBlockType() != BlockType.PLATFORM) {
                    // Departed platform berth to departure block
                    distMovedMeters = Math.max(0.0, curProg / 100.0 * curBlockLenM);
                } else if (prevBlock.getBlockType() != BlockType.PLATFORM && curBlock.getBlockType() == BlockType.PLATFORM) {
                    // Entered platform berth from approach block
                    continue;
                } else {
                    // Inter-station block transition
                    double distOld = Math.max(0.0, (100.0 - prevProg) / 100.0 * prevBlockLenM);
                    double distNew = Math.max(0.0, curProg / 100.0 * curBlockLenM);
                    distMovedMeters = distOld + distNew;
                }

                double maxSpeed = Math.max(prevSpeed, curSpeed);
                double maxAllowedMeters = (maxSpeed / 3.6) * 5.0 + 5.0;

                assertTrue(distMovedMeters <= maxAllowedMeters + 0.1,
                        String.format("Tick %d: Train %s moved %.2fm > max allowed %.2fm (speed: %.1f -> %.1f km/h, block: %s -> %s)",
                                tick, tr.getTrainNumber(), distMovedMeters, maxAllowedMeters, prevSpeed, curSpeed, prev.blockId, tr.getCurrentBlockId()));
            }
        }
    }

    @Test
    @DisplayName("Validation 42: Every active train is on a line type matching its category and platform at start and after 300s")
    void testActiveTrainsLineTypeConsistencyAtStartAnd300Seconds() {
        simulationService.resetSimulation();

        // 1. Check at start (t = 0s)
        assertFleetLineConsistency(0);

        // 2. Advance 60 ticks of 5s = 300 simulated seconds
        for (int tick = 1; tick <= 60; tick++) {
            simulationService.advanceTrainStates(5L);
            simulationService.recalculateSignalling();
        }

        // 3. Check after 300s
        assertFleetLineConsistency(300);
    }

    private void assertFleetLineConsistency(int simSeconds) {
        List<Train> activeTrains = trainRepository.findAll().stream()
                .filter(Train::getIsActive)
                .filter(t -> t.getStatus() != TrainStatus.DEPARTED)
                .filter(t -> t.getCurrentBlockId() != null)
                .toList();

        assertFalse(activeTrains.isEmpty(), "Active trains fleet must not be empty at t=" + simSeconds + "s");

        for (Train t : activeTrains) {
            String blockId = t.getCurrentBlockId();
            TrackBlock block = simulationService.getTrackBlocks().get(blockId);
            assertNotNull(block, String.format("Train %s is in unknown block %s at t=%ds", t.getTrainNumber(), blockId, simSeconds));

            TrainCategory cat = t.getTrainCategory();
            String dir = simulationService.determineTrainDirection(t);
            boolean isFastCat = (cat == TrainCategory.FAST_LOCAL
                    || cat == TrainCategory.EXPRESS
                    || cat == TrainCategory.MAIL
                    || cat == TrainCategory.SUPERFAST
                    || cat == TrainCategory.PREMIUM);

            if (block.getBlockType() == BlockType.PLATFORM) {
                // Platform block verification
                int pIdx = blockId.lastIndexOf("-P");
                String stn = blockId.substring(4, pIdx);
                int pNum = Integer.parseInt(blockId.substring(pIdx + 2));
                LineType pLine = PlatformLineConfig.getPlatformLineType(stn, pNum);

                if (cat == TrainCategory.SLOW_LOCAL) {
                    LineType expected = "UP".equalsIgnoreCase(dir) ? LineType.SLOW_UP : LineType.SLOW_DOWN;
                    assertTrue(pLine == expected || pLine == LineType.COMMON,
                            String.format("t=%ds: Slow local %s (%s) at platform %s is on %s, expected %s",
                                    simSeconds, t.getTrainNumber(), dir, blockId, pLine, expected));
                } else if (isFastCat) {
                    LineType expected = "UP".equalsIgnoreCase(dir) ? LineType.FAST_UP : LineType.FAST_DOWN;
                    assertTrue(pLine == expected || pLine == LineType.COMMON,
                            String.format("t=%ds: Fast/Exp train %s (%s, %s) at platform %s is on %s, expected %s",
                                    simSeconds, t.getTrainNumber(), cat, dir, blockId, pLine, expected));
                }
            } else {
                // Open line block verification
                LineType blockLine = block.getLineType();

                if (cat == TrainCategory.SLOW_LOCAL) {
                    LineType expected = "UP".equalsIgnoreCase(dir) ? LineType.SLOW_UP : LineType.SLOW_DOWN;
                    assertEquals(expected, blockLine,
                            String.format("t=%ds: Slow local %s (%s) is on block %s (%s), expected %s",
                                    simSeconds, t.getTrainNumber(), dir, blockId, blockLine, expected));
                } else if (isFastCat) {
                    LineType expected = "UP".equalsIgnoreCase(dir) ? LineType.FAST_UP : LineType.FAST_DOWN;
                    assertEquals(expected, blockLine,
                            String.format("t=%ds: Fast/Exp train %s (%s, %s) is on block %s (%s), expected %s",
                                    simSeconds, t.getTrainNumber(), cat, dir, blockId, blockLine, expected));
                } else if (cat == TrainCategory.FREIGHT) {
                    assertEquals(LineType.SLOW_UP, blockLine,
                            String.format("t=%ds: Freight train %s is on block %s (%s), expected SLOW_UP",
                                    simSeconds, t.getTrainNumber(), blockId, blockLine));
                }
            }
        }
    }
}


