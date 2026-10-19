package com.railway.simulator.domain;

import jakarta.persistence.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Railway Station along the Virar to Mumbai Central corridor.
 * Station names are authentic geographical locations.
 * Platform counts and line support flags are Configurable Simulation Assumptions.
 */
@Entity
@Table(name = "stations", indexes = {
    @Index(name = "idx_station_code", columnList = "station_code", unique = true),
    @Index(name = "idx_station_sequence", columnList = "sequence_order")
})
public class Station {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "station_id")
    private Long stationId;

    @Column(name = "station_code", nullable = false, unique = true, length = 10)
    private String stationCode;

    @Column(name = "station_name", nullable = false, length = 100)
    private String stationName;

    @Column(name = "number_of_platforms", nullable = false)
    private Integer numberOfPlatforms;

    @Column(name = "local_train_support", nullable = false)
    private Boolean localTrainSupport = true;

    @Column(name = "express_train_support", nullable = false)
    private Boolean expressTrainSupport = false;

    @Column(name = "fast_line_support", nullable = false)
    private Boolean fastLineSupport = true;

    @Column(name = "slow_line_support", nullable = false)
    private Boolean slowLineSupport = true;

    @Column(name = "terminal_flag", nullable = false)
    private Boolean terminalFlag = false;

    @Column(name = "sequence_order", nullable = false)
    private Integer sequenceOrder; // 1 = Virar, ..., 24 = Mumbai Central

    @Column(name = "status", nullable = false, length = 20)
    private String status = "ACTIVE"; // ACTIVE, DISRUPTED, CLOSED

    @Column(name = "latitude")
    private Double latitude;

    @Column(name = "longitude")
    private Double longitude;

    @OneToMany(mappedBy = "station", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<Platform> platforms = new ArrayList<>();

    public Station() {}

    public Station(String stationCode, String stationName, Integer numberOfPlatforms,
                   Boolean localTrainSupport, Boolean expressTrainSupport,
                   Boolean fastLineSupport, Boolean slowLineSupport,
                   Boolean terminalFlag, Integer sequenceOrder) {
        this(stationCode, stationName, numberOfPlatforms, localTrainSupport, expressTrainSupport,
             fastLineSupport, slowLineSupport, terminalFlag, sequenceOrder, null, null);
    }

    public Station(String stationCode, String stationName, Integer numberOfPlatforms,
                   Boolean localTrainSupport, Boolean expressTrainSupport,
                   Boolean fastLineSupport, Boolean slowLineSupport,
                   Boolean terminalFlag, Integer sequenceOrder,
                   Double latitude, Double longitude) {
        this.stationCode = stationCode;
        this.stationName = stationName;
        this.numberOfPlatforms = numberOfPlatforms;
        this.localTrainSupport = localTrainSupport;
        this.expressTrainSupport = expressTrainSupport;
        this.fastLineSupport = fastLineSupport;
        this.slowLineSupport = slowLineSupport;
        this.terminalFlag = terminalFlag;
        this.sequenceOrder = sequenceOrder;
        this.latitude = latitude;
        this.longitude = longitude;
        this.status = "ACTIVE";
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

    public String getStationName() {
        return stationName;
    }

    public void setStationName(String stationName) {
        this.stationName = stationName;
    }

    public Integer getNumberOfPlatforms() {
        return numberOfPlatforms;
    }

    public void setNumberOfPlatforms(Integer numberOfPlatforms) {
        this.numberOfPlatforms = numberOfPlatforms;
    }

    public Boolean getLocalTrainSupport() {
        return localTrainSupport;
    }

    public void setLocalTrainSupport(Boolean localTrainSupport) {
        this.localTrainSupport = localTrainSupport;
    }

    public Boolean getExpressTrainSupport() {
        return expressTrainSupport;
    }

    public void setExpressTrainSupport(Boolean expressTrainSupport) {
        this.expressTrainSupport = expressTrainSupport;
    }

    public Boolean getFastLineSupport() {
        return fastLineSupport;
    }

    public void setFastLineSupport(Boolean fastLineSupport) {
        this.fastLineSupport = fastLineSupport;
    }

    public Boolean getSlowLineSupport() {
        return slowLineSupport;
    }

    public void setSlowLineSupport(Boolean slowLineSupport) {
        this.slowLineSupport = slowLineSupport;
    }

    public Boolean getTerminalFlag() {
        return terminalFlag;
    }

    public void setTerminalFlag(Boolean terminalFlag) {
        this.terminalFlag = terminalFlag;
    }

    public Integer getSequenceOrder() {
        return sequenceOrder;
    }

    public void setSequenceOrder(Integer sequenceOrder) {
        this.sequenceOrder = sequenceOrder;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public List<Platform> getPlatforms() {
        return platforms;
    }

    public void setPlatforms(List<Platform> platforms) {
        this.platforms = platforms;
    }

    public Double getLatitude() {
        return latitude;
    }

    public void setLatitude(Double latitude) {
        this.latitude = latitude;
    }

    public Double getLongitude() {
        return longitude;
    }

    public void setLongitude(Double longitude) {
        this.longitude = longitude;
    }
}
