package com.railway.simulator.controller.dto;

public class WhatIfTrainDiff {
    private Long trainId;
    private String trainNumber;
    private String trainName;
    private Integer beforePlatformNumber;
    private Integer afterPlatformNumber;
    private int beforeDelayMinutes;
    private int afterDelayMinutes;
    private String beforeStatus;
    private String afterStatus;
    private String decisionReason;
    private boolean isHeld;
    private boolean isReallocated;

    public WhatIfTrainDiff() {}

    public WhatIfTrainDiff(Long trainId, String trainNumber, String trainName,
                           Integer beforePlatformNumber, Integer afterPlatformNumber,
                           int beforeDelayMinutes, int afterDelayMinutes,
                           String beforeStatus, String afterStatus,
                           String decisionReason, boolean isHeld, boolean isReallocated) {
        this.trainId = trainId;
        this.trainNumber = trainNumber;
        this.trainName = trainName;
        this.beforePlatformNumber = beforePlatformNumber;
        this.afterPlatformNumber = afterPlatformNumber;
        this.beforeDelayMinutes = beforeDelayMinutes;
        this.afterDelayMinutes = afterDelayMinutes;
        this.beforeStatus = beforeStatus;
        this.afterStatus = afterStatus;
        this.decisionReason = decisionReason;
        this.isHeld = isHeld;
        this.isReallocated = isReallocated;
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

    public Integer getBeforePlatformNumber() {
        return beforePlatformNumber;
    }

    public void setBeforePlatformNumber(Integer beforePlatformNumber) {
        this.beforePlatformNumber = beforePlatformNumber;
    }

    public Integer getAfterPlatformNumber() {
        return afterPlatformNumber;
    }

    public void setAfterPlatformNumber(Integer afterPlatformNumber) {
        this.afterPlatformNumber = afterPlatformNumber;
    }

    public int getBeforeDelayMinutes() {
        return beforeDelayMinutes;
    }

    public void setBeforeDelayMinutes(int beforeDelayMinutes) {
        this.beforeDelayMinutes = beforeDelayMinutes;
    }

    public int getAfterDelayMinutes() {
        return afterDelayMinutes;
    }

    public void setAfterDelayMinutes(int afterDelayMinutes) {
        this.afterDelayMinutes = afterDelayMinutes;
    }

    public String getBeforeStatus() {
        return beforeStatus;
    }

    public void setBeforeStatus(String beforeStatus) {
        this.beforeStatus = beforeStatus;
    }

    public String getAfterStatus() {
        return afterStatus;
    }

    public void setAfterStatus(String afterStatus) {
        this.afterStatus = afterStatus;
    }

    public String getDecisionReason() {
        return decisionReason;
    }

    public void setDecisionReason(String decisionReason) {
        this.decisionReason = decisionReason;
    }

    public boolean isHeld() {
        return isHeld;
    }

    public void setHeld(boolean held) {
        isHeld = held;
    }

    public boolean isReallocated() {
        return isReallocated;
    }

    public void setReallocated(boolean reallocated) {
        isReallocated = reallocated;
    }
}

