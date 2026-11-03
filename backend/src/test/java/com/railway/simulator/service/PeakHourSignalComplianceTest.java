package com.railway.simulator.service;

import com.railway.simulator.controller.dto.TrainSnapshotDto;
import com.railway.simulator.config.SignalSpeedConfig;
import com.railway.simulator.domain.SignalAspect;
import com.railway.simulator.domain.TrainStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
public class PeakHourSignalComplianceTest {

    @Autowired
    private SimulationService simulationService;

    @Autowired
    private AllocationService allocationService;

    @Autowired
    private AnalyticsService analyticsService;

    @BeforeEach
    void setup() {
        simulationService.resetSimulation();
        allocationService.runStationAllocation("BVI");
        allocationService.runStationAllocation("AND");
        allocationService.runStationAllocation("DDR");
        simulationService.syncSignallingState();
    }

    private void assertSignalComplianceOver2Hours(boolean peakHour, int speedMultiplier) {
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

        Map<String, String> prevBlock = new HashMap<>();
        Map<String, Double> prevProgress = new HashMap<>();

        int totalSpads = 0;
        int totalSpeedOverAspect = 0;
        int totalSharedBlockViolations = 0;

        for (int tick = 0; tick < totalTicks; tick++) {
            // Snapshot signals before tick
            var corridorBefore = simulationService.getCorridorState();
            Map<String, SignalAspect> signalsBefore = new HashMap<>();
            if (corridorBefore.getSignals() != null) {
                corridorBefore.getSignals().forEach(s -> signalsBefore.put(s.getSignalId(), s.getAspect()));
            }

            simulationService.advanceTrainStates(stepSeconds);

            var corridorAfter = simulationService.getCorridorState();

            // 1. Zero shared blocks assertion: no two trains in the same block simultaneously
            Map<String, List<TrainSnapshotDto>> blockOccupants = new HashMap<>();
            for (var train : corridorAfter.getTrains()) {
                if (train.getStatus() != TrainStatus.DEPARTED && train.getCurrentBlockId() != null) {
                    blockOccupants.computeIfAbsent(train.getCurrentBlockId(), k -> new ArrayList<>()).add(train);
                }
            }
            for (Map.Entry<String, List<TrainSnapshotDto>> entry : blockOccupants.entrySet()) {
                if (entry.getValue().size() > 1) {
                    totalSharedBlockViolations++;
                    System.err.printf("[SHARED_BLOCK] Tick %d | Block %s shared by: %s%n",
                            tick, entry.getKey(),
                            entry.getValue().stream().map(t -> t.getTrainNumber() + " (" + t.getStatus() + ")").toList());
                }
            }

            // 2. Transition SPAD checks from simulation engine's authoritative transition log
            List<SimulationService.BlockTransitionRecord> transitions = simulationService.getTransitionLog();
            for (SimulationService.BlockTransitionRecord tr : transitions) {
                if (tr.isSpad || tr.aspect == SignalAspect.RED) {
                    totalSpads++;
                    System.err.printf("[SPAD] Tick %d | Train %s entered %s whose entry signal was RED! Speed: %.1f km/h%n",
                            tick, tr.trainNumber, tr.toBlock, tr.speedKmH);
                }
            }
            simulationService.clearTransitionLog();

            // 3. Speed-over-aspect checks
            for (var train : corridorAfter.getTrains()) {
                String tNum = train.getTrainNumber();
                String currBlk = train.getCurrentBlockId();
                Double currProg = train.getProgressPercent();
                Double speed = train.getSpeedKmH() != null ? train.getSpeedKmH() : 0.0;

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

                    if (currentAspect == SignalAspect.RED) {
                        if (currProg != null && currProg >= 97.9 && speed > 0.5) {
                            totalSpeedOverAspect++;
                            System.err.printf("[SPEED_OVER_ASPECT_RED] Tick %d | Train %s moving at %.1f km/h at RED stop line (%s)%n",
                                    tick, tNum, speed, currBlk);
                        }
                    } else if (speed > limit + 0.5) {
                        totalSpeedOverAspect++;
                        System.err.printf("[SPEED_OVER_ASPECT] Tick %d | Train %s at %.1f km/h under aspect %s (Limit: %.1f)%n",
                                tick, tNum, speed, currentAspect, limit);
                    }
                }

                prevBlock.put(tNum, currBlk);
                prevProgress.put(tNum, currProg);
            }
        }

        // Operational Analytics SPAD counter verification
        assertEquals(0L, simulationService.getSignalsPassedAtDanger(), "Signals passed at danger counter must be 0");
        Map<String, Object> kpis = analyticsService.getDashboardKpis();
        assertEquals(0L, ((Number) kpis.get("signalsPassedAtDanger")).longValue(), "Analytics KPI signalsPassedAtDanger must be 0");

        assertEquals(0, totalSpads, String.format("Expected 0 SPADs in mode (peak=%b, %dx), found: %d", peakHour, speedMultiplier, totalSpads));
        assertEquals(0, totalSpeedOverAspect, String.format("Expected 0 speed-over-aspect violations in mode (peak=%b, %dx), found: %d", peakHour, speedMultiplier, totalSpeedOverAspect));
        assertEquals(0, totalSharedBlockViolations, String.format("Expected 0 shared blocks in mode (peak=%b, %dx), found: %d", peakHour, speedMultiplier, totalSharedBlockViolations));
    }

    @Test
    @DisplayName("Compliance 1: Normal mode (27 trains) at 5x for 2 hours - zero SPAD, zero speed-over-aspect, zero shared blocks")
    void testNormalMode5xCompliance() {
        assertSignalComplianceOver2Hours(false, 5);
    }

    @Test
    @DisplayName("Compliance 2: Normal mode (27 trains) at 10x for 2 hours - zero SPAD, zero speed-over-aspect, zero shared blocks")
    void testNormalMode10xCompliance() {
        assertSignalComplianceOver2Hours(false, 10);
    }

    @Test
    @DisplayName("Compliance 3: Peak Hour mode (57 trains) at 5x for 2 hours - zero SPAD, zero speed-over-aspect, zero shared blocks")
    void testPeakHour5xCompliance() {
        assertSignalComplianceOver2Hours(true, 5);
    }

    @Test
    @DisplayName("Compliance 4: Peak Hour mode (57 trains) at 10x for 2 hours - zero SPAD, zero speed-over-aspect, zero shared blocks")
    void testPeakHour10xCompliance() {
        assertSignalComplianceOver2Hours(true, 10);
    }
}

