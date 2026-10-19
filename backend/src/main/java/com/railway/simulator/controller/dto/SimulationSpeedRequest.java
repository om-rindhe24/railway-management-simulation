package com.railway.simulator.controller.dto;

public class SimulationSpeedRequest {
    private int speed;

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
