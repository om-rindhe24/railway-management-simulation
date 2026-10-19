package com.railway.simulator.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

/**
 * Station Platform model.
 * Line type assignments and supported train types are Configurable Simulation Assumptions.
 */
@Entity
@Table(name = "platforms", indexes = {
    @Index(name = "idx_platform_station", columnList = "station_id"),
    @Index(name = "idx_platform_status", columnList = "is_occupied, is_closed")
})
public class Platform {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "platform_id")
    private Long platformId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "station_id", nullable = false)
    @JsonIgnore
    private Station station;

    @Column(name = "station_code_snapshot", nullable = false, length = 10)
    private String stationCode;

    @Column(name = "platform_number", nullable = false)
    private Integer platformNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "line_type", nullable = false, length = 20)
    private LineType lineType = LineType.COMMON;

    @Column(name = "direction", length = 10)
    private String direction;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "platform_supported_train_types", joinColumns = @JoinColumn(name = "platform_id"))
    @Enumerated(EnumType.STRING)
    @Column(name = "train_type", length = 30)
    private Set<TrainType> supportedTrainTypes = new HashSet<>();

    @Column(name = "is_occupied", nullable = false)
    private Boolean isOccupied = false;

    @Column(name = "is_closed", nullable = false)
    private Boolean isClosed = false;

    @Column(name = "closure_reason", length = 255)
    private String closureReason;

    @Column(name = "current_train_id")
    private Long currentTrainId;

    @Column(name = "current_train_number", length = 20)
    private String currentTrainNumber;

    @Column(name = "occupancy_start")
    private LocalDateTime occupancyStart;

    @Column(name = "occupancy_end")
    private LocalDateTime occupancyEnd;

    public Platform() {}

    public Platform(Station station, Integer platformNumber, LineType lineType, Set<TrainType> supportedTrainTypes) {
        this.station = station;
        this.stationCode = station.getStationCode();
        this.platformNumber = platformNumber;
        this.lineType = lineType;
        this.supportedTrainTypes = supportedTrainTypes;
        this.isOccupied = false;
        this.isClosed = false;
        this.direction = (lineType == LineType.SLOW_UP || lineType == LineType.FAST_UP) ? "UP" :
                         (lineType == LineType.SLOW_DOWN || lineType == LineType.FAST_DOWN) ? "DOWN" : "COMMON";
    }

    public Long getPlatformId() {
        return platformId;
    }

    public void setPlatformId(Long platformId) {
        this.platformId = platformId;
    }

    public Station getStation() {
        return station;
    }

    public void setStation(Station station) {
        this.station = station;
        if (station != null) {
            this.stationCode = station.getStationCode();
        }
    }

    public String getStationCode() {
        return stationCode;
    }

    public void setStationCode(String stationCode) {
        this.stationCode = stationCode;
    }

    public Integer getPlatformNumber() {
        return platformNumber;
    }

    public void setPlatformNumber(Integer platformNumber) {
        this.platformNumber = platformNumber;
    }

    public LineType getLineType() {
        return lineType;
    }

    public void setLineType(LineType lineType) {
        this.lineType = lineType;
        if (this.direction == null) {
            this.direction = (lineType == LineType.SLOW_UP || lineType == LineType.FAST_UP) ? "UP" :
                             (lineType == LineType.SLOW_DOWN || lineType == LineType.FAST_DOWN) ? "DOWN" : "COMMON";
        }
    }

    public String getDirection() {
        if (direction != null && !direction.isBlank()) {
            return direction;
        }
        if (lineType == LineType.SLOW_UP || lineType == LineType.FAST_UP) return "UP";
        if (lineType == LineType.SLOW_DOWN || lineType == LineType.FAST_DOWN) return "DOWN";
        return "COMMON";
    }

    public void setDirection(String direction) {
        this.direction = direction;
    }

    public Set<TrainType> getSupportedTrainTypes() {
        return supportedTrainTypes;
    }

    public void setSupportedTrainTypes(Set<TrainType> supportedTrainTypes) {
        this.supportedTrainTypes = supportedTrainTypes;
    }

    public Boolean getIsOccupied() {
        return isOccupied;
    }

    public void setIsOccupied(Boolean occupied) {
        isOccupied = occupied;
    }

    public Boolean getIsClosed() {
        return isClosed;
    }

    public void setIsClosed(Boolean closed) {
        isClosed = closed;
    }

    public String getClosureReason() {
        return closureReason;
    }

    public void setClosureReason(String closureReason) {
        this.closureReason = closureReason;
    }

    public Long getCurrentTrainId() {
        return currentTrainId;
    }

    public void setCurrentTrainId(Long currentTrainId) {
        this.currentTrainId = currentTrainId;
    }

    public String getCurrentTrainNumber() {
        return currentTrainNumber;
    }

    public void setCurrentTrainNumber(String currentTrainNumber) {
        this.currentTrainNumber = currentTrainNumber;
    }

    public LocalDateTime getOccupancyStart() {
        return occupancyStart;
    }

    public void setOccupancyStart(LocalDateTime occupancyStart) {
        this.occupancyStart = occupancyStart;
    }

    public LocalDateTime getOccupancyEnd() {
        return occupancyEnd;
    }

    public void setOccupancyEnd(LocalDateTime occupancyEnd) {
        this.occupancyEnd = occupancyEnd;
    }
}
