package com.railway.simulator.controller.dto;

import com.railway.simulator.domain.LineType;

import java.time.LocalDateTime;

public class PlatformSnapshotDto {
    private Long platformId;
    private String stationCode;
    private Integer platformNumber;
    private LineType lineType;
    private boolean occupied;
    private boolean closed;
    private String closureReason;
    private Long currentTrainId;
    private String currentTrainNumber;
    private LocalDateTime occupancyStart;
    private LocalDateTime occupancyEnd;
    private String direction;
    private Integer dwellSecondsRemaining;
    private String nextWaitingTrainNumber;
    private String homeSignalId;
    private com.railway.simulator.domain.SignalAspect homeSignalAspect;
    private String starterSignalId;
    private com.railway.simulator.domain.SignalAspect starterSignalAspect;

    public PlatformSnapshotDto() {}

    public String getHomeSignalId() { return homeSignalId; }
    public void setHomeSignalId(String homeSignalId) { this.homeSignalId = homeSignalId; }

    public com.railway.simulator.domain.SignalAspect getHomeSignalAspect() { return homeSignalAspect; }
    public void setHomeSignalAspect(com.railway.simulator.domain.SignalAspect homeSignalAspect) { this.homeSignalAspect = homeSignalAspect; }

    public String getStarterSignalId() { return starterSignalId; }
    public void setStarterSignalId(String starterSignalId) { this.starterSignalId = starterSignalId; }

    public com.railway.simulator.domain.SignalAspect getStarterSignalAspect() { return starterSignalAspect; }
    public void setStarterSignalAspect(com.railway.simulator.domain.SignalAspect starterSignalAspect) { this.starterSignalAspect = starterSignalAspect; }

    public String getDirection() { return direction; }
    public void setDirection(String direction) { this.direction = direction; }

    public Integer getDwellSecondsRemaining() { return dwellSecondsRemaining; }
    public void setDwellSecondsRemaining(Integer dwellSecondsRemaining) { this.dwellSecondsRemaining = dwellSecondsRemaining; }

    public String getNextWaitingTrainNumber() { return nextWaitingTrainNumber; }
    public void setNextWaitingTrainNumber(String nextWaitingTrainNumber) { this.nextWaitingTrainNumber = nextWaitingTrainNumber; }

    public Long getPlatformId() { return platformId; }
    public void setPlatformId(Long platformId) { this.platformId = platformId; }

    public String getStationCode() { return stationCode; }
    public void setStationCode(String stationCode) { this.stationCode = stationCode; }

    public Integer getPlatformNumber() { return platformNumber; }
    public void setPlatformNumber(Integer platformNumber) { this.platformNumber = platformNumber; }

    public LineType getLineType() { return lineType; }
    public void setLineType(LineType lineType) { this.lineType = lineType; }

    public boolean isOccupied() { return occupied; }
    public void setOccupied(boolean occupied) { this.occupied = occupied; }

    public boolean isClosed() { return closed; }
    public void setClosed(boolean closed) { this.closed = closed; }

    public String getClosureReason() { return closureReason; }
    public void setClosureReason(String closureReason) { this.closureReason = closureReason; }

    public Long getCurrentTrainId() { return currentTrainId; }
    public void setCurrentTrainId(Long currentTrainId) { this.currentTrainId = currentTrainId; }

    public String getCurrentTrainNumber() { return currentTrainNumber; }
    public void setCurrentTrainNumber(String currentTrainNumber) { this.currentTrainNumber = currentTrainNumber; }

    public LocalDateTime getOccupancyStart() { return occupancyStart; }
    public void setOccupancyStart(LocalDateTime occupancyStart) { this.occupancyStart = occupancyStart; }

    public LocalDateTime getOccupancyEnd() { return occupancyEnd; }
    public void setOccupancyEnd(LocalDateTime occupancyEnd) { this.occupancyEnd = occupancyEnd; }
}
