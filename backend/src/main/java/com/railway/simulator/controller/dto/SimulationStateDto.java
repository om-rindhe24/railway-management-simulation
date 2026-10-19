package com.railway.simulator.controller.dto;

import java.time.LocalDateTime;

public class SimulationStateDto {
    private LocalDateTime simulationTime;
    private boolean running;
    private int speedMultiplier;

    public SimulationStateDto() {}

    public SimulationStateDto(LocalDateTime simulationTime, boolean running, int speedMultiplier) {
        this.simulationTime = simulationTime;
        this.running = running;
        this.speedMultiplier = speedMultiplier;
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
}
