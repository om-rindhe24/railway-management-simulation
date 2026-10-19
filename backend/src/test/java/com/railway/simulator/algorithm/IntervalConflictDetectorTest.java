package com.railway.simulator.algorithm;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class IntervalConflictDetectorTest {

    private IntervalConflictDetector detectorWithBuffer;
    private IntervalConflictDetector detectorZeroBuffer;
    private LocalDateTime baseTime;

    @BeforeEach
    void setUp() {
        detectorWithBuffer = new IntervalConflictDetector(3); // 3 min clearance buffer
        detectorZeroBuffer = new IntervalConflictDetector(0);
        baseTime = LocalDateTime.of(2026, 9, 18, 8, 0);
    }

    @Test
    @DisplayName("Non-overlapping intervals with clear gap should not conflict")
    void testNonOverlappingIntervals() {
        // Train A: 08:00 to 08:10
        LocalDateTime arrA = baseTime;
        LocalDateTime depA = baseTime.plusMinutes(10);

        // Train B: 08:20 to 08:30 (10 min gap > 3 min buffer)
        LocalDateTime arrB = baseTime.plusMinutes(20);
        LocalDateTime depB = baseTime.plusMinutes(30);

        assertFalse(detectorWithBuffer.hasConflict(arrA, depA, arrB, depB));
        assertEquals(0, detectorWithBuffer.calculateOverlapMinutes(arrA, depA, arrB, depB));
    }

    @Test
    @DisplayName("Intervals overlapping directly should detect conflict and calculate duration")
    void testDirectOverlappingIntervals() {
        // Train A: 08:00 to 08:15
        LocalDateTime arrA = baseTime;
        LocalDateTime depA = baseTime.plusMinutes(15);

        // Train B: 08:10 to 08:25 (5 min direct overlap + buffer)
        LocalDateTime arrB = baseTime.plusMinutes(10);
        LocalDateTime depB = baseTime.plusMinutes(25);

        assertTrue(detectorWithBuffer.hasConflict(arrA, depA, arrB, depB));
        long overlapMinutes = detectorWithBuffer.calculateOverlapMinutes(arrA, depA, arrB, depB);
        assertTrue(overlapMinutes > 0, "Overlap minutes should be greater than 0");
    }

    @Test
    @DisplayName("Intervals touching within safety clearance buffer should conflict when buffer is enabled")
    void testSafetyBufferSeparationConstraint() {
        // Train A: 08:00 to 08:10
        LocalDateTime arrA = baseTime;
        LocalDateTime depA = baseTime.plusMinutes(10);

        // Train B: 08:12 to 08:20 (2 min gap between A dep and B arr)
        LocalDateTime arrB = baseTime.plusMinutes(12);
        LocalDateTime depB = baseTime.plusMinutes(20);

        // With 0 buffer: no conflict (10 <= 12)
        assertFalse(detectorZeroBuffer.hasConflict(arrA, depA, arrB, depB));

        // With 3-min buffer: 2 min gap is < 3 min safety margin -> triggers conflict
        assertTrue(detectorWithBuffer.hasConflict(arrA, depA, arrB, depB),
                "Should conflict because 2-minute gap is within 3-minute clearance buffer");
    }

    @Test
    @DisplayName("Shorter interval completely enclosed within longer interval should conflict")
    void testContainedInterval() {
        // Outer train: 08:00 to 08:40
        LocalDateTime arrOuter = baseTime;
        LocalDateTime depOuter = baseTime.plusMinutes(40);

        // Inner train: 08:15 to 08:25
        LocalDateTime arrInner = baseTime.plusMinutes(15);
        LocalDateTime depInner = baseTime.plusMinutes(25);

        assertTrue(detectorWithBuffer.hasConflict(arrOuter, depOuter, arrInner, depInner));
    }

    @Test
    @DisplayName("Single buffer check: gap 2 min = conflict, gap 4 min = no conflict")
    void testSingleBufferGap2MinAnd4Min() {
        // Train 1: 08:00 to 08:10
        LocalDateTime arr1 = baseTime;
        LocalDateTime dep1 = baseTime.plusMinutes(10);

        // Gap 2 min: Train 2 arrives at 08:12 (dep1 + 2 min)
        LocalDateTime arr2Gap2 = baseTime.plusMinutes(12);
        LocalDateTime dep2Gap2 = baseTime.plusMinutes(20);
        assertTrue(detectorWithBuffer.hasConflict(arr1, dep1, arr2Gap2, dep2Gap2),
                "Gap of 2 minutes should conflict with 3 min buffer");

        // Gap 4 min: Train 2 arrives at 08:14 (dep1 + 4 min)
        LocalDateTime arr2Gap4 = baseTime.plusMinutes(14);
        LocalDateTime dep2Gap4 = baseTime.plusMinutes(20);
        assertFalse(detectorWithBuffer.hasConflict(arr1, dep1, arr2Gap4, dep2Gap4),
                "Gap of 4 minutes should not conflict with 3 min buffer");
    }
}
