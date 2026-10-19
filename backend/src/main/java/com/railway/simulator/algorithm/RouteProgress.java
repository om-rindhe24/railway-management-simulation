package com.railway.simulator.algorithm;

/**
 * Authoritative single source of truth for train route progress along the corridor.
 * Encapsulates the train's last station, upcoming station, and next scheduled stop.
 */
public class RouteProgress {

    private final String lastStationCode;
    private final String upcomingStationCode;
    private final String nextStopStationCode;

    public RouteProgress(String lastStationCode, String upcomingStationCode, String nextStopStationCode) {
        this.lastStationCode = lastStationCode;
        this.upcomingStationCode = upcomingStationCode;
        this.nextStopStationCode = nextStopStationCode;
    }

    public String getLastStationCode() {
        return lastStationCode;
    }

    public String getUpcomingStationCode() {
        return upcomingStationCode;
    }

    public String getNextStopStationCode() {
        return nextStopStationCode;
    }

    public String getCurrentStationDisplay() {
        if (upcomingStationCode == null || upcomingStationCode.isBlank()) {
            return "At " + lastStationCode;
        }
        return "Between " + lastStationCode + " and " + upcomingStationCode;
    }

    @Override
    public String toString() {
        return "RouteProgress{" +
                "lastStationCode='" + lastStationCode + '\'' +
                ", upcomingStationCode='" + upcomingStationCode + '\'' +
                ", nextStopStationCode='" + nextStopStationCode + '\'' +
                ", display='" + getCurrentStationDisplay() + '\'' +
                '}';
    }
}
