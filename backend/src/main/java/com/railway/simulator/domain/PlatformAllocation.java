package com.railway.simulator.domain;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * Platform allocation record.
 * Stores interval assignment [startTime, endTime], assigned platform,
 * selection rationale, and scoring breakdown.
 */
@Entity
@Table(name = "platform_allocations", indexes = {
    @Index(name = "idx_allocation_platform_interval", columnList = "platform_id, start_time, end_time"),
    @Index(name = "idx_allocation_train", columnList = "train_id"),
    @Index(name = "idx_allocation_status", columnList = "status")
})
public class PlatformAllocation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "allocation_id")
    private Long allocationId;

    @Column(name = "train_id", nullable = false)
    private Long trainId;

    @Column(name = "train_number", nullable = false, length = 20)
    private String trainNumber;

    @Column(name = "train_name", nullable = false, length = 100)
    private String trainName;

    @Column(name = "platform_id", nullable = false)
    private Long platformId;

    @Column(name = "platform_number", nullable = false)
    private Integer platformNumber;

    @Column(name = "station_id", nullable = false)
    private Long stationId;

    @Column(name = "station_code", nullable = false, length = 10)
    private String stationCode;

    @Column(name = "start_time", nullable = false)
    private LocalDateTime startTime;

    @Column(name = "end_time", nullable = false)
    private LocalDateTime endTime;

    @Column(name = "allocated_at", nullable = false)
    private LocalDateTime allocatedAt = LocalDateTime.now();

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private AllocationStatus status = AllocationStatus.ACTIVE;

    @Column(name = "selection_reason", columnDefinition = "TEXT")
    private String selectionReason;

    @Column(name = "scoring_breakdown", columnDefinition = "TEXT")
    private String scoringBreakdown;

    @Column(name = "delay_minutes", nullable = false)
    private Integer delayMinutes = 0;

    public PlatformAllocation() {}

    public PlatformAllocation(Long trainId, String trainNumber, String trainName,
                              Long platformId, Integer platformNumber,
                              Long stationId, String stationCode,
                              LocalDateTime startTime, LocalDateTime endTime,
                              String selectionReason, String scoringBreakdown,
                              Integer delayMinutes) {
        this.trainId = trainId;
        this.trainNumber = trainNumber;
        this.trainName = trainName;
        this.platformId = platformId;
        this.platformNumber = platformNumber;
        this.stationId = stationId;
        this.stationCode = stationCode;
        this.startTime = startTime;
        this.endTime = endTime;
        this.selectionReason = selectionReason;
        this.scoringBreakdown = scoringBreakdown;
        this.delayMinutes = delayMinutes;
        this.status = AllocationStatus.ACTIVE;
        this.allocatedAt = LocalDateTime.now();
    }

    public Long getAllocationId() {
        return allocationId;
    }

    public void setAllocationId(Long allocationId) {
        this.allocationId = allocationId;
    }

    public Long getTrainId() {
        return trainId;
    }

    public void setTrainId(Long trainId) {
        this.trainId = trainId;
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

    public Long getPlatformId() {
        return platformId;
    }

    public void setPlatformId(Long platformId) {
        this.platformId = platformId;
    }

    public Integer getPlatformNumber() {
        return platformNumber;
    }

    public void setPlatformNumber(Integer platformNumber) {
        this.platformNumber = platformNumber;
    }

    public Long getStationId() {
        return stationId;
    }

    public void setStationId(Long stationId) {
        this.stationId = stationId;
    }

    public String getStationCode() {
        return stationCode;
    }

    public void setStationCode(String stationCode) {
        this.stationCode = stationCode;
    }

    public LocalDateTime getStartTime() {
        return startTime;
    }

    public void setStartTime(LocalDateTime startTime) {
        this.startTime = startTime;
    }

    public LocalDateTime getEndTime() {
        return endTime;
    }

    public void setEndTime(LocalDateTime endTime) {
        this.endTime = endTime;
    }

    public LocalDateTime getAllocatedAt() {
        return allocatedAt;
    }

    public void setAllocatedAt(LocalDateTime allocatedAt) {
        this.allocatedAt = allocatedAt;
    }

    public AllocationStatus getStatus() {
        return status;
    }

    public void setStatus(AllocationStatus status) {
        this.status = status;
    }

    public String getSelectionReason() {
        return selectionReason;
    }

    public void setSelectionReason(String selectionReason) {
        this.selectionReason = selectionReason;
    }

    public String getScoringBreakdown() {
        return scoringBreakdown;
    }

    public void setScoringBreakdown(String scoringBreakdown) {
        this.scoringBreakdown = scoringBreakdown;
    }

    public Integer getDelayMinutes() {
        return delayMinutes;
    }

    public void setDelayMinutes(Integer delayMinutes) {
        this.delayMinutes = delayMinutes;
    }
}
