package com.railway.simulator.controller.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public class SimulationSpeedRequest {

    @Min(value = 1, message = "Simulation speed multiplier must be at least 1")
    @Max(value = 10, message = "Simulation speed multiplier must not exceed 10")
    private int speed = 1;

    public SimulationSpeedRequest() {}

    public SimulationSpeedRequest(int speed) {
        this.speed = speed;
    }

    public int getSpeed() {
        return speed;
    }

    public void setSpeed(int speed) {
        this.speed = speed;
    }
}
