package com.railway.simulator.controller.validation;

import java.util.Set;

public final class StationValidationUtil {

    public static final Set<String> CORRIDOR_STATIONS = Set.of(
            "VR", "NSP", "BSR", "NIG", "BYR", "MIRA", "DIC", "BVI",
            "KND", "MLD", "GMN", "JOS", "AND", "VLP", "STC", "KHR",
            "BA", "MM", "MRU", "DDR", "PR", "LP", "MX", "MMCT"
    );

    private StationValidationUtil() {}

    public static void validateStationCode(String stationCode) {
        if (stationCode != null && !stationCode.isBlank()) {
            if (!CORRIDOR_STATIONS.contains(stationCode.trim().toUpperCase())) {
                throw new IllegalArgumentException("Unknown station code: '" + stationCode + "'. Must be one of the 24 corridor stations.");
            }
        }
    }

    public static boolean isValid(String stationCode) {
        return stationCode != null && CORRIDOR_STATIONS.contains(stationCode.trim().toUpperCase());
    }
}
