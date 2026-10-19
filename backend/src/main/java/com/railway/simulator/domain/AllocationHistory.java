package com.railway.simulator.domain;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * Immutable audit log of all platform allocation, reallocation, and conflict resolution events.
 */
@Entity
@Table(name = "allocation_history", indexes = {
    @Index(name = "idx_history_train", columnList = "train_number"),
    @Index(name = "idx_history_timestamp", columnList = "timestamp"),
    @Index(name = "idx_history_station", columnList = "station_code")
})
public class AllocationHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "history_id")
    private Long historyId;

    @Column(name = "train_number", nullable = false, length = 20)
    private String trainNumber;

    @Column(name = "train_name", nullable = false, length = 100)
    private String trainName;

    @Column(name = "station_code", nullable = false, length = 10)
    private String stationCode;

    @Column(name = "from_platform_number")
    private Integer fromPlatformNumber;

    @Column(name = "to_platform_number", nullable = true)
    private Integer toPlatformNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "change_type", nullable = false, length = 30)
    private ChangeType changeType = ChangeType.INITIAL_ALLOCATION;

    @Column(name = "timestamp", nullable = false)
    private LocalDateTime timestamp = LocalDateTime.now();

    @Column(name = "reason", nullable = false, columnDefinition = "TEXT")
    private String reason;

    @Column(name = "delay_impact_minutes", nullable = false)
    private Integer delayImpactMinutes = 0;

    @Column(name = "conflicts_avoided", nullable = false)
    private Integer conflictsAvoided = 0;

    public AllocationHistory() {}

    public AllocationHistory(String trainNumber, String trainName, String stationCode,
                             Integer fromPlatformNumber, Integer toPlatformNumber,
                             ChangeType changeType, String reason,
                             Integer delayImpactMinutes, Integer conflictsAvoided) {
        this.trainNumber = trainNumber;
        this.trainName = trainName;
        this.stationCode = stationCode;
        this.fromPlatformNumber = fromPlatformNumber;
        this.toPlatformNumber = toPlatformNumber;
        this.changeType = changeType;
        this.reason = reason;
        this.delayImpactMinutes = delayImpactMinutes;
        this.conflictsAvoided = conflictsAvoided;
        this.timestamp = LocalDateTime.now();
    }

    public Long getHistoryId() {
        return historyId;
    }

    public void setHistoryId(Long historyId) {
        this.historyId = historyId;
    }

    public String getTrainNumber() {
        return trainNumber;
    }

    public void setTrainNumber(String trainNumber) {
        this.trainNumber = trainNumber;
    }

    public String getTrainName() {
        return trainName;
    }

    public void setTrainName(String trainName) {
        this.trainName = trainName;
    }

    public String getStationCode() {
        return stationCode;
    }

    public void setStationCode(String stationCode) {
        this.stationCode = stationCode;
    }

    public Integer getFromPlatformNumber() {
        return fromPlatformNumber;
    }

    public void setFromPlatformNumber(Integer fromPlatformNumber) {
        this.fromPlatformNumber = fromPlatformNumber;
    }

    public Integer getToPlatformNumber() {
        return toPlatformNumber;
    }

    public void setToPlatformNumber(Integer toPlatformNumber) {
        this.toPlatformNumber = toPlatformNumber;
    }

    public ChangeType getChangeType() {
        return changeType;
    }

    public void setChangeType(ChangeType changeType) {
        this.changeType = changeType;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(LocalDateTime timestamp) {
        this.timestamp = timestamp;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public Integer getDelayImpactMinutes() {
        return delayImpactMinutes;
    }

    public void setDelayImpactMinutes(Integer delayImpactMinutes) {
        this.delayImpactMinutes = delayImpactMinutes;
    }

    public Integer getConflictsAvoided() {
        return conflictsAvoided;
    }

    public void setConflictsAvoided(Integer conflictsAvoided) {
        this.conflictsAvoided = conflictsAvoided;
    }
}
