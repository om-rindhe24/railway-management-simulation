package com.railway.simulator.domain;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Train entity representing suburban, express, and special services.
 * Real train names/numbers are only used if verified; otherwise marked as simulated.
 * Schedules, delays, and priority levels are Configurable Simulation Parameters.
 */
@Entity
@Table(name = "trains", indexes = {
    @Index(name = "idx_train_number", columnList = "train_number", unique = true),
    @Index(name = "idx_train_status", columnList = "status"),
    @Index(name = "idx_train_station", columnList = "current_station_code"),
    @Index(name = "idx_train_priority", columnList = "simulation_priority")
})
public class Train {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "train_id")
    private Long trainId;

    @jakarta.validation.constraints.NotBlank(message = "Train number must not be blank")
    @jakarta.validation.constraints.Size(max = 20, message = "Train number must not exceed 20 characters")
    @Column(name = "train_number", nullable = false, unique = true, length = 20)
    private String trainNumber;

    @jakarta.validation.constraints.NotBlank(message = "Train name must not be blank")
    @jakarta.validation.constraints.Size(max = 100, message = "Train name must not exceed 100 characters")
    @Column(name = "train_name", nullable = false, length = 100)
    private String trainName;

    @jakarta.validation.constraints.NotNull(message = "Train type is required")
    @Enumerated(EnumType.STRING)
    @Column(name = "train_type", nullable = false, length = 30)
    private TrainType trainType = TrainType.LOCAL;

    @Enumerated(EnumType.STRING)
    @Column(name = "train_category", length = 30)
    private TrainCategory trainCategory;

    @Column(name = "stop_stations_str", length = 1000)
    private String stopStationsStr;

    @Transient
    private List<String> stopStations = new ArrayList<>();

    @Column(name = "next_stop_station_code", length = 10)
    private String nextStopStationCode;

    @jakarta.validation.constraints.Size(max = 50, message = "Source station must not exceed 50 characters")
    @Column(name = "source_station", nullable = false, length = 50)
    private String sourceStation;

    @jakarta.validation.constraints.Size(max = 50, message = "Destination station must not exceed 50 characters")
    @Column(name = "destination_station", nullable = false, length = 50)
    private String destinationStation;

    @jakarta.validation.constraints.Min(value = 1, message = "Simulation priority must be at least 1")
    @jakarta.validation.constraints.Max(value = 10, message = "Simulation priority must not exceed 10")
    @Column(name = "simulation_priority", nullable = false)
    private Integer simulationPriority = 5;

    @Column(name = "scheduled_arrival", nullable = false)
    private LocalDateTime scheduledArrival;

    @Column(name = "scheduled_departure", nullable = false)
    private LocalDateTime scheduledDeparture;

    @Column(name = "actual_arrival", nullable = false)
    private LocalDateTime actualArrival;

    @Column(name = "actual_departure", nullable = false)
    private LocalDateTime actualDeparture;

    @Column(name = "delay_minutes", nullable = false)
    private Integer delayMinutes = 0;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private TrainStatus status = TrainStatus.SCHEDULED;

    @Column(name = "current_station_code", nullable = false, length = 10)
    private String currentStationCode;

    @Column(name = "next_station_code", length = 10)
    private String nextStationCode;

    @Column(name = "last_station_code", length = 10)
    private String lastStationCode;

    @Column(name = "upcoming_station_code", length = 10)
    private String upcomingStationCode;

    @Column(name = "direction", length = 10)
    private String direction;

    @Column(name = "target_station_code", length = 10)
    private String targetStationCode;

    @Column(name = "assigned_platform_id")
    private Long assignedPlatformId;

    @Column(name = "assigned_platform_number")
    private Integer assignedPlatformNumber;

    @Column(name = "current_block_id", length = 50)
    private String currentBlockId;

    @Column(name = "next_signal_id", length = 50)
    private String nextSignalId;

    @Enumerated(EnumType.STRING)
    @Column(name = "signal_aspect", length = 20)
    private SignalAspect signalAspect;

    @Column(name = "waiting_reason", length = 255)
    private String waitingReason;

    @Column(name = "progress_percent")
    private Double progressPercent = 0.0;

    @Column(name = "speed_kmh")
    private Double speedKmH = 0.0;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    @Column(name = "is_simulated", nullable = false)
    private Boolean isSimulated = true;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();

    public Train() {}

    public Train(String trainNumber, String trainName, TrainType trainType,
                 String sourceStation, String destinationStation, Integer simulationPriority,
                 LocalDateTime scheduledArrival, LocalDateTime scheduledDeparture,
                 String currentStationCode, String targetStationCode) {
        this.trainNumber = trainNumber;
        this.trainName = trainName;
        this.trainType = trainType;
        this.sourceStation = sourceStation;
        this.destinationStation = destinationStation;
        this.simulationPriority = simulationPriority;
        this.scheduledArrival = scheduledArrival;
        this.scheduledDeparture = scheduledDeparture;
        this.actualArrival = scheduledArrival;
        this.actualDeparture = scheduledDeparture;
        this.delayMinutes = 0;
        this.status = TrainStatus.SCHEDULED;
        this.currentStationCode = currentStationCode;
        this.targetStationCode = targetStationCode;
        this.isActive = true;
        this.isSimulated = true;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
        this.trainCategory = getTrainCategory();

        String dir = "UP";
        if (this.destinationStation != null) {
            String dest = this.destinationStation.toUpperCase();
            if (dest.equals("VR") || dest.equals("DRD") || dest.equals("GNC") || dest.equals("NDLS")
                    || dest.equals("NZM") || dest.equals("ADI") || dest.equals("BHUJ") || dest.equals("ST") || dest.equals("ASR")) {
                dir = "DOWN";
            }
        }
        this.direction = dir;
        if (this.sourceStation != null && this.destinationStation != null) {
            List<String> defaultStops = com.railway.simulator.config.StoppingPatternConfig.generateDefaultStopStations(
                    this.trainCategory, this.sourceStation, this.destinationStation, dir);
            setStopStations(defaultStops);
        }
        if (targetStationCode != null) {
            this.nextStopStationCode = targetStationCode;
        } else if (this.stopStations != null && !this.stopStations.isEmpty()) {
            this.nextStopStationCode = this.stopStations.get(0);
        }
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

    public TrainType getTrainType() {
        return trainType;
    }

    public void setTrainType(TrainType trainType) {
        this.trainType = trainType;
    }

    public String getSourceStation() {
        return sourceStation;
    }

    public void setSourceStation(String sourceStation) {
        this.sourceStation = sourceStation;
    }

    public String getDestinationStation() {
        return destinationStation;
    }

    public void setDestinationStation(String destinationStation) {
        this.destinationStation = destinationStation;
    }

    public Integer getSimulationPriority() {
        return simulationPriority;
    }

    public void setSimulationPriority(Integer simulationPriority) {
        this.simulationPriority = simulationPriority;
    }

    public LocalDateTime getScheduledArrival() {
        return scheduledArrival;
    }

    public void setScheduledArrival(LocalDateTime scheduledArrival) {
        this.scheduledArrival = scheduledArrival;
    }

    public LocalDateTime getScheduledDeparture() {
        return scheduledDeparture;
    }

    public void setScheduledDeparture(LocalDateTime scheduledDeparture) {
        this.scheduledDeparture = scheduledDeparture;
    }

    public LocalDateTime getActualArrival() {
        return actualArrival;
    }

    public void setActualArrival(LocalDateTime actualArrival) {
        this.actualArrival = actualArrival;
    }

    public LocalDateTime getActualDeparture() {
        return actualDeparture;
    }

    public void setActualDeparture(LocalDateTime actualDeparture) {
        this.actualDeparture = actualDeparture;
    }

    public Integer getDelayMinutes() {
        return delayMinutes;
    }

    public void setDelayMinutes(Integer delayMinutes) {
        this.delayMinutes = delayMinutes;
    }

    public TrainStatus getStatus() {
        return status;
    }

    public void setStatus(TrainStatus status) {
        this.status = status;
    }

    public String getCurrentStationCode() {
        return currentStationCode;
    }

    public void setCurrentStationCode(String currentStationCode) {
        this.currentStationCode = currentStationCode;
    }

    public String getNextStationCode() {
        return nextStationCode;
    }

    public void setNextStationCode(String nextStationCode) {
        this.nextStationCode = nextStationCode;
    }

    public String getTargetStationCode() {
        return targetStationCode;
    }

    public void setTargetStationCode(String targetStationCode) {
        this.targetStationCode = targetStationCode;
    }

    public Long getAssignedPlatformId() {
        return assignedPlatformId;
    }

    public void setAssignedPlatformId(Long assignedPlatformId) {
        this.assignedPlatformId = assignedPlatformId;
    }

    public Integer getAssignedPlatformNumber() {
        return assignedPlatformNumber;
    }

    public void setAssignedPlatformNumber(Integer assignedPlatformNumber) {
        this.assignedPlatformNumber = assignedPlatformNumber;
    }

    public Boolean getIsActive() {
        return isActive;
    }

    public void setIsActive(Boolean active) {
        isActive = active;
    }

    public Boolean getIsSimulated() {
        return isSimulated;
    }

    public void setIsSimulated(Boolean simulated) {
        isSimulated = simulated;
    }

    public String getCurrentBlockId() {
        return currentBlockId;
    }

    public void setCurrentBlockId(String currentBlockId) {
        this.currentBlockId = currentBlockId;
    }

    public String getNextSignalId() {
        return nextSignalId;
    }

    public void setNextSignalId(String nextSignalId) {
        this.nextSignalId = nextSignalId;
    }

    public SignalAspect getSignalAspect() {
        return signalAspect;
    }

    public void setSignalAspect(SignalAspect signalAspect) {
        this.signalAspect = signalAspect;
    }

    public String getWaitingReason() {
        return waitingReason;
    }

    public void setWaitingReason(String waitingReason) {
        this.waitingReason = waitingReason;
    }

    public Double getProgressPercent() {
        return progressPercent != null ? progressPercent : 0.0;
    }

    public void setProgressPercent(Double progressPercent) {
        this.progressPercent = progressPercent;
    }

    public Double getSpeedKmH() {
        return speedKmH != null ? speedKmH : 0.0;
    }

    public void setSpeedKmH(Double speedKmH) {
        this.speedKmH = speedKmH;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public TrainCategory getTrainCategory() {
        if (this.trainCategory != null) {
            return this.trainCategory;
        }
        if (this.trainType == TrainType.LOCAL) {
            if (this.trainName != null && this.trainName.toLowerCase().contains("fast")) {
                return TrainCategory.FAST_LOCAL;
            }
            return TrainCategory.SLOW_LOCAL;
        }
        if (this.trainType == TrainType.EXPRESS_MAIL) {
            if (this.trainName != null && this.trainName.toLowerCase().contains("mail")) {
                return TrainCategory.MAIL;
            }
            return TrainCategory.EXPRESS;
        }
        if (this.trainType == TrainType.SUPERFAST) return TrainCategory.SUPERFAST;
        if (this.trainType == TrainType.PREMIUM) return TrainCategory.PREMIUM;
        if (this.trainType == TrainType.FREIGHT) return TrainCategory.FREIGHT;
        if (this.trainType == TrainType.SPECIAL) return TrainCategory.SPECIAL;
        return TrainCategory.SLOW_LOCAL;
    }

    public void setTrainCategory(TrainCategory trainCategory) {
        this.trainCategory = trainCategory;
    }

    public List<String> getStopStations() {
        if ((this.stopStations == null || this.stopStations.isEmpty()) && this.stopStationsStr != null && !this.stopStationsStr.isBlank()) {
            this.stopStations = new ArrayList<>(Arrays.asList(this.stopStationsStr.split(",")));
        }
        if ((this.stopStations == null || this.stopStations.isEmpty()) && this.sourceStation != null && this.destinationStation != null) {
            String dir = "UP";
            String dest = this.destinationStation.toUpperCase();
            if (dest.equals("VR") || dest.equals("DRD") || dest.equals("GNC") || dest.equals("NDLS")
                    || dest.equals("NZM") || dest.equals("ADI") || dest.equals("BHUJ") || dest.equals("ST")) {
                dir = "DOWN";
            }
            List<String> defaultStops = com.railway.simulator.config.StoppingPatternConfig.generateDefaultStopStations(
                    getTrainCategory(), this.sourceStation, this.destinationStation, dir);
            setStopStations(defaultStops);
        }
        return this.stopStations != null ? this.stopStations : Collections.emptyList();
    }

    public void setStopStations(List<String> stops) {
        this.stopStations = stops != null ? new ArrayList<>(stops) : new ArrayList<>();
        this.stopStationsStr = (stops != null && !stops.isEmpty()) ? String.join(",", stops) : "";
    }

    public String getStopStationsStr() {
        return stopStationsStr;
    }

    public void setStopStationsStr(String stopStationsStr) {
        this.stopStationsStr = stopStationsStr;
        if (stopStationsStr != null && !stopStationsStr.isBlank()) {
            this.stopStations = new ArrayList<>(Arrays.asList(stopStationsStr.split(",")));
        } else {
            this.stopStations = new ArrayList<>();
        }
    }

    public String getNextStopStationCode() {
        return nextStopStationCode;
    }

    public void setNextStopStationCode(String nextStopStationCode) {
        this.nextStopStationCode = nextStopStationCode;
    }

    public boolean stopsAtStation(String stationCode) {
        if (stationCode == null) return false;
        List<String> stops = getStopStations();
        if (stops != null && !stops.isEmpty()) {
            return stops.stream().anyMatch(s -> s.equalsIgnoreCase(stationCode));
        }
        if (stationCode.equalsIgnoreCase(this.destinationStation) || stationCode.equalsIgnoreCase(this.targetStationCode)) {
            return true;
        }
        return false;
    }

    public String getLastStationCode() {
        return lastStationCode;
    }

    public void setLastStationCode(String lastStationCode) {
        this.lastStationCode = lastStationCode;
    }

    public String getUpcomingStationCode() {
        return upcomingStationCode;
    }

    public void setUpcomingStationCode(String upcomingStationCode) {
        this.upcomingStationCode = upcomingStationCode;
    }

    public String getDirection() {
        return direction;
    }

    public void setDirection(String direction) {
        this.direction = direction;
    }

    public String getCurrentStationDisplay() {
        if (this.upcomingStationCode == null || this.upcomingStationCode.isBlank()) {
            return "At " + (this.lastStationCode != null ? this.lastStationCode : this.currentStationCode);
        }
        return "Between " + (this.lastStationCode != null ? this.lastStationCode : this.currentStationCode)
                + " and " + this.upcomingStationCode;
    }

    public List<String> getSkipStations(List<String> routeStations) {
        if (routeStations == null) return Collections.emptyList();
        List<String> stops = getStopStations();
        return routeStations.stream()
                .filter(s -> stops.stream().noneMatch(stop -> stop.equalsIgnoreCase(s)))
                .toList();
    }
}

