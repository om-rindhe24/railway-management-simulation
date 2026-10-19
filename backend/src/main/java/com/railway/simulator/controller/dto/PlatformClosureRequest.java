package com.railway.simulator.controller.dto;

import jakarta.validation.constraints.Size;

public class PlatformClosureRequest {

    @Size(max = 255, message = "Closure reason must not exceed 255 characters")
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
