package com.railway.simulator.controller.dto;

import com.railway.simulator.domain.LineType;
import com.railway.simulator.domain.SignalAspect;

public class SignalSnapshotDto {
    private String signalId;
    private String protectedBlockId;
    private String stationCode;
    private String direction;
    private LineType lineType;
    private SignalAspect aspect;
    private String reason;
    private String nextSignalId;

    public SignalSnapshotDto() {}

    public String getSignalId() { return signalId; }
    public void setSignalId(String signalId) { this.signalId = signalId; }

    public String getProtectedBlockId() { return protectedBlockId; }
    public void setProtectedBlockId(String protectedBlockId) { this.protectedBlockId = protectedBlockId; }

    public String getStationCode() { return stationCode; }
    public void setStationCode(String stationCode) { this.stationCode = stationCode; }

    public String getDirection() { return direction; }
    public void setDirection(String direction) { this.direction = direction; }

    public LineType getLineType() { return lineType; }
    public void setLineType(LineType lineType) { this.lineType = lineType; }

    public SignalAspect getAspect() { return aspect; }
    public void setAspect(SignalAspect aspect) { this.aspect = aspect; }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }

    public String getNextSignalId() { return nextSignalId; }
    public void setNextSignalId(String nextSignalId) { this.nextSignalId = nextSignalId; }
}
