package com.railway.simulator.algorithm.dto;

import java.time.LocalDateTime;

/**
 * Details of an interval overlap conflict detected between two trains on a platform.
 */
public class ConflictReport {
    private final Long platformId;
    private final Integer platformNumber;
    private final String stationCode;
    private final Long incomingTrainId;
    private final String incomingTrainNumber;
    private final Long existingTrainId;
    private final String existingTrainNumber;
    private final LocalDateTime overlapStart;
    private final LocalDateTime overlapEnd;
    private final long overlapDurationMinutes;
    private final String description;

    public ConflictReport(Long platformId, Integer platformNumber, String stationCode,
                          Long incomingTrainId, String incomingTrainNumber,
                          Long existingTrainId, String existingTrainNumber,
                          LocalDateTime overlapStart, LocalDateTime overlapEnd,
                          long overlapDurationMinutes, String description) {
        this.platformId = platformId;
        this.platformNumber = platformNumber;
        this.stationCode = stationCode;
        this.incomingTrainId = incomingTrainId;
        this.incomingTrainNumber = incomingTrainNumber;
        this.existingTrainId = existingTrainId;
        this.existingTrainNumber = existingTrainNumber;
        this.overlapStart = overlapStart;
        this.overlapEnd = overlapEnd;
        this.overlapDurationMinutes = overlapDurationMinutes;
        this.description = description;
    }

    public Long getPlatformId() {
        return platformId;
    }

    public Integer getPlatformNumber() {
        return platformNumber;
    }

    public String getStationCode() {
        return stationCode;
    }

    public Long getIncomingTrainId() {
        return incomingTrainId;
    }

    public String getIncomingTrainNumber() {
        return incomingTrainNumber;
    }

    public Long getExistingTrainId() {
        return existingTrainId;
    }

    public String getExistingTrainNumber() {
        return existingTrainNumber;
    }

    public LocalDateTime getOverlapStart() {
        return overlapStart;
    }

    public LocalDateTime getOverlapEnd() {
        return overlapEnd;
    }

    public long getOverlapDurationMinutes() {
        return overlapDurationMinutes;
    }

    public String getDescription() {
        return description;
    }
}
