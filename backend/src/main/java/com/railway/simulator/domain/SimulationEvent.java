package com.railway.simulator.domain;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * Operational event emitted during simulation progression or scenario injection.
 */
@Entity
@Table(name = "simulation_events", indexes = {
    @Index(name = "idx_event_timestamp", columnList = "timestamp DESC"),
    @Index(name = "idx_event_severity", columnList = "severity")
})
public class SimulationEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "event_id")
    private Long eventId;

    @Column(name = "event_type", nullable = false, length = 50)
    private String eventType;

    @Enumerated(EnumType.STRING)
    @Column(name = "severity", nullable = false, length = 20)
    private Severity severity = Severity.INFO;

    @Column(name = "timestamp", nullable = false)
    private LocalDateTime timestamp = LocalDateTime.now();

    @Column(name = "message", nullable = false, columnDefinition = "TEXT")
    private String message;

    @Column(name = "affected_train_number", length = 20)
    private String affectedTrainNumber;

    @Column(name = "affected_platform_number")
    private Integer affectedPlatformNumber;

    @Column(name = "affected_station_code", length = 10)
    private String affectedStationCode;

    public SimulationEvent() {}

    public SimulationEvent(String eventType, Severity severity, String message,
                           String affectedTrainNumber, Integer affectedPlatformNumber,
                           String affectedStationCode) {
        this.eventType = eventType;
        this.severity = severity;
        this.message = message;
        this.affectedTrainNumber = affectedTrainNumber;
        this.affectedPlatformNumber = affectedPlatformNumber;
        this.affectedStationCode = affectedStationCode;
        this.timestamp = LocalDateTime.now();
    }

    public Long getEventId() {
        return eventId;
    }

    public void setEventId(Long eventId) {
        this.eventId = eventId;
    }

    public String getEventType() {
        return eventType;
    }

    public void setEventType(String eventType) {
        this.eventType = eventType;
    }

    public Severity getSeverity() {
        return severity;
    }

    public void setSeverity(Severity severity) {
        this.severity = severity;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(LocalDateTime timestamp) {
        this.timestamp = timestamp;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getAffectedTrainNumber() {
        return affectedTrainNumber;
    }

    public void setAffectedTrainNumber(String affectedTrainNumber) {
        this.affectedTrainNumber = affectedTrainNumber;
    }

    public Integer getAffectedPlatformNumber() {
        return affectedPlatformNumber;
    }

    public void setAffectedPlatformNumber(Integer affectedPlatformNumber) {
        this.affectedPlatformNumber = affectedPlatformNumber;
    }

    public String getAffectedStationCode() {
        return affectedStationCode;
    }

    public void setAffectedStationCode(String affectedStationCode) {
        this.affectedStationCode = affectedStationCode;
    }
}
