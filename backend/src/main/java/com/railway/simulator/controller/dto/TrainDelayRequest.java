package com.railway.simulator.controller.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public class TrainDelayRequest {

    @NotNull(message = "Delay minutes must be provided")
    @Min(value = 1, message = "Delay must be at least 1 minute")
    private Integer delayMinutes;

    private String reason;

    public TrainDelayRequest() {}

    public TrainDelayRequest(Integer delayMinutes, String reason) {
        this.delayMinutes = delayMinutes;
        this.reason = reason;
    }

    public Integer getDelayMinutes() {
        return delayMinutes;
    }

    public void setDelayMinutes(Integer delayMinutes) {
        this.delayMinutes = delayMinutes;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }
}
