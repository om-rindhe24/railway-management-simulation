package com.railway.simulator.controller.dto;

import com.railway.simulator.algorithm.dto.ReallocationResult;
import java.util.ArrayList;
import java.util.List;

public class WhatIfPreviewResult {
    private boolean isolatedCopy = true;
    private int totalAffectedTrains;
    private int reallocatedCount;
    private int conflictsResolved;
    private int additionalDelayMinutes;
    private int heldCount;
    private String summaryMessage;
    private ReallocationResult reallocationResult;
    private List<WhatIfTrainDiff> diffs = new ArrayList<>();

    public WhatIfPreviewResult() {}

    public boolean isIsolatedCopy() {
        return isolatedCopy;
    }

    public void setIsolatedCopy(boolean isolatedCopy) {
        this.isolatedCopy = isolatedCopy;
    }

    public int getTotalAffectedTrains() {
        return totalAffectedTrains;
    }

    public void setTotalAffectedTrains(int totalAffectedTrains) {
        this.totalAffectedTrains = totalAffectedTrains;
    }

    public int getReallocatedCount() {
        return reallocatedCount;
    }

    public void setReallocatedCount(int reallocatedCount) {
        this.reallocatedCount = reallocatedCount;
    }

    public int getConflictsResolved() {
        return conflictsResolved;
    }

    public void setConflictsResolved(int conflictsResolved) {
        this.conflictsResolved = conflictsResolved;
    }

    public int getAdditionalDelayMinutes() {
        return additionalDelayMinutes;
    }

    public void setAdditionalDelayMinutes(int additionalDelayMinutes) {
        this.additionalDelayMinutes = additionalDelayMinutes;
    }

    public int getHeldCount() {
        return heldCount;
    }

    public void setHeldCount(int heldCount) {
        this.heldCount = heldCount;
    }

    public String getSummaryMessage() {
        return summaryMessage;
    }

    public void setSummaryMessage(String summaryMessage) {
        this.summaryMessage = summaryMessage;
    }

    public ReallocationResult getReallocationResult() {
        return reallocationResult;
    }

    public void setReallocationResult(ReallocationResult reallocationResult) {
        this.reallocationResult = reallocationResult;
    }

    public List<WhatIfTrainDiff> getDiffs() {
        return diffs;
    }

    public void setDiffs(List<WhatIfTrainDiff> diffs) {
        this.diffs = diffs;
    }
}

