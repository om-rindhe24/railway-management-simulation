package com.railway.simulator.domain;

/**
 * Educational 4-aspect railway simulation signal states.
 * Note: Configurable educational simulation rules only, not official IR signaling rules.
 */
public enum SignalAspect {
    RED("Stop - Restrictive: Protected block is not clear to enter"),
    YELLOW("Caution - Prepare to stop at next signal"),
    DOUBLE_YELLOW("Attention - Advanced Caution: Prepare to pass next signal at restricted speed"),
    GREEN("Proceed - Fully permissive: Route and downstream blocks clear");

    private final String description;

    SignalAspect(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }

    public boolean isPermissive() {
        return this != RED;
    }
}
