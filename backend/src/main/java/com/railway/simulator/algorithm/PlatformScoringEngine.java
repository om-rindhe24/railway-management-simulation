package com.railway.simulator.algorithm;

import com.railway.simulator.algorithm.dto.ConflictReport;
import com.railway.simulator.algorithm.dto.PlatformScoreResult;
import com.railway.simulator.domain.LineType;
import com.railway.simulator.domain.Platform;
import com.railway.simulator.domain.PlatformAllocation;
import com.railway.simulator.domain.Train;
import com.railway.simulator.domain.TrainType;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Explainable Multi-Factor Platform Scoring Engine.
 *
 * Factors:
 * 1. Hard Feasibility Gates (Mandatory):
 *    - Platform open (not closed)
 *    - Train type compatibility supported
 *    - Time interval conflict-free
 * 2. Line Type Match (Corridor efficiency)
 * 3. Platform Idle Buffer Spacing (prevents bottleneck cascading)
 * 4. Passenger Disruption Minimization (preserves scheduled platform proximity)
 * 5. Train Priority Weighting
 */
public class PlatformScoringEngine {

    private final IntervalConflictDetector conflictDetector;

    public PlatformScoringEngine(IntervalConflictDetector conflictDetector) {
        this.conflictDetector = conflictDetector;
    }

    public PlatformScoringEngine() {
        this(new IntervalConflictDetector(3));
    }

    /**
     * Evaluates and scores a candidate platform for a specific train and arrival/departure interval.
     */
    public PlatformScoreResult evaluatePlatform(Train train, Platform candidatePlatform,
                                                List<PlatformAllocation> existingPlatformAllocations) {
        Long platformId = candidatePlatform.getPlatformId();
        Integer platformNumber = candidatePlatform.getPlatformNumber();
        Map<String, Double> factorScores = new LinkedHashMap<>();

        // 1. Hard Gate: Platform Closure
        if (Boolean.TRUE.equals(candidatePlatform.getIsClosed())) {
            return new PlatformScoreResult(
                    platformId, platformNumber, false, 0.0,
                    "Infeasible: Platform " + platformNumber + " is currently CLOSED (" +
                            (candidatePlatform.getClosureReason() != null ? candidatePlatform.getClosureReason() : "Maintenance") + ")",
                    factorScores,
                    "Platform " + platformNumber + " rejected: Platform is closed"
            );
        }

        // 2. Hard Gate: Train Type Compatibility
        if (candidatePlatform.getSupportedTrainTypes() != null &&
            !candidatePlatform.getSupportedTrainTypes().isEmpty() &&
            !candidatePlatform.getSupportedTrainTypes().contains(train.getTrainType())) {
            return new PlatformScoreResult(
                    platformId, platformNumber, false, 0.0,
                    "Infeasible: Platform " + platformNumber + " does not support train category: " + train.getTrainType().getDisplayName(),
                    factorScores,
                    "Platform " + platformNumber + " rejected: Train type incompatible"
            );
        }

        // 2b. Hard Gate: Direction Compatibility (UP trains strictly use UP platforms, DOWN trains strictly use DOWN platforms)
        String trainDir = train.getDirection();
        if (trainDir == null || trainDir.isBlank()) {
            trainDir = "UP";
            if (train.getDestinationStation() != null) {
                String dest = train.getDestinationStation().toUpperCase();
                if (dest.equals("VR") || dest.equals("DRD") || dest.equals("GNC") || dest.equals("NDLS")
                        || dest.equals("NZM") || dest.equals("ADI") || dest.equals("BHUJ") || dest.equals("ST")) {
                    trainDir = "DOWN";
                }
            }
        }
        trainDir = trainDir.toUpperCase();

        LineType pLine = candidatePlatform.getLineType();
        if (pLine == null && candidatePlatform.getStationCode() != null && candidatePlatform.getPlatformNumber() != null) {
            pLine = com.railway.simulator.config.PlatformLineConfig.getPlatformLineType(candidatePlatform.getStationCode(), candidatePlatform.getPlatformNumber());
        }
        String platDir = (pLine != null)
                ? com.railway.simulator.config.PlatformLineConfig.getDirection(pLine)
                : candidatePlatform.getDirection();

        if (platDir != null && !"COMMON".equalsIgnoreCase(platDir) && !platDir.equalsIgnoreCase(trainDir)) {
            return new PlatformScoreResult(
                    platformId, platformNumber, false, 0.0,
                    "Infeasible: Platform " + platformNumber + " direction (" + platDir + ") incompatible with train direction (" + trainDir + ")",
                    factorScores,
                    "Platform " + platformNumber + " rejected: Direction mismatch (" + platDir + " vs " + trainDir + ")"
            );
        }

        // 3. Hard Gate: Interval Overlap Conflict Check
        Optional<ConflictReport> conflict = conflictDetector.detectPlatformConflict(
                platformId, platformNumber, candidatePlatform.getStationCode(),
                train.getTrainId(), train.getTrainNumber(),
                train.getActualArrival(), train.getActualDeparture(),
                existingPlatformAllocations
        );

        if (conflict.isPresent()) {
            return new PlatformScoreResult(
                    platformId, platformNumber, false, 0.0,
                    "Infeasible: " + conflict.get().getDescription(),
                    factorScores,
                    "Platform " + platformNumber + " rejected: Overlap conflict with Train " + conflict.get().getExistingTrainNumber()
            );
        }

        // --- Platform is Feasible: Compute Explainable Positive & Penalty Factors ---
        double baseScore = 100.0;
        factorScores.put("Base Feasibility", baseScore);

        // Factor A: Line Type Match
        double lineMatchScore = computeLineMatchScore(train, candidatePlatform);
        factorScores.put("Line Match", lineMatchScore);

        // Factor B: Platform Idle Spacing
        double idleSpacingScore = computeIdleSpacingScore(train, existingPlatformAllocations);
        factorScores.put("Idle Spacing Buffer", idleSpacingScore);

        // Factor C: Passenger Disruption Minimization
        double disruptionScore = computeDisruptionScore(train, candidatePlatform);
        factorScores.put("Disruption Minimization", disruptionScore);

        // Factor D: Priority Preference
        int priorityTier = PriorityPolicy.getPriorityTier(train.getTrainType(), train.getSimulationPriority());
        double priorityBonus = (7 - priorityTier) * 3.0; // Higher tier gets slightly higher preference for dedicated tracks
        factorScores.put("Priority Weight", priorityBonus);

        double totalScore = baseScore + lineMatchScore + idleSpacingScore + disruptionScore + priorityBonus;

        String summary = String.format(
                "Platform %d selected with Score %.1f (LineMatch: +%.0f, IdleSpacing: +%.1f, Disruption: %+.1f)",
                platformNumber, totalScore, lineMatchScore, idleSpacingScore, disruptionScore
        );

        return new PlatformScoreResult(
                platformId, platformNumber, true, totalScore,
                "Feasible: Conflict-free and compatible", factorScores, summary
        );
    }

    private double computeLineMatchScore(Train train, Platform platform) {
        LineType line = platform.getLineType();
        TrainType type = train.getTrainType();

        boolean isFastLine = (line == LineType.FAST_UP || line == LineType.FAST_DOWN);
        boolean isSlowLine = (line == LineType.SLOW_UP || line == LineType.SLOW_DOWN);

        boolean isFastTrain = (train.getTrainCategory() == com.railway.simulator.domain.TrainCategory.FAST_LOCAL
                || type == TrainType.SPECIAL
                || type == TrainType.PREMIUM
                || type == TrainType.SUPERFAST
                || type == TrainType.EXPRESS_MAIL);

        if (isFastLine && isFastTrain) {
            return 50.0; // Optimal match for fast corridor
        }

        if (isSlowLine && !isFastTrain) {
            return 50.0; // Optimal match for local corridor
        }

        if (line == LineType.COMMON) {
            return 30.0; // Universal platform fallback
        }

        return 15.0; // Functional fallback: opposite line type in same direction
    }

    private double computeIdleSpacingScore(Train train, List<PlatformAllocation> existingAllocations) {
        if (existingAllocations == null || existingAllocations.isEmpty()) {
            return 25.0; // Completely empty platform offers maximum idle buffer
        }

        // Find the allocation directly preceding this train
        long minIdleGapMinutes = Long.MAX_VALUE;
        LocalDateTime arr = train.getActualArrival();

        for (PlatformAllocation alloc : existingAllocations) {
            if (alloc.getEndTime().isBefore(arr)) {
                long gap = Duration.between(alloc.getEndTime(), arr).toMinutes();
                if (gap < minIdleGapMinutes) {
                    minIdleGapMinutes = gap;
                }
            }
        }

        if (minIdleGapMinutes == Long.MAX_VALUE) {
            return 25.0;
        }

        // 15+ minutes idle is optimal (+25), scaled linearly down to buffer
        double ratio = Math.min(1.0, (double) minIdleGapMinutes / 20.0);
        return ratio * 25.0;
    }

    private double computeDisruptionScore(Train train, Platform candidatePlatform) {
        Integer scheduledPlatform = train.getAssignedPlatformNumber();
        if (scheduledPlatform == null) {
            return 10.0;
        }

        if (scheduledPlatform.equals(candidatePlatform.getPlatformNumber())) {
            return 40.0; // Exactly on published timetable platform: 0 passenger disruption!
        }

        // Penalty proportional to walking distance between platform numbers
        int diff = Math.abs(candidatePlatform.getPlatformNumber() - scheduledPlatform);
        return -8.0 * diff;
    }
}
