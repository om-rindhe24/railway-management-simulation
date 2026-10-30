package com.railway.simulator.controller.dto;

import com.railway.simulator.domain.SignalAspect;
import com.railway.simulator.domain.TrainCategory;
import com.railway.simulator.domain.TrainStatus;
import com.railway.simulator.domain.TrainType;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class TrainSnapshotDto {
    private Long trainId;
    private String trainNumber;
    private String trainName;
    private TrainType trainType;
    private TrainCategory trainCategory;
    private TrainStatus status;
    private String sourceStation;
    private String destinationStation;
    private String stationCode;
    private String currentStationCode;
    private String lastStationCode;
    private String upcomingStationCode;
    private String currentStationDisplay;
    private String targetStationCode;
    private String nextStopStationCode;
    private List<String> stopStations = new ArrayList<>();
    private List<String> skipStations = new ArrayList<>();
    private String currentBlockId;
    private String nextSignalId;
    private SignalAspect signalAspect;
    private String waitingReason;
    private int delayMinutes;
    private Integer assignedPlatform;
    private Long assignedPlatformId;
    private String direction;
    private LocalDateTime actualArrival;
    private LocalDateTime actualDeparture;
    private LocalDateTime scheduledArrival;
    private LocalDateTime scheduledDeparture;
    private Double progressPercent = 0.0;
    private Double speedKmH = 0.0;

    public TrainSnapshotDto() {}

    public TrainCategory getTrainCategory() { return trainCategory; }
    public void setTrainCategory(TrainCategory trainCategory) { this.trainCategory = trainCategory; }

    public String getNextStopStationCode() { return nextStopStationCode; }
    public void setNextStopStationCode(String nextStopStationCode) { this.nextStopStationCode = nextStopStationCode; }

    public List<String> getStopStations() { return stopStations; }
    public void setStopStations(List<String> stopStations) { this.stopStations = stopStations; }

    public List<String> getSkipStations() { return skipStations; }
    public void setSkipStations(List<String> skipStations) { this.skipStations = skipStations; }

    public Double getProgressPercent() { return progressPercent != null ? progressPercent : 0.0; }
    public void setProgressPercent(Double progressPercent) { this.progressPercent = progressPercent; }

    public Double getSpeedKmH() { return speedKmH != null ? speedKmH : 0.0; }
    public void setSpeedKmH(Double speedKmH) { this.speedKmH = speedKmH; }

    public Long getTrainId() { return trainId; }
    public void setTrainId(Long trainId) { this.trainId = trainId; }

    public String getTrainNumber() { return trainNumber; }
    public void setTrainNumber(String trainNumber) { this.trainNumber = trainNumber; }

    public String getTrainName() { return trainName; }
    public void setTrainName(String trainName) { this.trainName = trainName; }

    public TrainType getTrainType() { return trainType; }
    public void setTrainType(TrainType trainType) { this.trainType = trainType; }

    public TrainStatus getStatus() { return status; }
    public void setStatus(TrainStatus status) { this.status = status; }

    public String getSourceStation() { return sourceStation; }
    public void setSourceStation(String sourceStation) { this.sourceStation = sourceStation; }

    public String getDestinationStation() { return destinationStation; }
    public void setDestinationStation(String destinationStation) { this.destinationStation = destinationStation; }

    public String getStationCode() { return stationCode; }
    public void setStationCode(String stationCode) { this.stationCode = stationCode; }

    public String getCurrentStationCode() { return currentStationCode; }
    public void setCurrentStationCode(String currentStationCode) { this.currentStationCode = currentStationCode; }

    public String getTargetStationCode() { return targetStationCode; }
    public void setTargetStationCode(String targetStationCode) { this.targetStationCode = targetStationCode; }

    public String getCurrentBlockId() { return currentBlockId; }
    public void setCurrentBlockId(String currentBlockId) { this.currentBlockId = currentBlockId; }

    public String getNextSignalId() { return nextSignalId; }
    public void setNextSignalId(String nextSignalId) { this.nextSignalId = nextSignalId; }

    public SignalAspect getSignalAspect() { return signalAspect; }
    public void setSignalAspect(SignalAspect signalAspect) { this.signalAspect = signalAspect; }

    public String getWaitingReason() { return waitingReason; }
    public void setWaitingReason(String waitingReason) { this.waitingReason = waitingReason; }

    public int getDelayMinutes() { return delayMinutes; }
    public void setDelayMinutes(int delayMinutes) { this.delayMinutes = delayMinutes; }

    public Integer getAssignedPlatform() { return assignedPlatform; }
    public void setAssignedPlatform(Integer assignedPlatform) { this.assignedPlatform = assignedPlatform; }

    public Long getAssignedPlatformId() { return assignedPlatformId; }
    public void setAssignedPlatformId(Long assignedPlatformId) { this.assignedPlatformId = assignedPlatformId; }

    public String getDirection() { return direction; }
    public void setDirection(String direction) { this.direction = direction; }

    public LocalDateTime getActualArrival() { return actualArrival; }
    public void setActualArrival(LocalDateTime actualArrival) { this.actualArrival = actualArrival; }

    public LocalDateTime getActualDeparture() { return actualDeparture; }
    public void setActualDeparture(LocalDateTime actualDeparture) { this.actualDeparture = actualDeparture; }

    public LocalDateTime getScheduledArrival() { return scheduledArrival; }
    public void setScheduledArrival(LocalDateTime scheduledArrival) { this.scheduledArrival = scheduledArrival; }

    public LocalDateTime getScheduledDeparture() { return scheduledDeparture; }
    public void setScheduledDeparture(LocalDateTime scheduledDeparture) { this.scheduledDeparture = scheduledDeparture; }

    public String getLastStationCode() { return lastStationCode; }
    public void setLastStationCode(String lastStationCode) { this.lastStationCode = lastStationCode; }

    public String getUpcomingStationCode() { return upcomingStationCode; }
    public void setUpcomingStationCode(String upcomingStationCode) { this.upcomingStationCode = upcomingStationCode; }

    public String getCurrentStationDisplay() { return currentStationDisplay; }
    public void setCurrentStationDisplay(String currentStationDisplay) { this.currentStationDisplay = currentStationDisplay; }
}
