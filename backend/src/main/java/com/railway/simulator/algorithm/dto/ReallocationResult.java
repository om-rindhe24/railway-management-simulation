package com.railway.simulator.algorithm.dto;

import java.util.List;

/**
 * Result of a dynamic reallocation cascade triggered by delays, closures, or preemption.
 */
public class ReallocationResult {
    private final int totalAffectedTrains;
    private final int reallocatedCount;
    private final int waitingCount;
    private final int additionalDelayMinutes;
    private final int conflictsResolved;
    private final List<AllocationDecision> reallocationDecisions;
    private final String summaryMessage;

    public ReallocationResult(int totalAffectedTrains, int reallocatedCount, int waitingCount,
                              int additionalDelayMinutes, int conflictsResolved,
                              List<AllocationDecision> reallocationDecisions, String summaryMessage) {
        this.totalAffectedTrains = totalAffectedTrains;
        this.reallocatedCount = reallocatedCount;
        this.waitingCount = waitingCount;
        this.additionalDelayMinutes = additionalDelayMinutes;
        this.conflictsResolved = conflictsResolved;
        this.reallocationDecisions = reallocationDecisions;
        this.summaryMessage = summaryMessage;
    }

    public int getTotalAffectedTrains() {
        return totalAffectedTrains;
    }

    public int getReallocatedCount() {
        return reallocatedCount;
    }

    public int getWaitingCount() {
        return waitingCount;
    }

    public int getAdditionalDelayMinutes() {
        return additionalDelayMinutes;
    }

    public int getConflictsResolved() {
        return conflictsResolved;
    }

    public List<AllocationDecision> getReallocationDecisions() {
        return reallocationDecisions;
    }

    public String getSummaryMessage() {
        return summaryMessage;
    }
}
