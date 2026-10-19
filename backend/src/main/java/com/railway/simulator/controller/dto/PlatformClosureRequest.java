package com.railway.simulator.controller.dto;

public class PlatformClosureRequest {
    private String reason;

    public PlatformClosureRequest() {}

    public PlatformClosureRequest(String reason) {
        this.reason = reason;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }
}
