package com.railway.simulator.algorithm;

import com.railway.simulator.algorithm.dto.AllocationDecision;
import com.railway.simulator.domain.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class PlatformAllocationEngineTest {

    private PlatformAllocationEngine allocationEngine;
    private LocalDateTime baseTime;
    private Station sampleStation;

    @BeforeEach
    void setUp() {
        allocationEngine = new PlatformAllocationEngine();
        baseTime = LocalDateTime.of(2026, 9, 18, 11, 0);
        sampleStation = new Station("BVI", "Borivali", 4, true, true, true, true, true, 8);
        sampleStation.setStationId(1L);
    }

    @Test
    @DisplayName("Algorithm should select a compatible, available, conflict-free platform")
    void testFeasiblePlatformSelection() {
        Platform p1 = new Platform(sampleStation, 1, LineType.SLOW_UP, Set.of(TrainType.LOCAL));
        p1.setPlatformId(101L);
        Platform p2 = new Platform(sampleStation, 2, LineType.FAST_UP, Set.of(TrainType.EXPRESS_MAIL, TrainType.SUPERFAST));
        p2.setPlatformId(102L);

        List<Platform> platforms = List.of(p1, p2);

        Train expressTrain = new Train("EXP-101", "Simulation Express", TrainType.EXPRESS_MAIL,
                "VR", "MMCT", 4, baseTime, baseTime.plusMinutes(10), "BVI", "BVI");
        expressTrain.setTrainId(10L);

        Map<Long, List<PlatformAllocation>> allocations = new HashMap<>();

        AllocationDecision decision = allocationEngine.allocatePlatform(expressTrain, platforms, allocations);

        assertTrue(decision.isAllocated(), "Express train must be successfully allocated");
        assertEquals(102L, decision.getSelectedPlatformId(), "Should select compatible Platform 2");
        assertNotNull(decision.getDetailedBreakdown());
    }

    @Test
    @DisplayName("When all platforms have interval conflicts, allocation engine should report unallocated/waiting")
    void testAllPlatformsConflictedReportsWaiting() {
        Platform p1 = new Platform(sampleStation, 1, LineType.SLOW_UP, Set.of(TrainType.LOCAL));
        p1.setPlatformId(101L);

        List<Platform> platforms = List.of(p1);

        // Platform 1 is already allocated from 11:00 to 11:15
        PlatformAllocation existingAlloc = new PlatformAllocation(
                99L, "LOC-OLD", "Existing Local", 101L, 1, 1L, "BVI",
                baseTime, baseTime.plusMinutes(15), "Scheduled", "Normal", 0
        );

        Map<Long, List<PlatformAllocation>> allocations = Map.of(101L, List.of(existingAlloc));

        // New train attempts to arrive at 11:05 to 11:12 (conflicts!)
        Train newTrain = new Train("LOC-NEW", "Incoming Local", TrainType.LOCAL,
                "VR", "CCG", 5, baseTime.plusMinutes(5), baseTime.plusMinutes(12), "BVI", "BVI");
        newTrain.setTrainId(11L);

        AllocationDecision decision = allocationEngine.allocatePlatform(newTrain, platforms, allocations);

        assertFalse(decision.isAllocated(), "Must not allocate when no platform is conflict-free");
        assertTrue(decision.getPrimaryReason().contains("conflict"));
        assertTrue(decision.getEstimatedDelayMinutes() > 0, "Should indicate holding delay");
    }
}
