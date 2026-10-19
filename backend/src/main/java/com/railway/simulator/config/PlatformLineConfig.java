package com.railway.simulator.config;

import com.railway.simulator.domain.LineType;

/**
 * Authoritative Platform-to-Line Type & Direction mapping configuration
 * across all 24 Western Railway simulation corridor stations.
 *
 * Rules:
 * - Every platform has a concrete line type: FAST_UP, FAST_DOWN, SLOW_UP, or SLOW_DOWN.
 * - Direction is strictly "UP" for UP lines and "DOWN" for DOWN lines.
 * - Station total platform counts are preserved.
 */
public final class PlatformLineConfig {

    private PlatformLineConfig() {}

    /**
     * Resolves the assigned LineType for any platform at any corridor station.
     */
    public static LineType getPlatformLineType(String stationCode, int platformNumber) {
        if (stationCode == null) return LineType.SLOW_UP;
        String stn = stationCode.toUpperCase();
        return switch (stn) {
            case "VR" -> switch (platformNumber) {
                case 1, 2 -> LineType.SLOW_UP;
                case 3, 4 -> LineType.SLOW_DOWN;
                case 5 -> LineType.FAST_UP;
                case 6 -> LineType.FAST_DOWN;
                default -> LineType.SLOW_UP;
            };
            case "NSP" -> switch (platformNumber) {
                case 1 -> LineType.SLOW_UP;
                case 2 -> LineType.SLOW_DOWN;
                case 3 -> LineType.FAST_UP;
                case 4 -> LineType.FAST_DOWN;
                default -> LineType.SLOW_UP;
            };
            case "BSR" -> switch (platformNumber) {
                case 1 -> LineType.SLOW_UP;
                case 2 -> LineType.SLOW_DOWN;
                case 3 -> LineType.SLOW_UP; // Loop line
                case 4 -> LineType.FAST_DOWN;
                case 5 -> LineType.FAST_UP;
                default -> LineType.SLOW_UP;
            };
            case "NIG" -> (platformNumber == 1) ? LineType.SLOW_UP : LineType.SLOW_DOWN;
            case "BYR" -> switch (platformNumber) {
                case 1, 2 -> LineType.SLOW_UP;
                case 3, 4 -> LineType.SLOW_DOWN;
                case 5 -> LineType.FAST_UP;
                case 6 -> LineType.FAST_DOWN;
                default -> LineType.SLOW_UP;
            };
            case "MIRA" -> switch (platformNumber) {
                case 1 -> LineType.SLOW_UP;
                case 2 -> LineType.SLOW_DOWN;
                case 3 -> LineType.FAST_UP;
                case 4 -> LineType.FAST_DOWN;
                default -> LineType.SLOW_UP;
            };
            case "DIC" -> switch (platformNumber) {
                case 1, 2 -> LineType.SLOW_UP;
                case 3, 4 -> LineType.SLOW_DOWN;
                default -> LineType.SLOW_UP;
            };
            case "BVI" -> switch (platformNumber) {
                case 1, 2 -> LineType.SLOW_UP;
                case 3, 4 -> LineType.SLOW_DOWN;
                case 5, 7 -> LineType.FAST_UP;
                case 6, 8 -> LineType.FAST_DOWN;
                default -> LineType.SLOW_UP;
            };
            case "KND" -> switch (platformNumber) {
                case 1, 2 -> LineType.SLOW_UP;
                case 3, 4 -> LineType.SLOW_DOWN;
                default -> LineType.SLOW_UP;
            };
            case "MLD" -> switch (platformNumber) {
                case 1 -> LineType.SLOW_UP;
                case 2 -> LineType.SLOW_DOWN;
                case 3 -> LineType.FAST_UP;
                case 4 -> LineType.FAST_DOWN;
                default -> LineType.SLOW_UP;
            };
            case "GMN" -> switch (platformNumber) {
                case 1 -> LineType.SLOW_UP;
                case 2 -> LineType.SLOW_DOWN;
                case 3 -> LineType.FAST_UP;
                case 4 -> LineType.FAST_DOWN;
                default -> LineType.SLOW_UP;
            };
            case "JOS" -> (platformNumber == 1) ? LineType.SLOW_UP : LineType.SLOW_DOWN;
            case "AND" -> switch (platformNumber) {
                case 1, 2 -> LineType.SLOW_UP;
                case 3, 4 -> LineType.SLOW_DOWN;
                case 5, 7 -> LineType.FAST_UP;
                case 6, 8 -> LineType.FAST_DOWN;
                default -> LineType.SLOW_UP;
            };
            case "VLP" -> (platformNumber == 1) ? LineType.SLOW_UP : LineType.SLOW_DOWN;
            case "STC" -> (platformNumber == 1) ? LineType.SLOW_UP : LineType.SLOW_DOWN;
            case "KHR" -> (platformNumber == 1) ? LineType.SLOW_UP : LineType.SLOW_DOWN;
            case "BA" -> switch (platformNumber) {
                case 1, 2 -> LineType.SLOW_UP;
                case 3, 4 -> LineType.SLOW_DOWN;
                case 5 -> LineType.FAST_UP;
                case 6 -> LineType.FAST_DOWN;
                default -> LineType.SLOW_UP;
            };
            case "MM" -> (platformNumber == 1) ? LineType.SLOW_UP : LineType.SLOW_DOWN;
            case "MRU" -> (platformNumber == 1) ? LineType.SLOW_UP : LineType.SLOW_DOWN;
            case "DDR" -> switch (platformNumber) {
                case 1, 2 -> LineType.SLOW_UP;
                case 3, 4 -> LineType.SLOW_DOWN;
                case 5, 7 -> LineType.FAST_UP;
                case 6 -> LineType.FAST_DOWN;
                default -> LineType.SLOW_UP;
            };
            case "PR" -> (platformNumber == 1) ? LineType.SLOW_UP : LineType.SLOW_DOWN;
            case "LP" -> (platformNumber == 1) ? LineType.SLOW_UP : LineType.SLOW_DOWN;
            case "MX" -> (platformNumber == 1) ? LineType.SLOW_UP : LineType.SLOW_DOWN;
            case "MMCT" -> switch (platformNumber) {
                case 1 -> LineType.SLOW_UP;
                case 2 -> LineType.SLOW_DOWN;
                case 3, 5 -> LineType.FAST_UP;
                case 4 -> LineType.FAST_DOWN;
                default -> LineType.FAST_UP;
            };
            default -> (platformNumber % 2 == 1) ? LineType.SLOW_UP : LineType.SLOW_DOWN;
        };
    }

    /**
     * Resolves the direction corresponding to a line type.
     */
    public static String getDirection(LineType lineType) {
        if (lineType == LineType.FAST_UP || lineType == LineType.SLOW_UP) {
            return "UP";
        }
        return "DOWN";
    }
}
