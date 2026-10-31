package com.railway.simulator.service;

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
import java.util.stream.Collectors;
import com.railway.simulator.config.PeakHourFleetConfig;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
public class PeakHourSafetyAndUtilizationIntegrationTest {

    @Autowired
    private SimulationService simulationService;

    @Autowired
    private AllocationService allocationService;

    @Autowired
    private AnalyticsService analyticsService;

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
        allocationService.runStationAllocation("DDR");
        simulationService.syncSignallingState();
    }

    @Test
    @DisplayName("Requirement 2: Closing a platform increments the Dynamic Reallocations counter")
    void testClosingPlatformIncrementsReallocationCounter() {
        Map<String, Object> beforeKpis = analyticsService.getDashboardKpis();
        long initialReallocations = ((Number) beforeKpis.get("totalReallocations")).longValue();

        Platform p5 = platformRepository.findByStationCodeAndPlatformNumber("BVI", 5).orElseThrow();
        Train train = trainRepository.findByTrainNumber("SIM-LOC-901").orElseThrow();
        train.setAssignedPlatformId(p5.getPlatformId());
        train.setAssignedPlatformNumber(5);
        train.setStatus(TrainStatus.APPROACHING);
        trainRepository.save(train);

        // Close P5: triggers reallocation to another platform or held at home signal
        allocationService.handlePlatformClosure(p5.getPlatformId(), "Maintenance closure");

        Map<String, Object> afterKpis = analyticsService.getDashboardKpis();
        long afterReallocations = ((Number) afterKpis.get("totalReallocations")).longValue();

        assertTrue(afterReallocations > initialReallocations,
                "Dynamic Reallocations counter must rise when a platform is closed! Before: "
                        + initialReallocations + ", After: " + afterReallocations);
    }

    @Test
    @DisplayName("Requirement 3 & 4: Peak Hour toggle adds realistic mix (24 locals, 6 long-distance), enforces fast lines, and cleanly removes on disable")
    void testPeakHourToggleAndCleanRemoval() {
        List<Train> baselineFleet = trainRepository.findByIsActiveTrue();
        assertEquals(27, baselineFleet.size(), "Default fleet must be 27 trains");
        assertFalse(simulationService.isPeakHour(), "Default mode must have isPeakHour = false");

        // Baseline category counts
        Map<TrainCategory, Long> baseCatCounts = baselineFleet.stream()
                .collect(Collectors.groupingBy(Train::getTrainCategory, Collectors.counting()));
        assertEquals(9L, baseCatCounts.get(TrainCategory.SLOW_LOCAL));
        assertEquals(6L, baseCatCounts.get(TrainCategory.FAST_LOCAL));
        assertEquals(3L, baseCatCounts.get(TrainCategory.EXPRESS));
        assertEquals(3L, baseCatCounts.get(TrainCategory.SUPERFAST));
        assertEquals(3L, baseCatCounts.get(TrainCategory.PREMIUM));
        assertEquals(2L, baseCatCounts.get(TrainCategory.SPECIAL));
        assertEquals(1L, baseCatCounts.get(TrainCategory.FREIGHT));

        // Enable peak hour
        simulationService.setPeakHourMode(true);
        assertTrue(simulationService.isPeakHour());
        List<Train> peakFleet = trainRepository.findByIsActiveTrue();
        assertEquals(57, peakFleet.size(), "Peak Hour fleet must contain 57 trains (27 baseline + 30 peak)");

        // Assert no two trains start in the same block
        Map<String, List<String>> blockToTrains = new HashMap<>();
        for (Train t : peakFleet) {
            if (t.getCurrentBlockId() != null) {
                blockToTrains.computeIfAbsent(t.getCurrentBlockId(), k -> new ArrayList<>()).add(t.getTrainNumber());
            }
        }
        for (Map.Entry<String, List<String>> entry : blockToTrains.entrySet()) {
            assertEquals(1, entry.getValue().size(),
                    "Block " + entry.getKey() + " must have only 1 train, found: " + entry.getValue());
        }

        // Assert Peak Hour category breakdown matches configured constants
        Map<TrainCategory, Long> peakCatCounts = peakFleet.stream()
                .collect(Collectors.groupingBy(Train::getTrainCategory, Collectors.counting()));
        assertEquals(9L + 12L, peakCatCounts.get(TrainCategory.SLOW_LOCAL)); // 21
        assertEquals(6L + 12L, peakCatCounts.get(TrainCategory.FAST_LOCAL)); // 18
        assertEquals(3L + PeakHourFleetConfig.EXTRA_EXPRESS_COUNT, peakCatCounts.get(TrainCategory.EXPRESS)); // 5
        assertEquals(3L + PeakHourFleetConfig.EXTRA_SUPERFAST_COUNT, peakCatCounts.get(TrainCategory.SUPERFAST)); // 5
        assertEquals(3L + PeakHourFleetConfig.EXTRA_PREMIUM_COUNT, peakCatCounts.get(TrainCategory.PREMIUM)); // 5
        assertEquals(2L, peakCatCounts.get(TrainCategory.SPECIAL)); // 2
        assertEquals(1L, peakCatCounts.get(TrainCategory.FREIGHT)); // 1

        // Assert extra long-distance and fast local trains are on FAST lines
        List<Train> peakExtraTrains = peakFleet.stream()
                .filter(t -> t.getTrainNumber().startsWith("SIM-PK-"))
                .toList();
        assertEquals(PeakHourFleetConfig.TOTAL_PEAK_TRAINS, peakExtraTrains.size());

        for (Train t : peakExtraTrains) {
            boolean isFastService = (t.getTrainCategory() == TrainCategory.FAST_LOCAL
                    || t.getTrainCategory() == TrainCategory.EXPRESS
                    || t.getTrainCategory() == TrainCategory.SUPERFAST
                    || t.getTrainCategory() == TrainCategory.PREMIUM);
            String blockId = t.getCurrentBlockId();
            assertNotNull(blockId);
            if (isFastService) {
                assertTrue(blockId.endsWith("-FU") || blockId.endsWith("-FD"),
                        "Fast service " + t.getTrainNumber() + " (" + t.getTrainCategory() + ") must be on FAST line: " + blockId);
            } else {
                assertTrue(blockId.endsWith("-SU") || blockId.endsWith("-SD"),
                        "Slow service " + t.getTrainNumber() + " must be on SLOW line: " + blockId);
            }
        }

        // Disable peak hour
        simulationService.setPeakHourMode(false);
        assertFalse(simulationService.isPeakHour());
        List<Train> restoredFleet = trainRepository.findByIsActiveTrue();
        assertEquals(27, restoredFleet.size(), "Fleet must cleanly return to 27 trains when peak hour disabled");

        boolean anyPeakTrainLeft = restoredFleet.stream().anyMatch(t -> t.getTrainNumber().startsWith("SIM-PK-"));
        assertFalse(anyPeakTrainLeft, "No SIM-PK- trains must remain after turning peak hour off");
    }

    @Test
    @DisplayName("Requirement 1: Rolling 60-sim-minute platform utilization, line-type utilization, and busiest 3 stations")
    void testRollingPlatformUtilizationAndBreakdowns() {
        // Run a few ticks to accumulate simulation data
        for (int i = 0; i < 10; i++) {
            simulationService.advanceTrainStates(15L);
        }

        Map<String, Object> kpis = analyticsService.getDashboardKpis();
        assertNotNull(kpis.get("platformUtilizationPercent"));
        assertNotNull(kpis.get("snapshotPlatformUtilizationPercent"));
        assertNotNull(kpis.get("rolling60MinUtilizationPercent"));
        assertNotNull(kpis.get("occupiedPlatformMinutes"));
        assertNotNull(kpis.get("availablePlatformMinutes"));

        // Busiest 3 stations
        @SuppressWarnings("unchecked")
        List<SimulationService.StationUtilizationDto> busiest =
                (List<SimulationService.StationUtilizationDto>) kpis.get("busiestStations");
        assertNotNull(busiest);
        assertTrue(busiest.size() <= 3, "Must return at most 3 busiest stations");
        for (int i = 0; i < busiest.size() - 1; i++) {
            assertTrue(busiest.get(i).getUtilizationPercent() >= busiest.get(i + 1).getUtilizationPercent(),
                    "Busiest stations must be sorted in descending order of utilization");
        }

        // Line type utilization
        @SuppressWarnings("unchecked")
        Map<String, Double> lineTypeUtil = (Map<String, Double>) kpis.get("lineTypeUtilization");
        assertNotNull(lineTypeUtil);
        assertTrue(lineTypeUtil.containsKey("FAST_UP"));
        assertTrue(lineTypeUtil.containsKey("FAST_DOWN"));
        assertTrue(lineTypeUtil.containsKey("SLOW_UP"));
        assertTrue(lineTypeUtil.containsKey("SLOW_DOWN"));
    }

    @Test
    @DisplayName("BUG 2: Zero-history 0/0 edge case produces 0.0% rolling utilization with 0m occupied / 0m available without NaN")
    void testRollingPlatformUtilizationZeroDividedByZeroSafe() {
        simulationService.resetSimulation();

        // Immediately query analytics before any ticks run
        Map<String, Object> kpis = analyticsService.getDashboardKpis();
        assertEquals(0.0, kpis.get("rolling60MinUtilizationPercent"), "Rolling utilization must be 0.0% when 0 history");
        assertEquals(0.0, kpis.get("occupiedPlatformMinutes"), "Occupied platform minutes must be 0.0 when 0 history");
        assertEquals(0.0, kpis.get("availablePlatformMinutes"), "Available platform minutes must be 0.0 when 0 history");
        assertEquals(0, kpis.get("historyMinutes"), "History minutes must be 0");
        assertFalse(Double.isNaN((Double) kpis.get("rolling60MinUtilizationPercent")));
    }

    @Test
    @DisplayName("BUG 2: Snapshot and Rolling utilization are distinct, correctly bound, and non-swapped")
    void testSnapshotVsRollingBindingConsistency() {
        simulationService.resetSimulation();
        // Run 40 ticks of 15s = 600s = 10 simulated minutes
        for (int i = 0; i < 40; i++) {
            simulationService.advanceTrainStates(15L);
        }

        Map<String, Object> kpis = analyticsService.getDashboardKpis();

        int occupiedNow = ((Number) kpis.get("occupiedPlatforms")).intValue();
        int totalPlats = ((Number) kpis.get("totalPlatforms")).intValue();
        int closedPlats = ((Number) kpis.get("closedPlatforms")).intValue();
        int openPlats = totalPlats - closedPlats;

        double expectedSnapshot = Math.round(((double) occupiedNow / openPlats * 100.0) * 10.0) / 10.0;
        double actualSnapshot = ((Number) kpis.get("snapshotPlatformUtilizationPercent")).doubleValue();
        assertEquals(expectedSnapshot, actualSnapshot, "Snapshot utilization must strictly match occupied/open platforms now");

        double occMins = ((Number) kpis.get("occupiedPlatformMinutes")).doubleValue();
        double availMins = ((Number) kpis.get("availablePlatformMinutes")).doubleValue();
        double actualRolling = ((Number) kpis.get("rolling60MinUtilizationPercent")).doubleValue();

        assertTrue(availMins > 0.0, "Available platform minutes must be > 0 after 10 simulated minutes");
        double expectedRolling = Math.round((occMins / availMins * 100.0) * 10.0) / 10.0;
        assertEquals(expectedRolling, actualRolling, "Rolling utilization must be strictly consistent with occupiedMins / availMins");

        int historyMins = ((Number) kpis.get("historyMinutes")).intValue();
        assertEquals(10, historyMins, "History minutes should report 10 min after 600 simulated seconds");
    }

    @Test
    @DisplayName("Requirement 4: Peak Hour 2-hour simulation safety (55 trains, 5x speed): no teleport, no block collision, no line-type violation, >=80% moved >5km, tick <500ms")
    void testPeakHour2HourSimulationSafety() {
        simulationService.resetSimulation();
        allocationService.runStationAllocation("BVI");
        allocationService.runStationAllocation("AND");
        allocationService.runStationAllocation("DDR");
        simulationService.syncSignallingState();

        simulationService.setPeakHourMode(true);
        List<Train> initialFleet = trainRepository.findByIsActiveTrue();
        assertEquals(57, initialFleet.size());

        Map<String, TrainCategory> categories = new HashMap<>();
        Map<String, String> initialBlocks = new HashMap<>();
        for (Train t : initialFleet) {
            categories.put(t.getTrainNumber(), t.getTrainCategory());
            initialBlocks.put(t.getTrainNumber(), t.getCurrentBlockId());
        }

        // Track cumulative distance moved by each train across the 2 hours
        Map<String, Double> totalDistanceMovedKm = new HashMap<>();
        for (Train t : initialFleet) {
            totalDistanceMovedKm.put(t.getTrainNumber(), 0.0);
        }

        int ticks = 288; // 288 * 25s = 7200s = 2 hours
        for (int i = 0; i < ticks; i++) {
            Map<String, Double> progressBefore = new HashMap<>();
            Map<String, String> blockBefore = new HashMap<>();
            for (Train t : trainRepository.findByIsActiveTrue()) {
                progressBefore.put(t.getTrainNumber(), t.getProgressPercent());
                blockBefore.put(t.getTrainNumber(), t.getCurrentBlockId());
            }

            long startNs = System.nanoTime();
            simulationService.advanceTrainStates(25L);
            long elapsedMs = (System.nanoTime() - startNs) / 1_000_000L;

            // 1. Tick execution time must be under 500ms
            assertTrue(elapsedMs < 500L, "Tick " + i + " took " + elapsedMs + "ms, which exceeds 500ms threshold!");

            List<Train> currentTrains = trainRepository.findByIsActiveTrue();

            // 2. No two trains in the same block simultaneously
            Map<String, List<String>> blockOccupants = new HashMap<>();
            for (Train t : currentTrains) {
                if (t.getCurrentBlockId() != null) {
                    blockOccupants.computeIfAbsent(t.getCurrentBlockId(), k -> new ArrayList<>()).add(t.getTrainNumber());
                }
            }
            for (Map.Entry<String, List<String>> entry : blockOccupants.entrySet()) {
                assertEquals(1, entry.getValue().size(),
                        "Collision! Multiple trains in block " + entry.getKey() + " at tick " + i + ": " + entry.getValue());
            }

            // 3. No teleporting: within same block, delta <= 40%
            for (Train t : currentTrains) {
                String oldB = blockBefore.get(t.getTrainNumber());
                Double oldP = progressBefore.get(t.getTrainNumber());
                String newB = t.getCurrentBlockId();
                Double newP = t.getProgressPercent();

                if (t.getStatus() == TrainStatus.APPROACHING && oldB != null && oldB.equals(newB) && oldP != null && newP != null) {
                    double delta = newP - oldP;
                    assertTrue(delta <= 40.0, "Train " + t.getTrainNumber() + " moved " + delta + "% in 25s, exceeding physical limits!");
                    // Accumulate distance moved (approx 2.5km per block)
                    double blockDist = 2.5 * (delta / 100.0);
                    totalDistanceMovedKm.merge(t.getTrainNumber(), Math.max(0.0, blockDist), Double::sum);
                } else if (oldB != null && !oldB.equals(newB)) {
                    // Block transition: train traversed into next block
                    totalDistanceMovedKm.merge(t.getTrainNumber(), 2.0, Double::sum);
                }
            }
        }

        // Fleet size stays constant (57 trains active)
        List<Train> activeAfter2Hours = trainRepository.findByIsActiveTrue();
        assertEquals(57, activeAfter2Hours.size(), "Fleet size must stay constant at 57 trains after 2 hours");

        // No train on wrong line type or direction
        for (Train t : activeAfter2Hours) {
            TrainCategory initialCat = categories.get(t.getTrainNumber());
            assertEquals(initialCat, t.getTrainCategory(),
                    "Train category must be preserved: " + t.getTrainNumber());
            String blkId = t.getCurrentBlockId();
            if (blkId != null && blkId.length() >= 2 && !blkId.matches(".*-P\\d+")) {
                String lineCode = blkId.substring(blkId.length() - 2);
                if (initialCat == TrainCategory.SLOW_LOCAL) {
                    assertFalse("FU".equals(lineCode) || "FD".equals(lineCode),
                            "Slow local " + t.getTrainNumber() + " must not run on fast line block " + blkId);
                } else if (initialCat == TrainCategory.EXPRESS || initialCat == TrainCategory.SUPERFAST || initialCat == TrainCategory.PREMIUM) {
                    assertFalse("SU".equals(lineCode) || "SD".equals(lineCode),
                            "Long-distance " + t.getTrainNumber() + " must not run on slow line block " + blkId);
                }
            }
        }

        // At least 80% of trains have moved more than 5 km (no gridlock/deadlock)
        long trainsMovedMoreThan5Km = totalDistanceMovedKm.values().stream()
                .filter(d -> d >= 5.0)
                .count();
        double movedPercent = (double) trainsMovedMoreThan5Km / activeAfter2Hours.size() * 100.0;
        System.out.printf("Peak Hour Deadlock Check: %d of %d trains (%.1f%%) moved >= 5.0 km over 2 hours%n",
                trainsMovedMoreThan5Km, activeAfter2Hours.size(), movedPercent);
        assertTrue(movedPercent >= 80.0,
                "At least 80% of trains must move >5 km (no gridlock). Found: " + movedPercent + "%");
    }
}
