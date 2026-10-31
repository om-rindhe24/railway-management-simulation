package com.railway.simulator.controller.dto;

import java.time.LocalDateTime;

public class SimulationStateDto {
    private LocalDateTime simulationTime;
    private boolean running;
    private int speedMultiplier;
    private boolean peakHour;

    public SimulationStateDto() {}

    public SimulationStateDto(LocalDateTime simulationTime, boolean running, int speedMultiplier) {
        this(simulationTime, running, speedMultiplier, false);
    }

    public SimulationStateDto(LocalDateTime simulationTime, boolean running, int speedMultiplier, boolean peakHour) {
        this.simulationTime = simulationTime;
        this.running = running;
        this.speedMultiplier = speedMultiplier;
        this.peakHour = peakHour;
    }

    public LocalDateTime getSimulationTime() {
        return simulationTime;
    }

    public void setSimulationTime(LocalDateTime simulationTime) {
        this.simulationTime = simulationTime;
    }

    public boolean isRunning() {
        return running;
    }

    public void setRunning(boolean running) {
        this.running = running;
    }

    public int getSpeedMultiplier() {
        return speedMultiplier;
    }

    public void setSpeedMultiplier(int speedMultiplier) {
        this.speedMultiplier = speedMultiplier;
    }

    public boolean isPeakHour() {
        return peakHour;
    }

    public void setPeakHour(boolean peakHour) {
        this.peakHour = peakHour;
    }
}
