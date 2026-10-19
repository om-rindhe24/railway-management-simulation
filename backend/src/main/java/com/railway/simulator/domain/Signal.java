package com.railway.simulator.domain;

/**
 * Signal represents an educational simulation color-light signal controlling entry
 * into a protected track block or platform line.
 *
 * NOTE: Educational simulation model only.
 */
public class Signal {

    private String signalId;
    private String stationCode;
    private String locationDescription;
    private String direction; // "UP", "DOWN", "COMMON"
    private LineType lineType;
    private String protectedBlockId;
    private String nextSignalId;
    private SignalAspect currentAspect = SignalAspect.RED;
    private String aspectReason = "Initial simulation state";
    private boolean enabled = true;

    public Signal() {}

    public Signal(String signalId, String stationCode, String locationDescription,
                  String direction, LineType lineType, String protectedBlockId) {
        this.signalId = signalId;
        this.stationCode = stationCode;
        this.locationDescription = locationDescription;
        this.direction = direction;
        this.lineType = lineType;
        this.protectedBlockId = protectedBlockId;
        this.currentAspect = SignalAspect.RED;
        this.enabled = true;
    }

    public String getSignalId() {
        return signalId;
    }

    public void setSignalId(String signalId) {
        this.signalId = signalId;
    }

    public String getStationCode() {
        return stationCode;
    }

    public void setStationCode(String stationCode) {
        this.stationCode = stationCode;
    }

    public String getLocationDescription() {
        return locationDescription;
    }

    public void setLocationDescription(String locationDescription) {
        this.locationDescription = locationDescription;
    }

    public String getDirection() {
        return direction;
    }

    public void setDirection(String direction) {
        this.direction = direction;
    }

    public LineType getLineType() {
        return lineType;
    }

    public void setLineType(LineType lineType) {
        this.lineType = lineType;
    }

    public String getProtectedBlockId() {
        return protectedBlockId;
    }

    public void setProtectedBlockId(String protectedBlockId) {
        this.protectedBlockId = protectedBlockId;
    }

    public String getNextSignalId() {
        return nextSignalId;
    }

    public void setNextSignalId(String nextSignalId) {
        this.nextSignalId = nextSignalId;
    }

    public SignalAspect getCurrentAspect() {
        return currentAspect;
    }

    public void setCurrentAspect(SignalAspect currentAspect) {
        this.currentAspect = currentAspect;
    }

    public String getAspectReason() {
        return aspectReason;
    }

    public void setAspectReason(String aspectReason) {
        this.aspectReason = aspectReason;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
}
