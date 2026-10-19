package com.railway.simulator.algorithm.dto;

import java.util.Map;

/**
 * Detailed scoring breakdown for an evaluated candidate platform.
 */
public class PlatformScoreResult {
    private final Long platformId;
    private final Integer platformNumber;
    private final boolean feasible;
    private final double totalScore;
    private final String feasibilityReason;
    private final Map<String, Double> factorScores;
    private final String summaryExplanation;

    public PlatformScoreResult(Long platformId, Integer platformNumber, boolean feasible,
                               double totalScore, String feasibilityReason,
                               Map<String, Double> factorScores, String summaryExplanation) {
        this.platformId = platformId;
        this.platformNumber = platformNumber;
        this.feasible = feasible;
        this.totalScore = totalScore;
        this.feasibilityReason = feasibilityReason;
        this.factorScores = factorScores;
        this.summaryExplanation = summaryExplanation;
    }

    public Long getPlatformId() {
        return platformId;
    }

    public Integer getPlatformNumber() {
        return platformNumber;
    }

    public boolean isFeasible() {
        return feasible;
    }

    public double getTotalScore() {
        return totalScore;
    }

    public String getFeasibilityReason() {
        return feasibilityReason;
    }

    public Map<String, Double> getFactorScores() {
        return factorScores;
    }

    public String getSummaryExplanation() {
        return summaryExplanation;
    }
}
