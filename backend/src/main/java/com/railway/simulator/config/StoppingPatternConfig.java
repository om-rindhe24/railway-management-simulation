package com.railway.simulator.config;

import com.railway.simulator.domain.LineType;
import com.railway.simulator.domain.TrainCategory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.function.BiPredicate;

/**
 * Authoritative stopping pattern policy and category defaults for the
 * Virar (VR) to Mumbai Central (MMCT) Western Railway simulation corridor.
 */
public class StoppingPatternConfig {

    private static final Logger log = LoggerFactory.getLogger(StoppingPatternConfig.class);

    // Corridor stations in Southbound (UP, Virar -> Mumbai Central) route order
    public static final List<String> CORRIDOR_STATIONS_UP = List.of(
            "VR", "NSP", "BSR", "NIG", "BYR", "MIRA", "DIC", "BVI",
            "KND", "MLD", "GMN", "JOS", "AND", "VLP", "STC", "KHR",
            "BA", "MM", "MRU", "DDR", "PR", "LP", "MX", "MMCT"
    );

    // Corridor stations in Northbound (DOWN, Mumbai Central -> Virar) route order
    public static final List<String> CORRIDOR_STATIONS_DN = List.of(
            "MMCT", "MX", "LP", "PR", "DDR", "MRU", "MM", "BA",
            "KHR", "STC", "VLP", "AND", "JOS", "GMN", "MLD", "KND",
            "BVI", "DIC", "MIRA", "BYR", "NIG", "BSR", "NSP", "VR"
    );

    // Default fast local stop stations (simulation assumption, editable)
    public static final List<String> FAST_LOCAL_CANDIDATE_STATIONS = List.of(
            "VR", "BSR", "BYR", "BVI", "AND", "BA", "DDR", "MMCT"
    );

    // Default intermediate express stops in corridor
    public static final List<String> EXPRESS_CORRIDOR_STOPS = List.of("BVI");

    // Terminal station codes in the corridor
    public static final Set<String> TERMINAL_STATIONS = Set.of("VR", "BVI", "AND", "BA", "DDR", "MMCT");

    /**
     * Returns the appropriate dwell duration in seconds for a station stop.
     */
    public static int getDwellSeconds(TrainCategory category, String stationCode, boolean isTerminal) {
        if (category == null) {
            category = TrainCategory.SLOW_LOCAL;
        }

        switch (category) {
            case SLOW_LOCAL:
                return 30; // 30s dwell at every station
            case FAST_LOCAL:
                // 45s at DDR, MMCT and other terminals, 30-35s at other stops
                if ("DDR".equalsIgnoreCase(stationCode) || "MMCT".equalsIgnoreCase(stationCode) || isTerminal || TERMINAL_STATIONS.contains(stationCode)) {
                    return 45;
                }
                return 30;
            case EXPRESS:
            case MAIL:
            case SUPERFAST:
            case PREMIUM:
                // 90-120 s (120s at terminals/major junctions, 90s intermediate)
                if (isTerminal || "MMCT".equalsIgnoreCase(stationCode) || "VR".equalsIgnoreCase(stationCode)) {
                    return 120;
                }
                return 90;
            case FREIGHT:
            case SPECIAL:
            default:
                return 0; // No passenger dwell
        }
    }

    /**
     * Determines whether a station is a corridor terminal or major reversal point.
     */
    public static boolean isTerminalStation(String stationCode) {
        return stationCode != null && TERMINAL_STATIONS.contains(stationCode.toUpperCase());
    }

    /**
     * Slices the corridor station sequence between source and destination according to travel direction.
     */
    public static List<String> getRouteSlice(String source, String destination, String direction) {
        List<String> fullRoute = "DOWN".equalsIgnoreCase(direction) ? CORRIDOR_STATIONS_DN : CORRIDOR_STATIONS_UP;

        String effectiveSource = source;
        String effectiveDest = destination;

        // Map external or terminal aliases
        if ("CCG".equalsIgnoreCase(destination) && !"DOWN".equalsIgnoreCase(direction)) {
            effectiveDest = "MMCT"; // Corresponds to corridor exit
        }
        if ("CCG".equalsIgnoreCase(source) && "DOWN".equalsIgnoreCase(direction)) {
            effectiveSource = "MMCT"; // Corresponds to corridor entry
        }

        int srcIdx = fullRoute.indexOf(effectiveSource);
        int dstIdx = fullRoute.indexOf(effectiveDest);

        if (srcIdx == -1) srcIdx = 0;
        if (dstIdx == -1) dstIdx = fullRoute.size() - 1;

        if (srcIdx <= dstIdx) {
            return new ArrayList<>(fullRoute.subList(srcIdx, dstIdx + 1));
        } else {
            return new ArrayList<>(fullRoute.subList(dstIdx, srcIdx + 1));
        }
    }

    /**
     * Generates the default stop list in route order for a train category between source and destination.
     */
    public static List<String> generateDefaultStopStations(TrainCategory category, String source, String destination, String direction) {
        List<String> routeSlice = getRouteSlice(source, destination, direction);
        if (routeSlice.isEmpty()) {
            return Collections.emptyList();
        }

        String firstStation = routeSlice.get(0);
        String lastStation = routeSlice.get(routeSlice.size() - 1);

        if (category == null) {
            category = TrainCategory.SLOW_LOCAL;
        }

        if (category == TrainCategory.FREIGHT) {
            return Collections.emptyList();
        }

        List<String> stops = new ArrayList<>();

        switch (category) {
            case SLOW_LOCAL:
                stops.addAll(routeSlice);
                break;

            case FAST_LOCAL:
                for (String stn : routeSlice) {
                    if (FAST_LOCAL_CANDIDATE_STATIONS.contains(stn) || stn.equals(firstStation) || stn.equals(lastStation)) {
                        stops.add(stn);
                    }
                }
                break;

            case EXPRESS:
            case MAIL:
            case SUPERFAST:
            case PREMIUM:
                for (String stn : routeSlice) {
                    if (stn.equals(firstStation) || stn.equals(lastStation) || EXPRESS_CORRIDOR_STOPS.contains(stn)) {
                        stops.add(stn);
                    }
                }
                break;

            case FREIGHT:
            case SPECIAL:
                stops.add(firstStation);
                if (!firstStation.equals(lastStation)) {
                    stops.add(lastStation);
                }
                break;
        }

        // Guarantee first and last stations are always stops
        if (!stops.contains(firstStation)) {
            stops.add(0, firstStation);
        }
        if (!stops.contains(lastStation)) {
            stops.add(lastStation);
        }

        // Preserve route slice ordering
        Set<String> stopSet = new LinkedHashSet<>(stops);
        List<String> orderedStops = new ArrayList<>();
        for (String stn : routeSlice) {
            if (stopSet.contains(stn)) {
                orderedStops.add(stn);
            }
        }
        return orderedStops;
    }

    /**
     * Validates stops against platform availability for the train's direction and line type.
     * Invalid stops (stations lacking a compatible platform) are removed and reported.
     *
     * @param proposedStops list of proposed station codes
     * @param category train category
     * @param direction "UP" or "DOWN"
     * @param trainNumber train identifier for logging/reporting
     * @param platformCompatibilityChecker predicate testing whether a station has a compatible platform
     * @return validated and filtered list of stops
     */
    public static List<String> validateAndFilterStops(
            List<String> proposedStops,
            TrainCategory category,
            String direction,
            String trainNumber,
            BiPredicate<String, LineType> platformCompatibilityChecker) {

        if (proposedStops == null || proposedStops.isEmpty()) {
            return Collections.emptyList();
        }

        boolean isFast = (category == TrainCategory.FAST_LOCAL
                || category == TrainCategory.EXPRESS
                || category == TrainCategory.MAIL
                || category == TrainCategory.SUPERFAST
                || category == TrainCategory.PREMIUM);

        LineType requiredLineType = "DOWN".equalsIgnoreCase(direction)
                ? (isFast ? LineType.FAST_DOWN : LineType.SLOW_DOWN)
                : (isFast ? LineType.FAST_UP : LineType.SLOW_UP);

        String firstStation = proposedStops.get(0);
        String lastStation = proposedStops.get(proposedStops.size() - 1);

        List<String> validStops = new ArrayList<>();

        for (String stationCode : proposedStops) {
            boolean isBoundary = stationCode.equalsIgnoreCase(firstStation) || stationCode.equalsIgnoreCase(lastStation);

            boolean hasPlatform = (platformCompatibilityChecker == null)
                    || platformCompatibilityChecker.test(stationCode, requiredLineType);

            if (hasPlatform || isBoundary) {
                validStops.add(stationCode);
            } else {
                log.warn("Removed invalid stop {} for train {} ({}): station lacks compatible platform for direction {} on {}",
                        stationCode, trainNumber, category, direction, requiredLineType);
                System.out.printf("STOPPING PATTERN VALIDATION: Removed invalid stop %s for train %s (%s) - no compatible platform for %s%n",
                        stationCode, trainNumber, category, requiredLineType);
            }
        }

        return validStops;
    }
}
