package com.railway.simulator.domain;

/**
 * Train category in the simulation.
 * Priority and operating rules are configurable simulation policies,
 * not claims about official Indian Railways operations.
 */
public enum TrainType {
    SPECIAL("Special / Emergency", 1),
    PREMIUM("Premium Service (Rajdhani/Shatabdi Style)", 2),
    SUPERFAST("Superfast Express", 3),
    EXPRESS_MAIL("Express / Mail", 4),
    LOCAL("Suburban Local", 5),
    FREIGHT("Freight / Goods", 6);

    private final String displayName;
    private final int defaultSimulationPriority;

    TrainType(String displayName, int defaultSimulationPriority) {
        this.displayName = displayName;
        this.defaultSimulationPriority = defaultSimulationPriority;
    }

    public String getDisplayName() {
        return displayName;
    }

    public int getDefaultSimulationPriority() {
        return defaultSimulationPriority;
    }
}
