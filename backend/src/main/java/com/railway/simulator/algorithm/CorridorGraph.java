package com.railway.simulator.algorithm;

import com.railway.simulator.domain.BlockType;
import com.railway.simulator.domain.LineType;
import com.railway.simulator.domain.Signal;
import com.railway.simulator.domain.SignalAspect;
import com.railway.simulator.domain.TrackBlock;
import com.railway.simulator.domain.Train;
import com.railway.simulator.domain.TrainStatus;
import com.railway.simulator.domain.TrainCategory;

import java.util.*;

/**
 * Graph representation of the Western Railway corridor from Virar to Mumbai Central.
 *
 * Data Structure:
 * Adjacency list graph with weighted directed edges representing track connections.
 *
 * NOTE: Station names represent authentic Western Railway locations.
 * Distances (km), transit times (min), and track assignments are Configurable Simulation Parameters.
 *
 * Complexity:
 * - Station Lookup: O(1)
 * - Traversal / Pathfinding: O(V + E) BFS, O(V + E log V) Dijkstra
 * - Space: O(V + E)
 */
public class CorridorGraph {


    public static class StationNode {
        private final String code;
        private final String name;
        private final int sequenceOrder;
        private final boolean fastLineSupport;
        private final boolean expressSupport;
        private final boolean terminal;
        private final double latitude;
        private final double longitude;

        public StationNode(String code, String name, int sequenceOrder,
                           boolean fastLineSupport, boolean expressSupport, boolean terminal) {
            this(code, name, sequenceOrder, fastLineSupport, expressSupport, terminal, 0.0, 0.0);
        }

        public StationNode(String code, String name, int sequenceOrder,
                           boolean fastLineSupport, boolean expressSupport, boolean terminal,
                           double latitude, double longitude) {
            this.code = code;
            this.name = name;
            this.sequenceOrder = sequenceOrder;
            this.fastLineSupport = fastLineSupport;
            this.expressSupport = expressSupport;
            this.terminal = terminal;
            this.latitude = latitude;
            this.longitude = longitude;
        }

        public String getCode() { return code; }
        public String getName() { return name; }
        public int getSequenceOrder() { return sequenceOrder; }
        public boolean isFastLineSupport() { return fastLineSupport; }
        public boolean isExpressSupport() { return expressSupport; }
        public boolean isTerminal() { return terminal; }
        public double getLatitude() { return latitude; }
        public double getLongitude() { return longitude; }
        public int getNumberOfPlatforms() {
            return STATION_PLATFORM_COUNTS.getOrDefault(code, 4);
        }
    }

    private static final Map<String, Integer> STATION_PLATFORM_COUNTS = Map.ofEntries(
        Map.entry("VR", 6), Map.entry("NSP", 4), Map.entry("BSR", 5), Map.entry("NIG", 2),
        Map.entry("BYR", 6), Map.entry("MIRA", 4), Map.entry("DIC", 4), Map.entry("BVI", 8),
        Map.entry("KND", 4), Map.entry("MLD", 4), Map.entry("GMN", 4), Map.entry("JOS", 2),
        Map.entry("AND", 8), Map.entry("VLP", 2), Map.entry("STC", 2), Map.entry("KHR", 2),
        Map.entry("BA", 6), Map.entry("MM", 2), Map.entry("MRU", 2), Map.entry("DDR", 7),
        Map.entry("PR", 2), Map.entry("LP", 2), Map.entry("MX", 2), Map.entry("MMCT", 5)
    );

    public static class TrackEdge {
        private final String fromStationCode;
        private final String toStationCode;
        private final LineType trackType;
        private final double distanceKm;
        private final int transitMinutes;

        public TrackEdge(String fromStationCode, String toStationCode, LineType trackType,
                         double distanceKm, int transitMinutes) {
            this.fromStationCode = fromStationCode;
            this.toStationCode = toStationCode;
            this.trackType = trackType;
            this.distanceKm = distanceKm;
            this.transitMinutes = transitMinutes;
        }

        public String getFromStationCode() { return fromStationCode; }
        public String getToStationCode() { return toStationCode; }
        public LineType getTrackType() { return trackType; }
        public double getDistanceKm() { return distanceKm; }
        public int getTransitMinutes() { return transitMinutes; }
    }

    private final Map<String, StationNode> stations = new LinkedHashMap<>();
    private final Map<String, List<TrackEdge>> adjacencyList = new HashMap<>();
    private final Map<String, TrackBlock> corridorTrackBlocks = new LinkedHashMap<>();

    public CorridorGraph() {
        initializeStandardCorridor();
    }


    public void addStation(StationNode node) {
        stations.put(node.getCode(), node);
        adjacencyList.putIfAbsent(node.getCode(), new ArrayList<>());
    }

    public void addEdge(String from, String to, LineType lineType, double distanceKm, int transitMinutes) {
        if (!stations.containsKey(from) || !stations.containsKey(to)) {
            throw new IllegalArgumentException("Stations must exist before adding track edge: " + from + " -> " + to);
        }
        adjacencyList.get(from).add(new TrackEdge(from, to, lineType, distanceKm, transitMinutes));
    }

    public StationNode getStation(String code) {
        return stations.get(code);
    }

    public Map<String, StationNode> getStations() {
        return Collections.unmodifiableMap(stations);
    }

    public Collection<StationNode> getAllStations() {
        return stations.values();
    }

    public List<TrackEdge> getOutgoingEdges(String stationCode) {
        return adjacencyList.getOrDefault(stationCode, Collections.emptyList());
    }

    /**
     * Validates whether a consecutive route sequence is topologically connected along the corridor.
     */
    public boolean isValidRoute(List<String> routeStations, LineType lineType) {
        if (routeStations == null || routeStations.size() < 2) return false;

        for (int i = 0; i < routeStations.size() - 1; i++) {
            String current = routeStations.get(i);
            String next = routeStations.get(i + 1);

            List<TrackEdge> edges = adjacencyList.getOrDefault(current, Collections.emptyList());
            boolean connected = edges.stream().anyMatch(e ->
                    e.getToStationCode().equals(next) &&
                    (lineType == null || lineType == LineType.COMMON || e.getTrackType() == lineType || e.getTrackType() == LineType.COMMON)
            );
            if (!connected) return false;
        }
        return true;
    }

    /**
     * Calculates total transit time (in minutes) between two connected corridor stations.
     */
    public int calculateTransitMinutes(String sourceCode, String destCode, LineType trackType) {
        StationNode source = stations.get(sourceCode);
        StationNode dest = stations.get(destCode);
        if (source == null || dest == null) return 0;

        int stationsBetween = Math.abs(dest.getSequenceOrder() - source.getSequenceOrder());
        // Simulation assumption: ~3 min per station for Slow lines, ~2 min per station for Fast lines
        if (trackType == LineType.FAST_UP || trackType == LineType.FAST_DOWN) {
            return Math.max(5, stationsBetween * 2);
        }
        return Math.max(3, stationsBetween * 3);
    }

    /**
     * Initializes the default 24-station Western Railway simulation corridor.
     */
    private void initializeStandardCorridor() {
        // Station definitions (Virar to Mumbai Central, sequence 1 to 24)
        Object[][] stationData = {
            {"VR", "Virar", 1, true, true, true, 19.4538, 72.8113},
            {"NSP", "Nalasopara", 2, true, false, false, 19.4182, 72.8188},
            {"BSR", "Vasai Road", 3, true, true, false, 19.3804, 72.8335},
            {"NIG", "Naigaon", 4, false, false, false, 19.3518, 72.8486},
            {"BYR", "Bhayandar", 5, true, true, false, 19.3134, 72.8530},
            {"MIRA", "Mira Road", 6, true, false, false, 19.2810, 72.8566},
            {"DIC", "Dahisar", 7, false, false, false, 19.2562, 72.8596},
            {"BVI", "Borivali", 8, true, true, true, 19.2294, 72.8573},
            {"KND", "Kandivali", 9, false, false, false, 19.2045, 72.8523},
            {"MLD", "Malad", 10, true, false, false, 19.1868, 72.8486},
            {"GMN", "Goregaon", 11, true, false, false, 19.1643, 72.8488},
            {"JOS", "Jogeshwari", 12, false, false, false, 19.1352, 72.8493},
            {"AND", "Andheri", 13, true, true, true, 19.1197, 72.8468},
            {"VLP", "Vile Parle", 14, false, false, false, 19.0998, 72.8441},
            {"STC", "Santacruz", 15, false, false, false, 19.0827, 72.8417},
            {"KHR", "Khar Road", 16, false, false, false, 19.0699, 72.8398},
            {"BA", "Bandra", 17, true, true, true, 19.0553, 72.8406},
            {"MM", "Mahim", 18, false, false, false, 19.0409, 72.8436},
            {"MRU", "Matunga Road", 19, false, false, false, 19.0278, 72.8437},
            {"DDR", "Dadar", 20, true, true, true, 19.0185, 72.8427},
            {"PR", "Prabhadevi", 21, false, false, false, 19.0118, 72.8358},
            {"LP", "Lower Parel", 22, false, false, false, 18.9957, 72.8310},
            {"MX", "Mahalaxmi", 23, false, false, false, 18.9828, 72.8242},
            {"MMCT", "Mumbai Central", 24, true, true, true, 18.9696, 72.8194}
        };

        for (Object[] row : stationData) {
            addStation(new StationNode(
                (String) row[0],
                (String) row[1],
                (int) row[2],
                (boolean) row[3],
                (boolean) row[4],
                (boolean) row[5],
                (double) row[6],
                (double) row[7]
            ));
        }

        // Connect adjacent stations with directional Slow and Fast track edges
        List<StationNode> stationList = new ArrayList<>(stations.values());
        for (int i = 0; i < stationList.size() - 1; i++) {
            StationNode current = stationList.get(i);
            StationNode next = stationList.get(i + 1);

            double dist = 2.5; // Configurable simulation parameter: ~2.5 km average station spacing
            int slowTransit = 3; // ~3 mins
            int fastTransit = 2; // ~2 mins

            // Southbound (UP towards Mumbai Central)
            addEdge(current.getCode(), next.getCode(), LineType.SLOW_UP, dist, slowTransit);
            addEdge(current.getCode(), next.getCode(), LineType.FAST_UP, dist, fastTransit);

            // Northbound (DOWN towards Virar)
            addEdge(next.getCode(), current.getCode(), LineType.SLOW_DOWN, dist, slowTransit);
            addEdge(next.getCode(), current.getCode(), LineType.FAST_DOWN, dist, fastTransit);
        }

        corridorTrackBlocks.putAll(createCorridorTrackBlocks());
    }

    public String getLineCode(LineType lineType) {
        return switch (lineType) {
            case SLOW_UP -> "SU";
            case FAST_UP -> "FU";
            case SLOW_DOWN -> "SD";
            case FAST_DOWN -> "FD";
            case COMMON -> "CM";
        };
    }

    /**
     * Authoritative calculation of train route progress along the corridor.
     * Single source of truth for: lastStationCode, upcomingStationCode, and nextStopStationCode.
     *
     * Rules:
     * - UP direction means VR -> MMCT order; DOWN means MMCT -> VR order.
     * - AT_PLATFORM: last = that station, upcoming = null;
     *   nextStop = first station in the train's stop pattern AFTER it in the direction of travel.
     * - In an inter-station block: last = the block's from-station, upcoming = to-station;
     *   nextStop = first station in the stop pattern AT or AFTER upcoming in the direction of travel.
     * - Platform blocks (BLK-XXX-Pn): the station is XXX.
     * - nextStop must never be a station already passed.
     * - Freight/Special without a stop list: nextStop = final destination, or null if already passed.
     */
    public RouteProgress computeRouteProgress(Train train) {
        if (train == null) {
            return new RouteProgress("VR", null, "VR");
        }

        String dir = train.getDirection();
        if (dir == null || dir.isBlank()) {
            dir = determineTrainDirection(train);
        }
        boolean isUp = "UP".equalsIgnoreCase(dir);

        String blockId = train.getCurrentBlockId();
        TrackBlock block = blockId != null ? corridorTrackBlocks.get(blockId) : null;

        boolean isPlatform = train.getStatus() == TrainStatus.AT_PLATFORM
                || (block != null && block.getBlockType() == BlockType.PLATFORM)
                || (blockId != null && blockId.matches("BLK-[A-Za-z0-9]+-P\\d+"));

        String lastStation;
        String upcomingStation;
        String nextStop = null;

        if (isPlatform) {
            String platStation = null;
            if (block != null && block.getBlockType() == BlockType.PLATFORM) {
                platStation = block.getFromStationCode();
            } else if (blockId != null && blockId.startsWith("BLK-") && blockId.matches(".*-P\\d+")) {
                String[] parts = blockId.split("-");
                if (parts.length > 1) {
                    platStation = parts[1];
                }
            }
            if (platStation == null || platStation.isBlank()) {
                platStation = train.getCurrentStationCode() != null ? train.getCurrentStationCode() : (isUp ? "VR" : "MMCT");
            }

            lastStation = platStation;
            upcomingStation = null;

            StationNode curNode = stations.get(platStation);
            int curSeq = curNode != null ? curNode.getSequenceOrder() : (isUp ? 0 : 25);

            List<String> stops = train.getStopStations();
            if (stops != null && !stops.isEmpty()) {
                for (String stn : stops) {
                    StationNode sNode = stations.get(stn);
                    if (sNode == null) continue;
                    int sSeq = sNode.getSequenceOrder();
                    if (isUp && sSeq > curSeq) {
                        nextStop = stn;
                        break;
                    } else if (!isUp && sSeq < curSeq) {
                        nextStop = stn;
                        break;
                    }
                }
            }

            // Freight/Special without a stop list or at end of stops
            if (nextStop == null) {
                String dest = train.getDestinationStation();
                if (dest != null && !dest.isBlank()) {
                    StationNode destNode = stations.get(dest);
                    if (destNode != null) {
                        int destSeq = destNode.getSequenceOrder();
                        if (isUp && destSeq > curSeq) {
                            nextStop = dest;
                        } else if (!isUp && destSeq < curSeq) {
                            nextStop = dest;
                        }
                    } else {
                        // Destination outside corridor: never expose non-corridor station code
                        nextStop = null;
                    }
                }
            }
        } else {
            // Inter-station block
            String fromStation = null;
            String toStation = null;
            if (block != null) {
                fromStation = block.getFromStationCode();
                toStation = block.getToStationCode();
            } else if (blockId != null && blockId.startsWith("BLK-")) {
                String[] parts = blockId.split("-");
                if (parts.length > 2) {
                    fromStation = parts[1];
                    toStation = parts[2];
                }
            }
            if (fromStation == null) fromStation = isUp ? "VR" : "MMCT";
            if (toStation == null) toStation = isUp ? "NSP" : "VR";

            lastStation = fromStation;
            upcomingStation = toStation;

            StationNode upNode = stations.get(toStation);
            int upSeq = upNode != null ? upNode.getSequenceOrder() : (isUp ? 25 : 0);

            List<String> stops = train.getStopStations();
            if (stops != null && !stops.isEmpty()) {
                for (String stn : stops) {
                    StationNode sNode = stations.get(stn);
                    if (sNode == null) continue;
                    int sSeq = sNode.getSequenceOrder();
                    if (isUp && sSeq >= upSeq) {
                        nextStop = stn;
                        break;
                    } else if (!isUp && sSeq <= upSeq) {
                        nextStop = stn;
                        break;
                    }
                }
            }

            // Freight/Special without a stop list or reached end of stops
            if (nextStop == null) {
                String dest = train.getDestinationStation();
                if (dest != null && !dest.isBlank()) {
                    StationNode destNode = stations.get(dest);
                    if (destNode != null) {
                        int destSeq = destNode.getSequenceOrder();
                        if (isUp && destSeq >= upSeq) {
                            nextStop = dest;
                        } else if (!isUp && destSeq <= upSeq) {
                            nextStop = dest;
                        }
                    } else {
                        // Destination outside corridor: never expose non-corridor station code
                        nextStop = null;
                    }
                }
            }
        }

        return new RouteProgress(lastStation, upcomingStation, nextStop);
    }

    public String determineTrainDirection(Train t) {
        if (t == null) return "UP";
        if (t.getDirection() != null && !t.getDirection().isBlank()) {
            return t.getDirection();
        }
        String dest = t.getDestinationStation();
        if (dest != null) {
            String d = dest.toUpperCase();
            if (d.equals("CCG") || d.equals("MMCT") || d.equals("JNPT") || d.equals("BDTS")) {
                return "UP";
            }
            if (d.equals("VR") || d.equals("DRD") || d.equals("GNC") || d.equals("NDLS")
                    || d.equals("NZM") || d.equals("ADI") || d.equals("BHUJ") || d.equals("ST") || d.equals("ASR")) {
                return "DOWN";
            }
        }
        String src = t.getSourceStation();
        StationNode srcNode = src != null ? stations.get(src.toUpperCase()) : null;
        StationNode destNode = dest != null ? stations.get(dest.toUpperCase()) : null;
        if (srcNode != null && destNode != null) {
            return destNode.getSequenceOrder() > srcNode.getSequenceOrder() ? "UP" : "DOWN";
        }
        if ("VR".equalsIgnoreCase(src) || "BSR".equalsIgnoreCase(src) || "BYR".equalsIgnoreCase(src)) {
            return "UP";
        } else if ("MMCT".equalsIgnoreCase(src) || "CCG".equalsIgnoreCase(src) || "BDTS".equalsIgnoreCase(src)) {
            return "DOWN";
        }
        return "UP";
    }

    /**
     * Generates discrete simulation track blocks from the CorridorGraph topology.
     */
    public Map<String, TrackBlock> createCorridorTrackBlocks() {
        Map<String, TrackBlock> blocks = new LinkedHashMap<>();

        // Generate track blocks for all directed edges in the adjacency list
        for (Map.Entry<String, List<TrackEdge>> entry : adjacencyList.entrySet()) {
            for (TrackEdge edge : entry.getValue()) {
                String from = edge.getFromStationCode();
                String to = edge.getToStationCode();
                LineType lineType = edge.getTrackType();
                String blockId = "BLK-" + from + "-" + to + "-" + getLineCode(lineType);
                String direction = lineType.name().contains("UP") ? "UP" : "DOWN";

                TrackBlock block = new TrackBlock(blockId, from, to, lineType, BlockType.INTER_STATION, direction, edge.getDistanceKm());
                blocks.put(blockId, block);
            }
        }

        // Add platform blocks for all corridor stations from Virar to Mumbai Central
        for (StationNode node : stations.values()) {
            int pCount = node.getNumberOfPlatforms();
            for (int p = 1; p <= pCount; p++) {
                String pBlockId = "BLK-" + node.getCode() + "-P" + p;
                LineType pLine = com.railway.simulator.config.PlatformLineConfig.getPlatformLineType(node.getCode(), p);
                String direction = com.railway.simulator.config.PlatformLineConfig.getDirection(pLine);
                TrackBlock pBlock = new TrackBlock(pBlockId, node.getCode(), node.getCode(), pLine, BlockType.PLATFORM, direction, 0.6);

                // Set downstream departure nextBlockId matching the platform line
                List<TrackEdge> outEdges = adjacencyList.getOrDefault(node.getCode(), Collections.emptyList());
                for (TrackEdge edge : outEdges) {
                    if (edge.getTrackType() == pLine) {
                        pBlock.setNextBlockId("BLK-" + edge.getFromStationCode() + "-" + edge.getToStationCode() + "-" + getLineCode(edge.getTrackType()));
                        break;
                    }
                }

                blocks.put(pBlockId, pBlock);
            }
        }

        // Sequential chaining along each corridor line
        for (TrackBlock block : blocks.values()) {
            if (block.getBlockType() == BlockType.INTER_STATION) {
                String toStation = block.getToStationCode();
                LineType line = block.getLineType();
                List<TrackEdge> nextEdges = adjacencyList.getOrDefault(toStation, Collections.emptyList());

                TrackEdge chosenEdge = null;
                // 1. Try matching same line type first
                for (TrackEdge nextEdge : nextEdges) {
                    if (nextEdge.getTrackType() == line) {
                        chosenEdge = nextEdge;
                        break;
                    }
                }
                // 2. If no next block exists on the same line type, link to the same-direction block
                // of the other line type for that edge (fast<->slow crossover at stations)
                if (chosenEdge == null) {
                    LineType alternateLine = getAlternateSameDirectionLine(line);
                    if (alternateLine != null) {
                        for (TrackEdge nextEdge : nextEdges) {
                            if (nextEdge.getTrackType() == alternateLine) {
                                chosenEdge = nextEdge;
                                break;
                            }
                        }
                    }
                }

                if (chosenEdge != null) {
                    String nextBlockId = "BLK-" + chosenEdge.getFromStationCode() + "-" + chosenEdge.getToStationCode() + "-" + getLineCode(chosenEdge.getTrackType());
                    block.setNextBlockId(nextBlockId);
                }
            }
        }

        return blocks;
    }

    public LineType getAlternateSameDirectionLine(LineType line) {
        return switch (line) {
            case FAST_UP -> LineType.SLOW_UP;
            case SLOW_UP -> LineType.FAST_UP;
            case FAST_DOWN -> LineType.SLOW_DOWN;
            case SLOW_DOWN -> LineType.FAST_DOWN;
            case COMMON -> null;
        };
    }

    /**
     * Generates simulated signals protecting entry into each track block and platform line.
     */
    public Map<String, Signal> createCorridorSignals(Map<String, TrackBlock> blocks) {
        Map<String, Signal> signals = new LinkedHashMap<>();

        for (TrackBlock block : blocks.values()) {
            String sigId = "SIG-" + block.getBlockId().substring(4);
            String desc = block.getFromStationCode() + " " + block.getLineType() + " Section Entrance";
            if (block.getBlockType() == BlockType.PLATFORM) {
                desc = block.getFromStationCode() + " Platform " + block.getBlockId().substring(block.getBlockId().lastIndexOf('P')) + " Home Signal";
            }

            Signal signal = new Signal(sigId, block.getFromStationCode(), desc, block.getDirection(), block.getLineType(), block.getBlockId());

            if (block.getNextBlockId() != null) {
                signal.setNextSignalId("SIG-" + block.getNextBlockId().substring(4));
            }
            signal.setCurrentAspect(SignalAspect.GREEN);
            signal.setAspectReason("Route clear: protected block available");
            signals.put(sigId, signal);

            // Create Starter (Exit) Signal for each Platform Block
            if (block.getBlockType() == BlockType.PLATFORM) {
                String pNum = block.getBlockId().substring(block.getBlockId().lastIndexOf('P'));
                String starterSigId = "SIG-" + block.getBlockId().substring(4) + "-STARTER";
                String starterDesc = block.getFromStationCode() + " Platform " + pNum + " Starter Signal";
                String downstreamBlockId = block.getNextBlockId() != null ? block.getNextBlockId() : block.getBlockId();
                Signal starter = new Signal(starterSigId, block.getFromStationCode(), starterDesc,
                        block.getDirection(), block.getLineType(), downstreamBlockId);
                if (block.getNextBlockId() != null) {
                    starter.setNextSignalId("SIG-" + block.getNextBlockId().substring(4));
                }
                starter.setCurrentAspect(SignalAspect.RED);
                starter.setAspectReason("Starter signal at rest (train berthed or platform unoccupied)");
                signals.put(starterSigId, starter);
            }
        }

        return signals;
    }
}
