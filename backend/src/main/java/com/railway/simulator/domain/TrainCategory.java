package com.railway.simulator.domain;

/**
 * Service category of a train, defining its stopping pattern and operational policy.
 */
public enum TrainCategory {
    SLOW_LOCAL("Slow Suburban Local"),
    FAST_LOCAL("Fast Suburban Local"),
    EXPRESS("Express Service"),
    MAIL("Mail Service"),
    SUPERFAST("Superfast Express"),
    PREMIUM("Premium Express (Rajdhani/Vande Bharat)"),
    FREIGHT("Freight / Goods Train"),
    SPECIAL("Special / Emergency Service");

    private final String displayName;

    TrainCategory(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
