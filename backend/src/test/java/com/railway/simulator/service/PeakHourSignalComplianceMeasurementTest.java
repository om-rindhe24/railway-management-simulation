package com.railway.simulator.service;

import com.railway.simulator.config.SignalSpeedConfig;
import com.railway.simulator.domain.*;
import com.railway.simulator.repository.PlatformRepository;
import com.railway.simulator.repository.TrainRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.*;

@SpringBootTest
public class PeakHourSignalComplianceMeasurementTest {

    @Autowired
    private SimulationService simulationService;

    @Autowired
    private AllocationService allocationService;

    @Autowired
    private PlatformRepository platformRepository;

    @Autowired
    private TrainRepository trainRepository;

    @BeforeEach
    void setup() {
        simulationService.resetSimulation();
        allocationService.runStationAllocation("BVI");
        allocationService.runStationAllocation("AND");
        allocationService.runStationAllocation("DDR");
        simulationService.syncSignallingState();
    }

    public static class ViolationRecord {
        final int tick;
        final String trainNumber;
        final String currentBlock;
        final String nextBlock;
        final SignalAspect protectingAspect;
        final double speed;
        final double speedLimit;
        final String cause; // SPAD or SPEED_OVER_ASPECT

        public ViolationRecord(int tick, String trainNumber, String currentBlock, String nextBlock,
                               SignalAspect protectingAspect, double speed, double speedLimit, String cause) {
            this.tick = tick;
            this.trainNumber = trainNumber;
            this.currentBlock = currentBlock;
            this.nextBlock = nextBlock;
            this.protectingAspect = protectingAspect;
            this.speed = speed;
            this.speedLimit = speedLimit;
            this.cause = cause;
        }

        @Override
        public String toString() {
            return String.format("[%s] Tick %d | Train %s | Blk %s -> %s | Aspect %s | Speed %.1f km/h (Limit: %.1f)",
                    cause, tick, trainNumber, currentBlock, nextBlock, protectingAspect, speed, speedLimit);
        }
    }

    private void runMeasurement(String modeName, boolean peakHour, int speedMultiplier) {
        simulationService.resetSimulation();
        allocationService.runStationAllocation("BVI");
        allocationService.runStationAllocation("AND");
        allocationService.runStationAllocation("DDR");
        simulationService.syncSignallingState();

        if (peakHour) {
            simulationService.setPeakHourMode(true);
        }

        long stepSeconds = speedMultiplier == 10 ? 50L : 25L;
        int totalTicks = speedMultiplier == 10 ? 144 : 288; // 7200 seconds = 2 simulated hours

        List<ViolationRecord> violations = new ArrayList<>();
        Map<String, Integer> spadPerTrain = new HashMap<>();
        Map<String, Integer> speedOverAspectPerTrain = new HashMap<>();

        Map<String, String> prevBlock = new HashMap<>();
        Map<String, Double> prevProgress = new HashMap<>();

        for (int tick = 0; tick < totalTicks; tick++) {
            // Snapshot state before tick
            var corridorBefore = simulationService.getCorridorState();
            Map<String, SignalAspect> signalsBefore = new HashMap<>();
            if (corridorBefore.getSignals() != null) {
                corridorBefore.getSignals().forEach(s -> signalsBefore.put(s.getSignalId(), s.getAspect()));
            }

            simulationService.advanceTrainStates(stepSeconds);

            var corridorAfter = simulationService.getCorridorState();
            Map<String, SignalAspect> signalsAfter = new HashMap<>();
            if (corridorAfter.getSignals() != null) {
                corridorAfter.getSignals().forEach(s -> signalsAfter.put(s.getSignalId(), s.getAspect()));
            }

            List<SimulationService.BlockTransitionRecord> transitions = simulationService.getTransitionLog();
            for (SimulationService.BlockTransitionRecord tr : transitions) {
                if (tr.isSpad || tr.aspect == SignalAspect.RED) {
                    ViolationRecord v = new ViolationRecord(tick, tr.trainNumber, tr.fromBlock, tr.toBlock,
                            tr.aspect, tr.speedKmH, 0.0, "SPAD");
                    violations.add(v);
                    spadPerTrain.merge(tr.trainNumber, 1, Integer::sum);
                }
            }
            simulationService.clearTransitionLog();

            for (var train : corridorAfter.getTrains()) {
                String tNum = train.getTrainNumber();
                String currBlk = train.getCurrentBlockId();
                Double currProg = train.getProgressPercent();
                Double speed = train.getSpeedKmH() != null ? train.getSpeedKmH() : 0.0;

                // Speed-over-aspect check:
                // Check train's current signal aspect and speed
                SignalAspect currentAspect = train.getSignalAspect();
                if (currentAspect != null) {
                    double limit;
                    switch (currentAspect) {
                        case RED:
                            limit = 0.0;
                            break;
                        case YELLOW:
                            limit = SignalSpeedConfig.SPEED_YELLOW; // 15.0
                            break;
                        case DOUBLE_YELLOW:
                            limit = SignalSpeedConfig.SPEED_DOUBLE_YELLOW; // 50.0
                            break;
                        case GREEN:
                        default:
                            limit = SignalSpeedConfig.MAX_CORRIDOR_SPEED; // 110.0
                            break;
                    }

                    // A violation occurs if speed exceeds aspect limit by more than 0.5 km/h tolerance
                    // For RED, violation if train is at or past stop line (progress >= 98.0) and moving
                    if (currentAspect == SignalAspect.RED) {
                        if (currProg != null && currProg >= 97.9 && speed > 0.5) {
                            ViolationRecord v = new ViolationRecord(tick, tNum, currBlk, train.getNextSignalId(),
                                    currentAspect, speed, limit, "SPEED_OVER_ASPECT_RED");
                            violations.add(v);
                            speedOverAspectPerTrain.merge(tNum, 1, Integer::sum);
                        }
                    } else if (speed > limit + 0.5) {
                        ViolationRecord v = new ViolationRecord(tick, tNum, currBlk, train.getNextSignalId(),
                                currentAspect, speed, limit, "SPEED_OVER_ASPECT");
                        violations.add(v);
                        speedOverAspectPerTrain.merge(tNum, 1, Integer::sum);
                    }
                }

                prevBlock.put(tNum, currBlk);
                prevProgress.put(tNum, currProg);
            }
        }

        long spadCount = violations.stream().filter(v -> "SPAD".equals(v.cause)).count();
        long speedViolationCount = violations.stream().filter(v -> v.cause.startsWith("SPEED_OVER_ASPECT")).count();

        System.out.printf("=== MEASUREMENT RESULT [%s @ %dx] ===%n", modeName, speedMultiplier);
        System.out.printf("Total Ticks: %d | Total Violations: %d | SPADs: %d | Speed-Over-Aspect: %d%n",
                totalTicks, violations.size(), spadCount, speedViolationCount);
        System.out.printf("Trains with SPAD: %d trains: %s%n", spadPerTrain.size(), spadPerTrain);
        System.out.printf("Trains with Speed Violations: %d trains: %s%n", speedOverAspectPerTrain.size(), speedOverAspectPerTrain);

        if (!violations.isEmpty()) {
            System.out.println("Sample Violations (first 10):");
            violations.stream().limit(10).forEach(System.out::println);
        }
        System.out.println("====================================================\n");
    }

    @Test
    @DisplayName("Measurement Baseline: Normal mode @ 5x and 10x")
    void measureNormalMode() {
        runMeasurement("Normal Fleet (27 trains)", false, 5);
        runMeasurement("Normal Fleet (27 trains)", false, 10);
    }

    @Test
    @DisplayName("Measurement: Peak Hour mode @ 5x and 10x")
    void measurePeakHourMode() {
        runMeasurement("Peak Hour (57 trains)", true, 5);
        runMeasurement("Peak Hour (57 trains)", true, 10);
    }
}

