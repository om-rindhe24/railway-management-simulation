package com.railway.simulator.service;

import com.railway.simulator.config.PeakHourFleetConfig;
import com.railway.simulator.config.SignalSpeedConfig;
import com.railway.simulator.controller.dto.TrainSnapshotDto;
import com.railway.simulator.domain.*;
import com.railway.simulator.repository.TrainRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.*;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Rush Load Stress Test Suite
 *
 * Runs every trace and invariant check with Peak Hour ON from tick 0 at full
 * density (27 base + 30 SIM-PK = 57 trains) at 1x, 5x and 10x speed.
 * Also tests mid-run activation (trains already moving when peak is enabled).
 *
 * Tracked metrics per run:
 *  - Total SPADs (signals passed at danger)
 *  - Shared block violations (two trains in one block)
 *  - Speed-over-aspect violations
 *  - Teleport violations (progress jump > 40% in one step within same block)
 *  - Line-type violations (slow on fast, long-distance on slow)
 *  - Max trains held simultaneously (status == WAITING)
 *  - Longest per-station queue (trains WAITING targeting the same station)
 *  - Tick wall-clock performance (must be < 500ms)
 *  - Fleet size stability (must stay at 57)
 *  - Movement liveness (>= 80% of trains move > 5km over 2 hours)
 */
@SpringBootTest
public class RushLoadStressTest {

    @Autowired private SimulationService simulationService;
    @Autowired private AllocationService allocationService;
    @Autowired private AnalyticsService analyticsService;
    @Autowired private TrainRepository trainRepository;

    @BeforeEach
    void setup() {
        simulationService.resetSimulation();
        allocationService.runStationAllocation("BVI");
        allocationService.runStationAllocation("AND");
        allocationService.runStationAllocation("DDR");
        simulationService.syncSignallingState();
    }

    // ───────────────────────────────────────────────────────────
    //  Core rush-load runner: 2 simulated hours of full-density
    // ───────────────────────────────────────────────────────────

    private static class RushLoadResult {
        int totalSpads = 0;
        int totalSpeedOverAspect = 0;
        int totalSharedBlockViolations = 0;
        int totalTeleportViolations = 0;
        int totalLineTypeViolations = 0;
        long maxTickMs = 0;
        int maxHeldSimultaneously = 0;
        String maxHeldTick = "";
        int longestStationQueue = 0;
        String longestQueueStation = "";
        String longestQueueTick = "";
        int fleetSizeAtEnd = 0;
        double movedPercent = 0.0;
        long trainsMovedOver5Km = 0;
    }

    /**
     * Runs a full 2-simulated-hour rush load with all 57 trains and collects
     * every invariant violation and queue metric.
     *
     * @param speedMultiplier 1, 5, or 10
     * @param peakFromTick0   true = enable peak before first tick;
     *                        false = enable peak after warmup ticks
     * @param warmupTicks     if peakFromTick0==false, how many ticks to run
     *                        before enabling peak (trains already moving)
     */
    private RushLoadResult runRushLoad(int speedMultiplier, boolean peakFromTick0, int warmupTicks) {
        // Fresh reset
        simulationService.resetSimulation();
        allocationService.runStationAllocation("BVI");
        allocationService.runStationAllocation("AND");
        allocationService.runStationAllocation("DDR");
        simulationService.syncSignallingState();

        long stepSeconds;
        int totalTicks;
        switch (speedMultiplier) {
            case 1:  stepSeconds = 5L;  totalTicks = 1440; break;   // 1440 * 5 = 7200s
            case 5:  stepSeconds = 25L; totalTicks = 288;  break;   //  288 * 25 = 7200s
            case 10: stepSeconds = 50L; totalTicks = 144;  break;   //  144 * 50 = 7200s
            default: stepSeconds = 25L; totalTicks = 288;  break;
        }

        // Enable peak hour either from tick 0 or after warmup
        if (peakFromTick0) {
            simulationService.setPeakHourMode(true);
            assertEquals(57, trainRepository.findByIsActiveTrue().size(),
                    "Fleet must be 57 after enabling peak hour");
        }

        // Record initial categories
        Map<String, TrainCategory> categories = new HashMap<>();
        for (Train t : trainRepository.findByIsActiveTrue()) {
            categories.put(t.getTrainNumber(), t.getTrainCategory());
        }

        // Cumulative distance tracking
        Map<String, Double> totalDistKm = new HashMap<>();
        for (Train t : trainRepository.findByIsActiveTrue()) {
            totalDistKm.put(t.getTrainNumber(), 0.0);
        }

        RushLoadResult r = new RushLoadResult();

        for (int tick = 0; tick < totalTicks; tick++) {

            // Mid-run activation: enable peak after warmup
            if (!peakFromTick0 && tick == warmupTicks) {
                simulationService.setPeakHourMode(true);
                assertEquals(57, trainRepository.findByIsActiveTrue().size(),
                        "Fleet must be 57 after mid-run peak hour activation");
                // Seed distance tracking for new peak trains
                for (Train t : trainRepository.findByIsActiveTrue()) {
                    categories.putIfAbsent(t.getTrainNumber(), t.getTrainCategory());
                    totalDistKm.putIfAbsent(t.getTrainNumber(), 0.0);
                }
            }

            // Snapshot before tick
            Map<String, Double> progressBefore = new HashMap<>();
            Map<String, String> blockBefore = new HashMap<>();
            for (Train t : trainRepository.findByIsActiveTrue()) {
                progressBefore.put(t.getTrainNumber(), t.getProgressPercent());
                blockBefore.put(t.getTrainNumber(), t.getCurrentBlockId());
            }

            // Advance with wall-clock timing
            long t0 = System.nanoTime();
            simulationService.advanceTrainStates(stepSeconds);
            long elapsedMs = (System.nanoTime() - t0) / 1_000_000L;
            if (elapsedMs > r.maxTickMs) r.maxTickMs = elapsedMs;
            assertTrue(elapsedMs < 500L,
                    "Tick " + tick + " at " + speedMultiplier + "x took " + elapsedMs + "ms (>500ms)");

            var corridor = simulationService.getCorridorState();

            // ── Invariant 1: No shared blocks ──
            Map<String, List<TrainSnapshotDto>> blockOccupants = new HashMap<>();
            for (var ts : corridor.getTrains()) {
                if (ts.getStatus() != TrainStatus.DEPARTED && ts.getCurrentBlockId() != null) {
                    blockOccupants.computeIfAbsent(ts.getCurrentBlockId(), k -> new ArrayList<>()).add(ts);
                }
            }
            for (var entry : blockOccupants.entrySet()) {
                if (entry.getValue().size() > 1) {
                    r.totalSharedBlockViolations++;
                    System.err.printf("[SHARED_BLOCK] %dx tick %d | Block %s shared by: %s%n",
                            speedMultiplier, tick, entry.getKey(),
                            entry.getValue().stream().map(t -> t.getTrainNumber() + "(" + t.getStatus() + ")")
                                    .collect(Collectors.joining(", ")));
                }
            }

            // ── Invariant 2: No SPADs from transition log ──
            for (SimulationService.BlockTransitionRecord tr : simulationService.getTransitionLog()) {
                if (tr.isSpad || tr.aspect == SignalAspect.RED) {
                    r.totalSpads++;
                    System.err.printf("[SPAD] %dx tick %d | Train %s entered %s at RED (%.1f km/h)%n",
                            speedMultiplier, tick, tr.trainNumber, tr.toBlock, tr.speedKmH);
                }
            }
            simulationService.clearTransitionLog();

            // ── Invariant 3: Speed-over-aspect ──
            for (var ts : corridor.getTrains()) {
                SignalAspect aspect = ts.getSignalAspect();
                double speed = ts.getSpeedKmH() != null ? ts.getSpeedKmH() : 0.0;
                Double prog = ts.getProgressPercent();
                if (aspect != null) {
                    if (aspect == SignalAspect.RED) {
                        if (prog != null && prog >= 97.9 && speed > 0.5) {
                            r.totalSpeedOverAspect++;
                            System.err.printf("[SPEED@RED] %dx tick %d | %s at %.1f km/h p=%.1f%%%n",
                                    speedMultiplier, tick, ts.getTrainNumber(), speed, prog);
                        }
                    } else {
                        double limit;
                        switch (aspect) {
                            case YELLOW:        limit = SignalSpeedConfig.SPEED_YELLOW; break;
                            case DOUBLE_YELLOW: limit = SignalSpeedConfig.SPEED_DOUBLE_YELLOW; break;
                            default:            limit = SignalSpeedConfig.MAX_CORRIDOR_SPEED; break;
                        }
                        if (speed > limit + 0.5) {
                            r.totalSpeedOverAspect++;
                            System.err.printf("[SPEED_OVER] %dx tick %d | %s at %.1f km/h, aspect %s limit %.1f%n",
                                    speedMultiplier, tick, ts.getTrainNumber(), speed, aspect, limit);
                        }
                    }
                }
            }

            // ── Invariant 4: No teleporting (scaled by stepSeconds: delta <= (stepSeconds/25.0)*40.0%) ──
            double maxAllowedDelta = (stepSeconds / 25.0) * 40.0;
            for (var ts : corridor.getTrains()) {
                String tn = ts.getTrainNumber();
                String oldB = blockBefore.get(tn);
                Double oldP = progressBefore.get(tn);
                String newB = ts.getCurrentBlockId();
                Double newP = ts.getProgressPercent();
                if (ts.getStatus() == TrainStatus.APPROACHING && oldB != null && oldB.equals(newB)
                        && oldP != null && newP != null) {
                    double delta = newP - oldP;
                    if (delta > maxAllowedDelta + 0.5) {
                        r.totalTeleportViolations++;
                        System.err.printf("[TELEPORT] %dx tick %d | %s jumped %.1f%% in block %s (limit: %.1f%%)%n",
                                speedMultiplier, tick, tn, delta, newB, maxAllowedDelta);
                    }
                    totalDistKm.merge(tn, 2.5 * (Math.max(0, delta) / 100.0), Double::sum);
                } else if (oldB != null && !oldB.equals(newB)) {
                    totalDistKm.merge(tn, 2.0, Double::sum);
                }
            }

            // ── Invariant 5: Line-type correctness ──
            for (var ts : corridor.getTrains()) {
                String blkId = ts.getCurrentBlockId();
                TrainCategory cat = ts.getTrainCategory();
                if (blkId != null && blkId.length() >= 2 && !blkId.matches(".*-P\\d+")) {
                    String lineCode = blkId.substring(blkId.length() - 2);
                    if (cat == TrainCategory.SLOW_LOCAL) {
                        if ("FU".equals(lineCode) || "FD".equals(lineCode)) {
                            r.totalLineTypeViolations++;
                            System.err.printf("[LINE_TYPE] %dx tick %d | Slow local %s on fast block %s%n",
                                    speedMultiplier, tick, ts.getTrainNumber(), blkId);
                        }
                    } else if (cat == TrainCategory.EXPRESS || cat == TrainCategory.SUPERFAST
                            || cat == TrainCategory.PREMIUM) {
                        if ("SU".equals(lineCode) || "SD".equals(lineCode)) {
                            r.totalLineTypeViolations++;
                            System.err.printf("[LINE_TYPE] %dx tick %d | Long-dist %s on slow block %s%n",
                                    speedMultiplier, tick, ts.getTrainNumber(), blkId);
                        }
                    }
                }
            }

            // ── Metric: Trains held simultaneously ──
            List<TrainSnapshotDto> waitingTrains = corridor.getTrains().stream()
                    .filter(ts -> ts.getStatus() == TrainStatus.WAITING)
                    .toList();
            int heldNow = waitingTrains.size();
            if (heldNow > r.maxHeldSimultaneously) {
                r.maxHeldSimultaneously = heldNow;
                r.maxHeldTick = "tick " + tick + " (" + speedMultiplier + "x)";
            }

            // ── Metric: Longest per-station queue ──
            Map<String, List<String>> stationQueues = new HashMap<>();
            for (var ts : waitingTrains) {
                String tgt = ts.getTargetStationCode();
                if (tgt != null) {
                    stationQueues.computeIfAbsent(tgt, k -> new ArrayList<>()).add(ts.getTrainNumber());
                }
            }
            for (var entry : stationQueues.entrySet()) {
                if (entry.getValue().size() > r.longestStationQueue) {
                    r.longestStationQueue = entry.getValue().size();
                    r.longestQueueStation = entry.getKey();
                    r.longestQueueTick = "tick " + tick + " (" + speedMultiplier + "x)";
                }
            }
        }

        // ── Post-run checks ──
        List<Train> endFleet = trainRepository.findByIsActiveTrue();
        r.fleetSizeAtEnd = endFleet.size();
        assertEquals(57, r.fleetSizeAtEnd,
                "Fleet must stay at 57 trains after 2 hours at " + speedMultiplier + "x");

        r.trainsMovedOver5Km = totalDistKm.values().stream().filter(d -> d >= 5.0).count();
        r.movedPercent = (double) r.trainsMovedOver5Km / endFleet.size() * 100.0;

        // Analytics counters
        assertEquals(0L, simulationService.getSignalsPassedAtDanger(),
                "Signals-passed-at-danger counter must be 0 at " + speedMultiplier + "x");
        Map<String, Object> kpis = analyticsService.getDashboardKpis();
        assertEquals(0L, ((Number) kpis.get("signalsPassedAtDanger")).longValue(),
                "Analytics KPI signalsPassedAtDanger must be 0 at " + speedMultiplier + "x");

        return r;
    }

    private void printResult(String label, RushLoadResult r) {
        System.out.println();
        System.out.println("══════════════════════════════════════════════════════════");
        System.out.println("  RUSH LOAD RESULT: " + label);
        System.out.println("══════════════════════════════════════════════════════════");
        System.out.printf("  SPADs:                    %d%n", r.totalSpads);
        System.out.printf("  Shared block violations:  %d%n", r.totalSharedBlockViolations);
        System.out.printf("  Speed-over-aspect:        %d%n", r.totalSpeedOverAspect);
        System.out.printf("  Teleport violations:      %d%n", r.totalTeleportViolations);
        System.out.printf("  Line-type violations:     %d%n", r.totalLineTypeViolations);
        System.out.printf("  Max tick wall-clock:      %d ms%n", r.maxTickMs);
        System.out.printf("  Fleet size at end:        %d%n", r.fleetSizeAtEnd);
        System.out.printf("  Trains moved >=5km:       %d / %d (%.1f%%)%n",
                r.trainsMovedOver5Km, r.fleetSizeAtEnd, r.movedPercent);
        System.out.println("  ──────────────────────────────────────────────────────");
        System.out.printf("  MAX TRAINS HELD AT ONCE:  %d  (at %s)%n",
                r.maxHeldSimultaneously, r.maxHeldTick);
        System.out.printf("  LONGEST STATION QUEUE:    %d trains at %s  (at %s)%n",
                r.longestStationQueue, r.longestQueueStation, r.longestQueueTick);
        System.out.println("══════════════════════════════════════════════════════════");
    }

    private void assertAllInvariants(RushLoadResult r, String label) {
        assertEquals(0, r.totalSpads,
                label + ": Expected 0 SPADs, found " + r.totalSpads);
        assertEquals(0, r.totalSharedBlockViolations,
                label + ": Expected 0 shared blocks, found " + r.totalSharedBlockViolations);
        assertEquals(0, r.totalSpeedOverAspect,
                label + ": Expected 0 speed-over-aspect, found " + r.totalSpeedOverAspect);
        assertEquals(0, r.totalTeleportViolations,
                label + ": Expected 0 teleports, found " + r.totalTeleportViolations);
        assertEquals(0, r.totalLineTypeViolations,
                label + ": Expected 0 line-type violations, found " + r.totalLineTypeViolations);
        assertTrue(r.movedPercent >= 80.0,
                label + ": >=80% trains must move >5km (got " + r.movedPercent + "%)");
    }

    // ───────────────────────────────────────────────────────────
    //  Test cases
    // ───────────────────────────────────────────────────────────

    @Test
    @DisplayName("Rush Load 1x: Peak Hour ON from tick 0, 57 trains, 2 simulated hours at 1x")
    void testRushLoad1x() {
        RushLoadResult r = runRushLoad(1, true, 0);
        printResult("Peak Hour @ 1x (from tick 0)", r);
        assertAllInvariants(r, "1x");
    }

    @Test
    @DisplayName("Rush Load 5x: Peak Hour ON from tick 0, 57 trains, 2 simulated hours at 5x")
    void testRushLoad5x() {
        RushLoadResult r = runRushLoad(5, true, 0);
        printResult("Peak Hour @ 5x (from tick 0)", r);
        assertAllInvariants(r, "5x");
    }

    @Test
    @DisplayName("Rush Load 10x: Peak Hour ON from tick 0, 57 trains, 2 simulated hours at 10x")
    void testRushLoad10x() {
        RushLoadResult r = runRushLoad(10, true, 0);
        printResult("Peak Hour @ 10x (from tick 0)", r);
        assertAllInvariants(r, "10x");
    }

    @Test
    @DisplayName("Rush Load Mid-Run: 27 trains warm up 20 ticks at 5x, then Peak Hour ON for rest of 2 hours")
    void testRushLoadMidRun5x() {
        RushLoadResult r = runRushLoad(5, false, 20);
        printResult("Mid-Run Peak @ 5x (activated at tick 20)", r);
        assertAllInvariants(r, "mid-run 5x");
    }

    // ───────────────────────────────────────────────────────────
    //  Combined summary (runs all four and prints a comparison)
    // ───────────────────────────────────────────────────────────

    @Test
    @DisplayName("Rush Load Summary: All 4 scenarios with comparison table")
    void testRushLoadAllScenariosWithSummary() {
        RushLoadResult r1x  = runRushLoad(1,  true,  0);
        RushLoadResult r5x  = runRushLoad(5,  true,  0);
        RushLoadResult r10x = runRushLoad(10, true,  0);
        RushLoadResult rMid = runRushLoad(5,  false, 20);

        printResult("1x (from tick 0)",  r1x);
        printResult("5x (from tick 0)",  r5x);
        printResult("10x (from tick 0)", r10x);
        printResult("Mid-Run 5x (tick 20)", rMid);

        System.out.println();
        System.out.println("╔═══════════════════════════════════════════════════════════════════════════════════════╗");
        System.out.println("║                     RUSH LOAD COMPARISON TABLE (57 trains, 2 sim-hours)             ║");
        System.out.println("╠═════════════════════╦═══════╦═══════════╦══════════╦══════════╦═══════════╦══════════╣");
        System.out.println("║ Scenario            ║ SPADs ║ SharedBlk ║ Teleport ║ LineType ║ MaxHeld   ║ LongQ   ║");
        System.out.println("╠═════════════════════╬═══════╬═══════════╬══════════╬══════════╬═══════════╬══════════╣");
        System.out.printf( "║ Peak@1x (tick 0)    ║  %3d  ║    %3d    ║   %3d    ║   %3d    ║   %3d     ║   %3d    ║%n",
                r1x.totalSpads, r1x.totalSharedBlockViolations, r1x.totalTeleportViolations,
                r1x.totalLineTypeViolations, r1x.maxHeldSimultaneously, r1x.longestStationQueue);
        System.out.printf( "║ Peak@5x (tick 0)    ║  %3d  ║    %3d    ║   %3d    ║   %3d    ║   %3d     ║   %3d    ║%n",
                r5x.totalSpads, r5x.totalSharedBlockViolations, r5x.totalTeleportViolations,
                r5x.totalLineTypeViolations, r5x.maxHeldSimultaneously, r5x.longestStationQueue);
        System.out.printf( "║ Peak@10x (tick 0)   ║  %3d  ║    %3d    ║   %3d    ║   %3d    ║   %3d     ║   %3d    ║%n",
                r10x.totalSpads, r10x.totalSharedBlockViolations, r10x.totalTeleportViolations,
                r10x.totalLineTypeViolations, r10x.maxHeldSimultaneously, r10x.longestStationQueue);
        System.out.printf( "║ Mid-Run@5x (tick20) ║  %3d  ║    %3d    ║   %3d    ║   %3d    ║   %3d     ║   %3d    ║%n",
                rMid.totalSpads, rMid.totalSharedBlockViolations, rMid.totalTeleportViolations,
                rMid.totalLineTypeViolations, rMid.maxHeldSimultaneously, rMid.longestStationQueue);
        System.out.println("╠═════════════════════╬═══════╬═══════════╬══════════╬══════════╬═══════════╬══════════╣");
        System.out.printf( "║ Peak@1x  max tick   ║               %4d ms                                        ║%n", r1x.maxTickMs);
        System.out.printf( "║ Peak@5x  max tick   ║               %4d ms                                        ║%n", r5x.maxTickMs);
        System.out.printf( "║ Peak@10x max tick   ║               %4d ms                                        ║%n", r10x.maxTickMs);
        System.out.printf( "║ MidRun   max tick   ║               %4d ms                                        ║%n", rMid.maxTickMs);
        System.out.println("╠═════════════════════╩═══════╩═══════════╩══════════╩══════════╩═══════════╩══════════╣");
        System.out.printf( "║ 1x  longest queue: %d trains at station %s (%s)%n",
                r1x.longestStationQueue, r1x.longestQueueStation, r1x.longestQueueTick);
        System.out.printf( "║ 5x  longest queue: %d trains at station %s (%s)%n",
                r5x.longestStationQueue, r5x.longestQueueStation, r5x.longestQueueTick);
        System.out.printf( "║ 10x longest queue: %d trains at station %s (%s)%n",
                r10x.longestStationQueue, r10x.longestQueueStation, r10x.longestQueueTick);
        System.out.printf( "║ Mid longest queue: %d trains at station %s (%s)%n",
                rMid.longestStationQueue, rMid.longestQueueStation, rMid.longestQueueTick);
        System.out.println("╚═════════════════════════════════════════════════════════════════════════════════════════╝");

        // Hard assertions on all four
        assertAllInvariants(r1x,  "1x");
        assertAllInvariants(r5x,  "5x");
        assertAllInvariants(r10x, "10x");
        assertAllInvariants(rMid, "mid-run");
    }
}

