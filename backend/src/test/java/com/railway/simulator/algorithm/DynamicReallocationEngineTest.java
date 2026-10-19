package com.railway.simulator.algorithm;

import com.railway.simulator.algorithm.dto.AllocationDecision;
import com.railway.simulator.algorithm.dto.ReallocationResult;
import com.railway.simulator.domain.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class DynamicReallocationEngineTest {

    private DynamicReallocationEngine reallocationEngine;
    private LocalDateTime baseTime;
    private Station sampleStation;

    @BeforeEach
    void setUp() {
        reallocationEngine = new DynamicReallocationEngine();
        baseTime = LocalDateTime.of(2026, 9, 18, 14, 0);
        sampleStation = new Station("BVI", "Borivali", 4, true, true, true, true, true, 8);
        sampleStation.setStationId(1L);
    }

    @Test
    @DisplayName("Platform closure should evacuate train and reallocate to a compatible, available platform algorithmically")
    void testPlatformClosureReallocation() {
        // Setup 3 platforms
        Platform p1 = new Platform(sampleStation, 1, LineType.SLOW_UP, Set.of(TrainType.LOCAL));
        p1.setPlatformId(101L);
        Platform p2 = new Platform(sampleStation, 2, LineType.COMMON, Set.of(TrainType.LOCAL, TrainType.EXPRESS_MAIL));
        p2.setPlatformId(102L);
        Platform p3 = new Platform(sampleStation, 3, LineType.SLOW_UP, Set.of(TrainType.LOCAL));
        p3.setPlatformId(103L);

        List<Platform> platforms = List.of(p1, p2, p3);

        // Train originally assigned to Platform 1
        Train trainOnP1 = new Train("LOC-201", "Simulation Local 201", TrainType.LOCAL,
                "VR", "CCG", 5, baseTime, baseTime.plusMinutes(10), "BVI", "BVI");
        trainOnP1.setTrainId(21L);
        trainOnP1.setAssignedPlatformId(101L);
        trainOnP1.setAssignedPlatformNumber(1);

        PlatformAllocation allocOnP1 = new PlatformAllocation(
                21L, "LOC-201", "Simulation Local 201", 101L, 1, 1L, "BVI",
                baseTime, baseTime.plusMinutes(10), "Initial", "Normal", 0
        );

        List<Train> stationTrains = List.of(trainOnP1);
        List<PlatformAllocation> stationAllocations = new ArrayList<>(List.of(allocOnP1));

        // Act: Close Platform 1
        ReallocationResult result = reallocationEngine.handlePlatformClosure(
                101L, 1, "BVI", platforms, stationTrains, stationAllocations
        );

        // Assert: Algorithmic Verification (Rule 10 & 13)
        assertEquals(1, result.getTotalAffectedTrains(), "Should identify 1 affected train on closed platform");
        assertEquals(1, result.getReallocatedCount(), "Should reallocate the train");

        AllocationDecision decision = result.getReallocationDecisions().get(0);
        assertTrue(decision.isAllocated());
        assertNotEquals(101L, decision.getSelectedPlatformId(), "Must NOT reassign to the closed platform");

        // Verify the newly chosen platform is open and compatible with TrainType.LOCAL
        Platform chosenPlatform = platforms.stream()
                .filter(p -> p.getPlatformId().equals(decision.getSelectedPlatformId()))
                .findFirst()
                .orElseThrow();

        assertTrue(chosenPlatform.getSupportedTrainTypes().contains(TrainType.LOCAL),
                "Selected platform must be compatible with train type");
        assertFalse(chosenPlatform.getIsClosed(), "Selected platform must not be closed");
        assertNotNull(decision.getDetailedBreakdown(), "Must contain scoring rationale");
    }

    @Test
    @DisplayName("Train delay causing interval overlap should resolve conflict preserving priority")
    void testTrainDelayConflictResolution() {
        Platform p2 = new Platform(sampleStation, 2, LineType.COMMON, Set.of(TrainType.values()));
        p2.setPlatformId(102L);
        Platform p3 = new Platform(sampleStation, 3, LineType.COMMON, Set.of(TrainType.values()));
        p3.setPlatformId(103L);

        List<Platform> platforms = List.of(p2, p3);

        // Train A (Express, Priority 4): Scheduled at 14:00 to 14:10 on Platform 2
        Train trainA = new Train("EXP-101", "Simulation Express", TrainType.EXPRESS_MAIL,
                "VR", "MMCT", 4, baseTime, baseTime.plusMinutes(10), "BVI", "BVI");
        trainA.setTrainId(31L);
        trainA.setAssignedPlatformId(102L);
        trainA.setAssignedPlatformNumber(2);

        // Train B (Local, Priority 5): Scheduled at 14:20 to 14:28 on Platform 2
        Train trainB = new Train("LOC-301", "Simulation Local", TrainType.LOCAL,
                "VR", "CCG", 5, baseTime.plusMinutes(20), baseTime.plusMinutes(28), "BVI", "BVI");
        trainB.setTrainId(32L);
        trainB.setAssignedPlatformId(102L);
        trainB.setAssignedPlatformNumber(2);

        PlatformAllocation allocA = new PlatformAllocation(
                31L, "EXP-101", "Simulation Express", 102L, 2, 1L, "BVI",
                baseTime, baseTime.plusMinutes(10), "Initial", "Normal", 0
        );

        PlatformAllocation allocB = new PlatformAllocation(
                32L, "LOC-301", "Simulation Local", 102L, 2, 1L, "BVI",
                baseTime.plusMinutes(20), baseTime.plusMinutes(28), "Initial", "Normal", 0
        );

        List<Train> stationTrains = List.of(trainA, trainB);
        List<PlatformAllocation> stationAllocations = new ArrayList<>(List.of(allocA, allocB));

        // Act: Inject +15 min delay on Train A (new arrival 14:15, departure 14:25).
        // This overlaps with Train B [14:20, 14:28] with 3-minute safety buffer!
        ReallocationResult result = reallocationEngine.handleTrainDelay(
                trainA, 15, platforms, stationTrains, stationAllocations
        );

        // Assert
        assertTrue(result.getTotalAffectedTrains() >= 1, "Should detect affected trains from delay");
        assertTrue(result.getReallocatedCount() >= 1, "Should reallocate to resolve overlap");
        assertNotNull(result.getSummaryMessage());
    }
}
