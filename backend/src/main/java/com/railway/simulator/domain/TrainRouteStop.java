package com.railway.simulator.domain;

import jakarta.persistence.*;
import java.time.LocalTime;

/**
 * Route stop sequence for a train along the corridor.
 */
@Entity
@Table(name = "train_route_stops", indexes = {
    @Index(name = "idx_route_train", columnList = "train_id"),
    @Index(name = "idx_route_station", columnList = "station_code")
})
public class TrainRouteStop {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "stop_id")
    private Long stopId;

    @Column(name = "train_id", nullable = false)
    private Long trainId;

    @Column(name = "stop_sequence", nullable = false)
    private Integer stopSequence;

    @Column(name = "station_code", nullable = false, length = 10)
    private String stationCode;

    @Column(name = "scheduled_arrival_time")
    private LocalTime scheduledArrivalTime;

    @Column(name = "scheduled_departure_time")
    private LocalTime scheduledDepartureTime;

    @Column(name = "halt_duration_minutes", nullable = false)
    private Integer haltDurationMinutes = 2;

    @Column(name = "is_passed", nullable = false)
    private Boolean isPassed = false;

    public TrainRouteStop() {}

    public TrainRouteStop(Long trainId, Integer stopSequence, String stationCode,
                          LocalTime scheduledArrivalTime, LocalTime scheduledDepartureTime,
                          Integer haltDurationMinutes) {
        this.trainId = trainId;
        this.stopSequence = stopSequence;
        this.stationCode = stationCode;
        this.scheduledArrivalTime = scheduledArrivalTime;
        this.scheduledDepartureTime = scheduledDepartureTime;
        this.haltDurationMinutes = haltDurationMinutes;
        this.isPassed = false;
    }

    public Long getStopId() {
        return stopId;
    }

    public void setStopId(Long stopId) {
        this.stopId = stopId;
    }

    public Long getTrainId() {
        return trainId;
    }

    public void setTrainId(Long trainId) {
        this.trainId = trainId;
    }

    public Integer getStopSequence() {
        return stopSequence;
    }

    public void setStopSequence(Integer stopSequence) {
        this.stopSequence = stopSequence;
    }

    public String getStationCode() {
        return stationCode;
    }

    public void setStationCode(String stationCode) {
        this.stationCode = stationCode;
    }

    public LocalTime getScheduledArrivalTime() {
        return scheduledArrivalTime;
    }

    public void setScheduledArrivalTime(LocalTime scheduledArrivalTime) {
        this.scheduledArrivalTime = scheduledArrivalTime;
    }

    public LocalTime getScheduledDepartureTime() {
        return scheduledDepartureTime;
    }

    public void setScheduledDepartureTime(LocalTime scheduledDepartureTime) {
        this.scheduledDepartureTime = scheduledDepartureTime;
    }

    public Integer getHaltDurationMinutes() {
        return haltDurationMinutes;
    }

    public void setHaltDurationMinutes(Integer haltDurationMinutes) {
        this.haltDurationMinutes = haltDurationMinutes;
    }

    public Boolean getIsPassed() {
        return isPassed;
    }

    public void setIsPassed(Boolean isPassed) {
        this.isPassed = isPassed;
    }
}
