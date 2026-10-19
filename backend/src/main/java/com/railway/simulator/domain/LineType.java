package com.railway.simulator.domain;

/**
 * Line / Corridor track type.
 * Simulation parameter distinguishing slow suburban tracks from fast mainline tracks.
 */
public enum LineType {
    SLOW_UP("Slow Line (Southbound / Churchgate / Mumbai Central)"),
    SLOW_DOWN("Slow Line (Northbound / Virar)"),
    FAST_UP("Fast Line (Southbound / Churchgate / Mumbai Central)"),
    FAST_DOWN("Fast Line (Northbound / Virar)"),
    COMMON("Common / Bidirectional Platform Track");

    private final String description;

    LineType(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
