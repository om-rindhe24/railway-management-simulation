package com.railway.simulator.algorithm;

import com.railway.simulator.algorithm.dto.AllocationDecision;
import com.railway.simulator.algorithm.dto.PlatformScoreResult;
import com.railway.simulator.domain.Platform;
import com.railway.simulator.domain.PlatformAllocation;
import com.railway.simulator.domain.Train;

import java.util.*;

/**
 * Greedy Platform Allocation Engine.
 *
 * For a given train arrival event, evaluates all available platforms at the station
 * through the explainable PlatformScoringEngine and makes the greedy optimal selection.
 *
 * Complexity:
 * - Evaluating P platforms at station with average M allocations: O(P * M)
 * - Sorting candidate scores: O(P log P)
 * - Total: O(P * M + P log P)
 */
public class PlatformAllocationEngine {

    private final PlatformScoringEngine scoringEngine;

    public PlatformAllocationEngine(PlatformScoringEngine scoringEngine) {
        this.scoringEngine = scoringEngine;
    }

    public PlatformAllocationEngine() {
        this(new PlatformScoringEngine());
    }

    /**
     * Allocates a platform for the given train among station platforms.
     */
    public AllocationDecision allocatePlatform(Train train, List<Platform> stationPlatforms,
                                               Map<Long, List<PlatformAllocation>> allocationsByPlatform) {
        if (stationPlatforms == null || stationPlatforms.isEmpty()) {
            return new AllocationDecision(
                    train.getTrainId(), train.getTrainNumber(), train.getTrainName(),
                    null, null, false,
                    "No platforms available at station " + train.getTargetStationCode(),
                    "Station platform registry empty.", Collections.emptyList(), 15
            );
        }

        List<PlatformScoreResult> candidateScores = new ArrayList<>();
        PlatformScoreResult bestResult = null;
        Platform bestPlatform = null;

        for (Platform platform : stationPlatforms) {
            List<PlatformAllocation> existing = allocationsByPlatform.getOrDefault(platform.getPlatformId(), Collections.emptyList());
            PlatformScoreResult scoreResult = scoringEngine.evaluatePlatform(train, platform, existing);
            candidateScores.add(scoreResult);

            if (scoreResult.isFeasible()) {
                if (bestResult == null || scoreResult.getTotalScore() > bestResult.getTotalScore()) {
                    bestResult = scoreResult;
                    bestPlatform = platform;
                }
            }
        }

        // Sort candidate results by score descending for visual transparency in UI
        candidateScores.sort((a, b) -> Double.compare(b.getTotalScore(), a.getTotalScore()));

        if (bestPlatform != null && bestResult != null) {
            StringBuilder breakdown = new StringBuilder();
            breakdown.append(String.format("Platform %d selected (Score: %.1f). Breakdown: ",
                    bestPlatform.getPlatformNumber(), bestResult.getTotalScore()));

            bestResult.getFactorScores().forEach((factor, val) ->
                    breakdown.append(String.format("[%s: %+.1f] ", factor, val)));

            return new AllocationDecision(
                    train.getTrainId(), train.getTrainNumber(), train.getTrainName(),
                    bestPlatform.getPlatformId(), bestPlatform.getPlatformNumber(),
                    true,
                    "Feasible conflict-free assignment with highest suitability score",
                    breakdown.toString().trim(),
                    candidateScores,
                    0
            );
        } else {
            // Infeasible: All platforms either closed, incompatible, or occupied
            String primaryReason = candidateScores.isEmpty() ? "No compatible platforms" :
                    candidateScores.get(0).getFeasibilityReason();

            return new AllocationDecision(
                    train.getTrainId(), train.getTrainNumber(), train.getTrainName(),
                    null, null, false,
                    "No conflict-free platform currently available: " + primaryReason,
                    "Train placed in WAITING queue. Dynamic reallocation will evaluate when platform frees.",
                    candidateScores,
                    10 // Estimated initial holding delay
            );
        }
    }
}
