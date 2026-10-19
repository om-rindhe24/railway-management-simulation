package com.railway.simulator.algorithm.dto;

import java.util.List;

/**
 * Result of allocating a platform to a train.
 */
public class AllocationDecision {
    private final Long trainId;
    private final String trainNumber;
    private final String trainName;
    private final Long selectedPlatformId;
    private final Integer selectedPlatformNumber;
    private final boolean allocated;
    private final String primaryReason;
    private final String detailedBreakdown;
    private final List<PlatformScoreResult> candidateScores;
    private final int estimatedDelayMinutes;

    public AllocationDecision(Long trainId, String trainNumber, String trainName,
                              Long selectedPlatformId, Integer selectedPlatformNumber,
                              boolean allocated, String primaryReason, String detailedBreakdown,
                              List<PlatformScoreResult> candidateScores, int estimatedDelayMinutes) {
        this.trainId = trainId;
        this.trainNumber = trainNumber;
        this.trainName = trainName;
        this.selectedPlatformId = selectedPlatformId;
        this.selectedPlatformNumber = selectedPlatformNumber;
        this.allocated = allocated;
        this.primaryReason = primaryReason;
        this.detailedBreakdown = detailedBreakdown;
        this.candidateScores = candidateScores;
        this.estimatedDelayMinutes = estimatedDelayMinutes;
    }

    public Long getTrainId() {
        return trainId;
    }

    public String getTrainNumber() {
        return trainNumber;
    }

    public String getTrainName() {
        return trainName;
    }

    public Long getSelectedPlatformId() {
        return selectedPlatformId;
    }

    public Integer getSelectedPlatformNumber() {
        return selectedPlatformNumber;
    }

    public boolean isAllocated() {
        return allocated;
    }

    public String getPrimaryReason() {
        return primaryReason;
    }

    public String getDetailedBreakdown() {
        return detailedBreakdown;
    }

    public List<PlatformScoreResult> getCandidateScores() {
        return candidateScores;
    }

    public int getEstimatedDelayMinutes() {
        return estimatedDelayMinutes;
    }
}
