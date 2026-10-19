package com.railway.simulator.algorithm;

import com.railway.simulator.algorithm.dto.PlatformScoreResult;
import com.railway.simulator.domain.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class PlatformScoringEngineTest {

    private PlatformScoringEngine scoringEngine;
    private LocalDateTime baseTime;
    private Station sampleStation;

    @BeforeEach
    void setUp() {
        scoringEngine = new PlatformScoringEngine(new IntervalConflictDetector(3));
        baseTime = LocalDateTime.of(2026, 9, 18, 9, 0);
        sampleStation = new Station("BVI", "Borivali", 6, true, true, true, true, true, 8);
        sampleStation.setStationId(1L);
    }

    @Test
    @DisplayName("Closed platform must be marked infeasible and rejected")
    void testClosedPlatformRejection() {
        Platform closedPlatform = new Platform(sampleStation, 3, LineType.COMMON, Set.of(TrainType.values()));
        closedPlatform.setPlatformId(103L);
        closedPlatform.setIsClosed(true);
        closedPlatform.setClosureReason("Track maintenance simulation");

        Train train = new Train("LOC-101", "Simulation Local", TrainType.LOCAL,
                "VR", "CCG", 5, baseTime, baseTime.plusMinutes(5), "BVI", "BVI");
        train.setTrainId(1L);

        PlatformScoreResult result = scoringEngine.evaluatePlatform(train, closedPlatform, Collections.emptyList());

        assertFalse(result.isFeasible(), "Closed platform must be infeasible");
        assertTrue(result.getFeasibilityReason().contains("CLOSED"));
    }

    @Test
    @DisplayName("Incompatible train type platform must be marked infeasible")
    void testIncompatibleTrainTypeRejection() {
        // Platform only supports Suburban Locals
        Platform localOnlyPlatform = new Platform(sampleStation, 1, LineType.SLOW_UP, Set.of(TrainType.LOCAL));
        localOnlyPlatform.setPlatformId(101L);

        // Train is a Heavy Freight service
        Train freightTrain = new Train("FRT-501", "Simulation Freight", TrainType.FREIGHT,
                "BSR", "JNPT", 6, baseTime, baseTime.plusMinutes(15), "BVI", "BVI");
        freightTrain.setTrainId(2L);

        PlatformScoreResult result = scoringEngine.evaluatePlatform(freightTrain, localOnlyPlatform, Collections.emptyList());

        assertFalse(result.isFeasible(), "Platform supporting only locals must reject freight");
        assertTrue(result.getFeasibilityReason().contains("does not support"));
    }

    @Test
    @DisplayName("Feasible platform should produce explainable multi-factor score breakdown")
    void testFeasiblePlatformScoringBreakdown() {
        Platform platform = new Platform(sampleStation, 4, LineType.FAST_UP,
                Set.of(TrainType.SUPERFAST, TrainType.EXPRESS_MAIL, TrainType.PREMIUM));
        platform.setPlatformId(104L);

        Train superfastTrain = new Train("SF-12952", "Simulation Superfast", TrainType.SUPERFAST,
                "VR", "MMCT", 3, baseTime, baseTime.plusMinutes(8), "BVI", "BVI");
        superfastTrain.setTrainId(3L);
        superfastTrain.setAssignedPlatformNumber(4); // Timetable preference

        PlatformScoreResult result = scoringEngine.evaluatePlatform(superfastTrain, platform, Collections.emptyList());

        assertTrue(result.isFeasible(), "Platform should be feasible");
        assertTrue(result.getTotalScore() > 100.0, "Total score should be > base feasibility score");
        assertNotNull(result.getFactorScores().get("Line Match"), "Should contain Line Match factor");
        assertNotNull(result.getFactorScores().get("Disruption Minimization"), "Should contain Disruption factor");
        assertNotNull(result.getSummaryExplanation(), "Should provide human-readable summary explanation");
    }
}
