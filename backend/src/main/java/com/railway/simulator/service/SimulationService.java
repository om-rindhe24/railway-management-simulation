package com.railway.simulator.service;

import com.railway.simulator.algorithm.CorridorGraph;
import com.railway.simulator.algorithm.CorridorGraph.TrackEdge;
import com.railway.simulator.algorithm.RouteProgress;
import com.railway.simulator.algorithm.SignalStateEngine;
import com.railway.simulator.algorithm.dto.ConflictReport;
import com.railway.simulator.controller.dto.*;
import com.railway.simulator.domain.*;
import com.railway.simulator.repository.*;
import jakarta.annotation.PostConstruct;
import com.railway.simulator.config.PeakHourFleetConfig;
import com.railway.simulator.config.SignalSpeedConfig;
import com.railway.simulator.config.StoppingPatternConfig;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.Deque;
import java.util.stream.Collectors;

/**
 * Manages simulation clock, train movement progression, and educational signalling integration.
 * Acts as the authoritative backend source of truth for corridor simulation state.
 */
@Service
public class SimulationService {

    private LocalDateTime simulationTime = LocalDateTime.of(2026, 9, 18, 10, 0, 0);
    private boolean isRunning = false;
    private int speedMultiplier = 1;
    private boolean isPeakHour = false;
    private final Deque<PlatformStateSlice> platformHistory = new ConcurrentLinkedDeque<>();

    private final TrainRepository trainRepository;
    private final PlatformRepository platformRepository;
    private final SimulationEventRepository eventRepository;
    private final AllocationService allocationService;

    // Signalling Layer Engine and in-memory topology
    private final CorridorGraph corridorGraph = new CorridorGraph();
    private final SignalStateEngine signalStateEngine = new SignalStateEngine();
    private final Map<String, TrackBlock> trackBlocks = new ConcurrentHashMap<>();
    private final Map<String, Signal> signals = new ConcurrentHashMap<>();

    public SimulationService(TrainRepository trainRepository,
                             PlatformRepository platformRepository,
                             SimulationEventRepository eventRepository,
                             @org.springframework.context.annotation.Lazy AllocationService allocationService) {
        this.trainRepository = trainRepository;
        this.platformRepository = platformRepository;
        this.eventRepository = eventRepository;
        this.allocationService = allocationService;
        initializeSignallingTopology();
    }

    @PostConstruct
    public void init() {
        initializeSignallingTopology();
        recalculateSignalling();
    }

    private synchronized void initializeSignallingTopology() {
        if (trackBlocks.isEmpty()) {
            trackBlocks.putAll(corridorGraph.createCorridorTrackBlocks());
            signals.putAll(corridorGraph.createCorridorSignals(trackBlocks));
        }
    }

    public Map<String, TrackBlock> getTrackBlocks() {
        return trackBlocks;
    }

    public Map<String, Signal> getSignals() {
        return signals;
    }

    public synchronized LocalDateTime getSimulationTime() {
        return simulationTime;
    }

    public synchronized void setSimulationTime(LocalDateTime simulationTime) {
        this.simulationTime = simulationTime;
    }

    public synchronized boolean isRunning() {
        return isRunning;
    }

    public synchronized int getSpeedMultiplier() {
        return speedMultiplier;
    }

    public synchronized void startSimulation() {
        this.isRunning = true;
        recalculateSignalling();
        eventRepository.save(new SimulationEvent("SIMULATION_STARTED", Severity.INFO,
                "Simulation clock started at " + simulationTime, null, null, null));
    }

    public synchronized void pauseSimulation() {
        this.isRunning = false;
        eventRepository.save(new SimulationEvent("SIMULATION_PAUSED", Severity.INFO,
                "Simulation clock paused at " + simulationTime, null, null, null));
    }

    public synchronized void setSpeed(int multiplier) {
        if (multiplier == 1 || multiplier == 2 || multiplier == 5 || multiplier == 10) {
            this.speedMultiplier = multiplier;
        }
    }

    @Transactional
    public synchronized void resetSimulation() {
        this.isRunning = false;
        this.speedMultiplier = 1;
        this.simulationTime = LocalDateTime.of(2026, 9, 18, 10, 0, 0);
        this.isPeakHour = false;
        this.platformHistory.clear();

        // Reset track blocks
        for (TrackBlock tb : trackBlocks.values()) {
            tb.vacate();
            tb.setClosed(false);
        }

        // Reopen platforms
        List<Platform> platforms = platformRepository.findAll();
        for (Platform p : platforms) {
            p.setIsOccupied(false);
            p.setIsClosed(false);
            p.setCurrentTrainId(null);
            p.setCurrentTrainNumber(null);
            platformRepository.save(p);
        }

        // [blockId, progress, speed, status, waitingReason, src, dest, targetStation]
        Map<String, Object[]> baselineMap = new HashMap<>();
        baselineMap.put("SIM-LOC-901", new Object[]{"BLK-DIC-BVI-FU", 45.0, 65.0, TrainStatus.APPROACHING, null, "VR", "CCG", "BVI"});
        baselineMap.put("SIM-LOC-902", new Object[]{"BLK-BSR-NIG-SU", 50.0, 50.0, TrainStatus.APPROACHING, null, "VR", "CCG", "BVI"});
        baselineMap.put("SIM-VB-20901", new Object[]{"BLK-AND-JOS-FD", 30.0, 95.0, TrainStatus.APPROACHING, null, "MMCT", "GNC", "BVI"});
        baselineMap.put("SIM-RAJ-12951", new Object[]{"BLK-KHR-STC-FD", 60.0, 50.0, TrainStatus.APPROACHING, null, "MMCT", "NDLS", "BVI"});
        baselineMap.put("SIM-SF-12953", new Object[]{"BLK-DDR-P6", 0.0, 0.0, TrainStatus.AT_PLATFORM, null, "MMCT", "NZM", "DDR"});
        baselineMap.put("SIM-LOC-903", new Object[]{"BLK-VR-NSP-FU", 20.0, 70.0, TrainStatus.APPROACHING, null, "VR", "CCG", "AND"});
        baselineMap.put("SIM-EXP-19015", new Object[]{"BLK-KND-MLD-FU", 40.0, 60.0, TrainStatus.APPROACHING, null, "VR", "MMCT", "BA"});
        baselineMap.put("SIM-LOC-904", new Object[]{"BLK-BVI-P2", 0.0, 0.0, TrainStatus.AT_PLATFORM, null, "BVI", "CCG", "BVI"});
        baselineMap.put("SIM-SF-22927", new Object[]{"BLK-BA-P6", 0.0, 0.0, TrainStatus.AT_PLATFORM, null, "BDTS", "ADI", "BA"});
        baselineMap.put("SIM-FRT-8001", new Object[]{"BLK-NSP-BSR-SU", 80.0, 45.0, TrainStatus.APPROACHING, null, "VR", "JNPT", "VR"});
        baselineMap.put("SIM-EXP-12925", new Object[]{"BLK-AND-P6", 0.0, 0.0, TrainStatus.AT_PLATFORM, null, "MMCT", "ASR", "AND"});
        baselineMap.put("SIM-SPL-09001", new Object[]{"BLK-MIRA-DIC-SU", 98.0, 0.0, TrainStatus.WAITING, "Waiting at signal SIG-DIC-BVI-SU: Protected block BLK-DIC-BVI-SU is OCCUPIED by train SIM-LOC-902", "VR", "MMCT", "BVI"});
        baselineMap.put("SIM-LOC-905", new Object[]{"BLK-LP-MX-SU", 35.0, 55.0, TrainStatus.APPROACHING, null, "VR", "CCG", "MMCT"});
        baselineMap.put("SIM-LOC-906", new Object[]{"BLK-MRU-DDR-FU", 65.0, 50.0, TrainStatus.APPROACHING, null, "VR", "CCG", "DDR"});
        baselineMap.put("SIM-EMG-01", new Object[]{"BLK-MMCT-P5", 0.0, 0.0, TrainStatus.AT_PLATFORM, null, "VR", "MMCT", "MMCT"});
        baselineMap.put("SIM-EXP-12955", new Object[]{"BLK-NSP-BSR-FU", 60.0, 75.0, TrainStatus.APPROACHING, null, "ST", "MMCT", "BSR"});
        baselineMap.put("SIM-LOC-908", new Object[]{"BLK-BYR-P1", 0.0, 0.0, TrainStatus.AT_PLATFORM, null, "BYR", "CCG", "BYR"});
        baselineMap.put("SIM-LOC-909", new Object[]{"BLK-MLD-GMN-FU", 35.0, 65.0, TrainStatus.APPROACHING, null, "VR", "CCG", "MLD"});
        baselineMap.put("SIM-LOC-910", new Object[]{"BLK-GMN-P1", 0.0, 0.0, TrainStatus.AT_PLATFORM, null, "VR", "CCG", "GMN"});
        baselineMap.put("SIM-EXP-22903", new Object[]{"BLK-BA-KHR-FD", 45.0, 55.0, TrainStatus.APPROACHING, null, "BDTS", "BHUJ", "KHR"});
        baselineMap.put("SIM-LOC-912", new Object[]{"BLK-DDR-P1", 0.0, 0.0, TrainStatus.AT_PLATFORM, null, "VR", "CCG", "DDR"});
        baselineMap.put("SIM-SF-12951B", new Object[]{"BLK-MMCT-P4", 0.0, 0.0, TrainStatus.AT_PLATFORM, null, "MMCT", "ADI", "MMCT"});
        baselineMap.put("SIM-LOC-921", new Object[]{"BLK-MMCT-P2", 0.0, 0.0, TrainStatus.AT_PLATFORM, null, "MMCT", "VR", "MMCT"});
        baselineMap.put("SIM-LOC-922", new Object[]{"BLK-GMN-MLD-SD", 40.0, 50.0, TrainStatus.APPROACHING, null, "CCG", "VR", "MLD"});
        baselineMap.put("SIM-LOC-923", new Object[]{"BLK-MIRA-BYR-SD", 50.0, 50.0, TrainStatus.APPROACHING, null, "CCG", "VR", "BYR"});
        baselineMap.put("SIM-LOC-924", new Object[]{"BLK-BYR-P6", 0.0, 0.0, TrainStatus.AT_PLATFORM, null, "CCG", "VR", "BYR"});
        baselineMap.put("SIM-LOC-925", new Object[]{"BLK-BSR-NSP-FD", 40.0, 70.0, TrainStatus.APPROACHING, null, "CCG", "VR", "VR"});

        // Reset all platforms to baseline (open and unoccupied)
        for (Platform p : platformRepository.findAll()) {
            p.setIsClosed(false);
            p.setClosureReason(null);
            p.setIsOccupied(false);
            p.setCurrentTrainId(null);
            p.setCurrentTrainNumber(null);
            p.setOccupancyStart(null);
            p.setOccupancyEnd(null);
            platformRepository.save(p);
        }

        // Vacate all track blocks
        for (TrackBlock tb : trackBlocks.values()) {
            tb.vacate();
            tb.setClosed(false);
        }

        // Clean up any test-injected trains created during tests
        trainRepository.findAll().stream()
                .filter(t -> !baselineMap.containsKey(t.getTrainNumber()))
                .forEach(trainRepository::delete);

        // Reset train states
        List<Train> trains = trainRepository.findAll();
        for (Train t : trains) {
            t.setIsActive(true);
            t.setDelayMinutes(0);
            t.setActualArrival(t.getScheduledArrival());
            t.setActualDeparture(t.getScheduledDeparture());

            Object[] baseData = baselineMap.get(t.getTrainNumber());
            if (baseData != null) {
                String bId = (String) baseData[0];
                Double prog = (Double) baseData[1];
                Double spd = (Double) baseData[2];
                TrainStatus st = (TrainStatus) baseData[3];
                String reason = (String) baseData[4];
                String src = (String) baseData[5];
                String dest = (String) baseData[6];
                String tgt = (String) baseData[7];

                t.setSourceStation(src);
                t.setDestinationStation(dest);
                boolean isUpDest = "CCG".equalsIgnoreCase(dest) || "MMCT".equalsIgnoreCase(dest)
                        || "JNPT".equalsIgnoreCase(dest) || "BDTS".equalsIgnoreCase(dest);
                String dir = isUpDest ? "UP" : "DOWN";
                t.setDirection(dir);
                t.setTargetStationCode(tgt);
                List<String> defaultStops = StoppingPatternConfig.generateDefaultStopStations(t.getTrainCategory(), src, dest, dir);
                t.setStopStations(defaultStops);

                t.setCurrentBlockId(bId);
                t.setProgressPercent(prog);
                t.setSpeedKmH(spd);
                t.setStatus(st);
                t.setWaitingReason(reason);
                if (st == TrainStatus.WAITING) {
                    t.setSignalAspect(SignalAspect.RED);
                } else {
                    t.setSignalAspect(SignalAspect.GREEN);
                }

                if (bId != null && trackBlocks.containsKey(bId)) {
                    trackBlocks.get(bId).occupy(t.getTrainId(), t.getTrainNumber());
                }

                if (bId != null && bId.matches(".*-P\\d+")) {
                    int pIdx = bId.lastIndexOf("-P");
                    String stn = bId.substring(4, pIdx);
                    int pNum = Integer.parseInt(bId.substring(pIdx + 2));
                    t.setAssignedPlatformNumber(pNum);
                    t.setTargetStationCode(stn);
                    platformRepository.findByStationCodeAndPlatformNumber(stn, pNum).ifPresent(p -> {
                        t.setAssignedPlatformId(p.getPlatformId());
                        if (st == TrainStatus.AT_PLATFORM) {
                            p.setIsOccupied(true);
                            p.setCurrentTrainId(t.getTrainId());
                            p.setCurrentTrainNumber(t.getTrainNumber());
                            platformRepository.save(p);
                        }
                    });
                } else {
                    t.setAssignedPlatformNumber(null);
                    t.setAssignedPlatformId(null);
                }
            } else {
                t.setStatus(TrainStatus.SCHEDULED);
                t.setCurrentBlockId(null);
                t.setProgressPercent(0.0);
                t.setSpeedKmH(0.0);
                t.setWaitingReason(null);
            }
            RouteProgress progress = corridorGraph.computeRouteProgress(t);
            t.setLastStationCode(progress.getLastStationCode());
            t.setUpcomingStationCode(progress.getUpcomingStationCode());
            t.setCurrentStationCode(progress.getLastStationCode());
            t.setNextStopStationCode(progress.getNextStopStationCode());
            if (t.getStatus() != TrainStatus.AT_PLATFORM && (t.getCurrentBlockId() == null || !t.getCurrentBlockId().matches(".*-P\\d+"))) {
                t.setTargetStationCode(progress.getNextStopStationCode());
            }
            trainRepository.save(t);
        }

        recalculateSignalling();

        eventRepository.save(new SimulationEvent("SIMULATION_RESET", Severity.INFO,
                "Simulation reset to baseline at 10:00:00", null, null, null));
    }

    /**
     * Synchronizes platform states into corresponding platform track blocks and re-evaluates all signals.
     */
    @Transactional
    public synchronized void recalculateSignalling() {
        List<Platform> platforms = platformRepository.findAll();
        Map<String, Platform> platformMap = new HashMap<>();

        for (Platform p : platforms) {
            String code = p.getStationCode();
            if (code != null) {
                String pBlockId = "BLK-" + code + "-P" + p.getPlatformNumber();
                platformMap.put(pBlockId, p);

                TrackBlock tb = trackBlocks.get(pBlockId);
                if (tb != null) {
                    tb.setClosed(p.getIsClosed());
                    tb.setOccupied(p.getIsOccupied());
                    tb.setOccupyingTrainNumber(p.getCurrentTrainNumber());
                    tb.setOccupyingTrainId(p.getCurrentTrainId());
                }
            }
        }

        signalStateEngine.evaluateAllSignals(signals.values(), trackBlocks, signals, platformMap, simulationTime);
    }

    /**
     * Internal simulation clock pulse executed once per second.
     */
    @Scheduled(fixedRate = 1000)
    public void onClockTick() {
        if (!isRunning) return;
        advanceTrainStates(Math.max(5L, speedMultiplier * 5L));
    }

    /**
     * Authoritative synchronization of platform states, track blocks, signals, and train movements.
     * Called whenever platform availability changes, trains are delayed, or reallocations occur.
     */
    @Transactional
    public synchronized void syncSignallingState() {
        recalculateSignalling();

        List<Train> trains = trainRepository.findByIsActiveTrue();
        Set<String> claimedStationPlatforms = new HashSet<>();
        for (Train ot : trains) {
            if (ot.getTargetStationCode() != null && ot.getAssignedPlatformNumber() != null
                    && (ot.getStatus() == TrainStatus.AT_PLATFORM || ot.getStatus() == TrainStatus.APPROACHING || ot.getStatus() == TrainStatus.WAITING)) {
                claimedStationPlatforms.add(ot.getTargetStationCode().toUpperCase() + "-P" + ot.getAssignedPlatformNumber());
            }
        }
        for (Train train : trains) {
            if (train.getStatus() == TrainStatus.DEPARTED || train.getStatus() == TrainStatus.SCHEDULED) {
                continue;
            }

            String targetStation = train.getTargetStationCode();
            Integer assignedPlatNum = train.getAssignedPlatformNumber();
            Long assignedPlatId = train.getAssignedPlatformId();

            String homeSignalId = (targetStation != null && assignedPlatNum != null)
                    ? "SIG-" + targetStation + "-P" + assignedPlatNum
                    : null;
            Signal homeSignal = homeSignalId != null ? signals.get(homeSignalId) : null;

            String currentBlockId = train.getCurrentBlockId();
            TrackBlock currentBlock = currentBlockId != null ? trackBlocks.get(currentBlockId) : null;
            String nextBlockId = currentBlock != null ? currentBlock.getNextBlockId() : null;
            String nextSigId = nextBlockId != null ? "SIG-" + nextBlockId.substring(4) : null;
            Signal nextSig = nextSigId != null ? signals.get(nextSigId) : null;

            boolean stopsAtStation = train.stopsAtStation(targetStation);

            boolean isEnteringStation = currentBlock != null && targetStation != null &&
                    currentBlock.getToStationCode().equalsIgnoreCase(targetStation) && stopsAtStation;

            boolean platformClosedOrOccupied = false;
            String platformReason = null;
            if (assignedPlatId != null) {
                Platform p = platformRepository.findById(assignedPlatId).orElse(null);
                if (p != null) {
                    if (p.getIsClosed()) {
                        platformClosedOrOccupied = true;
                        platformReason = "platform is CLOSED" + (p.getClosureReason() != null ? ": " + p.getClosureReason() : "");
                    } else if (p.getIsOccupied() && p.getCurrentTrainId() != null && !p.getCurrentTrainId().equals(train.getTrainId())) {
                        platformClosedOrOccupied = true;
                        platformReason = "platform is OCCUPIED by train " + (p.getCurrentTrainNumber() != null ? p.getCurrentTrainNumber() : "another train");
                    }
                }
            }

            Signal aheadSignal = (isEnteringStation && homeSignal != null) ? homeSignal : nextSig;
            String aheadSignalId = (isEnteringStation && homeSignal != null) ? homeSignalId : nextSigId;

            boolean isAheadRed = (aheadSignal != null && aheadSignal.getCurrentAspect() == SignalAspect.RED) ||
                    (isEnteringStation && platformClosedOrOccupied);

            if (isAheadRed) {
                if (train.getStatus() == TrainStatus.APPROACHING || train.getStatus() == TrainStatus.WAITING) {
                    boolean wasWaiting = train.getStatus() == TrainStatus.WAITING;
                    train.setStatus(TrainStatus.WAITING);
                    train.setSignalAspect(SignalAspect.RED);
                    train.setSpeedKmH(0.0);
                    train.setNextSignalId(aheadSignalId != null ? aheadSignalId : "SIG-AUTO");
                    String waitMsg = platformReason != null ? platformReason :
                            (aheadSignal != null && aheadSignal.getAspectReason() != null ? aheadSignal.getAspectReason() : "block ahead occupied");
                    train.setWaitingReason(String.format("Waiting at signal %s: %s", aheadSignalId != null ? aheadSignalId : "SIG-AUTO", waitMsg));
                    trainRepository.save(train);
                    if (!wasWaiting) {
                        eventRepository.save(new SimulationEvent("TRAIN_HELD", Severity.WARNING,
                                String.format("Train %s HELD at %s: %s", train.getTrainNumber(), aheadSignalId != null ? aheadSignalId : "SIG-AUTO", waitMsg),
                                train.getTrainNumber(), train.getAssignedPlatformNumber(), train.getTargetStationCode()));
                    }
                }
            } else {
                if (train.getStatus() == TrainStatus.WAITING) {
                    train.setStatus(TrainStatus.APPROACHING);
                    train.setSignalAspect(aheadSignal != null ? aheadSignal.getCurrentAspect() : SignalAspect.GREEN);
                    train.setWaitingReason(null);
                    train.setNextSignalId(aheadSignalId);
                    trainRepository.save(train);
                }
            }
        }
    }

    @Transactional
    public void advanceTrainStates() {
        advanceTrainStates(Math.max(5L, speedMultiplier * 5L));
    }

    @Transactional
    public void advanceTrainStates(long secondsAdvanced) {
        synchronized (this) {
            simulationTime = simulationTime.plusSeconds(secondsAdvanced);
        }
        recalculateSignalling();

        List<Train> trains = trainRepository.findByIsActiveTrue();

        // 1. Clean up stale platform block occupancies
        for (TrackBlock tb : trackBlocks.values()) {
            if (tb.getBlockType() == BlockType.PLATFORM && tb.isOccupied()) {
                String occTrainNum = tb.getOccupyingTrainNumber();
                boolean stillValid = trains.stream().anyMatch(t ->
                        t.getTrainNumber().equals(occTrainNum) &&
                        t.getStatus() == TrainStatus.AT_PLATFORM &&
                        tb.getBlockId().equals("BLK-" + t.getTargetStationCode() + "-P" + t.getAssignedPlatformNumber())
                );
                if (!stillValid) {
                    tb.vacate();
                }
            }
        }

        // 2. Ensure platform entity occupancy in database matches active trains
        for (Platform p : platformRepository.findAll()) {
            if (p.getIsOccupied() && p.getCurrentTrainNumber() != null) {
                String platTrainNum = p.getCurrentTrainNumber();
                boolean stillValid = trains.stream().anyMatch(t ->
                        t.getTrainNumber().equals(platTrainNum) &&
                        t.getStatus() == TrainStatus.AT_PLATFORM &&
                        t.getAssignedPlatformId() != null &&
                        t.getAssignedPlatformId().equals(p.getPlatformId())
                );
                if (!stillValid) {
                    p.setIsOccupied(false);
                    p.setCurrentTrainId(null);
                    p.setCurrentTrainNumber(null);
                    platformRepository.save(p);
                }
            }
        }

        recalculateSignalling();

        Set<String> claimedStationPlatforms = new HashSet<>();
        for (Train ot : trains) {
            if (ot.getTargetStationCode() != null && ot.getAssignedPlatformNumber() != null
                    && (ot.getStatus() == TrainStatus.AT_PLATFORM || ot.getStatus() == TrainStatus.APPROACHING || ot.getStatus() == TrainStatus.WAITING)) {
                claimedStationPlatforms.add(ot.getTargetStationCode().toUpperCase() + "-P" + ot.getAssignedPlatformNumber());
            }
        }

        for (Train train : trains) {
            if (train.getStatus() == TrainStatus.DEPARTED) {
                if (train.getActualDeparture() == null || !simulationTime.isBefore(train.getActualDeparture().plusSeconds(30))) {
                    executeTrainTurnaround(train);
                }
                continue;
            }

            LocalDateTime arr = train.getActualArrival();
            LocalDateTime dep = train.getActualDeparture();
            String targetStation = train.getTargetStationCode();
            Integer assignedPlatNum = train.getAssignedPlatformNumber();
            Long assignedPlatId = train.getAssignedPlatformId();

            String targetPlatformBlockId = (targetStation != null && assignedPlatNum != null)
                    ? "BLK-" + targetStation + "-P" + assignedPlatNum
                    : null;
            String homeSignalId = (targetStation != null && assignedPlatNum != null)
                    ? "SIG-" + targetStation + "-P" + assignedPlatNum
                    : null;

            Signal homeSignal = homeSignalId != null ? signals.get(homeSignalId) : null;
            String currentBlockId = train.getCurrentBlockId();
            TrackBlock currentBlock = currentBlockId != null ? trackBlocks.get(currentBlockId) : null;

            // Status Machine & Continuous Movement
            boolean platformClosedOrOccupied = false;
            if (assignedPlatId != null) {
                Platform p = platformRepository.findById(assignedPlatId).orElse(null);
                if (p != null) {
                    if (p.getIsClosed()) {
                        platformClosedOrOccupied = true;
                    } else if (p.getIsOccupied() && p.getCurrentTrainId() != null && !p.getCurrentTrainId().equals(train.getTrainId())) {
                        platformClosedOrOccupied = true;
                    }
                }
            }

            if (currentBlockId == null && simulationTime.isBefore(arr)) {
                // Scheduled in the future
                train.setStatus(TrainStatus.SCHEDULED);
                train.setProgressPercent(0.0);
                train.setSpeedKmH(0.0);
                train.setWaitingReason(null);
            } else if (train.getStatus() == TrainStatus.AT_PLATFORM) {
                // Synchronize platform block with assigned platform at the same station
                String curBlk = train.getCurrentBlockId();
                if (targetPlatformBlockId != null && curBlk != null && curBlk.startsWith("BLK-") && curBlk.contains("-P")) {
                    String curStn = curBlk.split("-")[1];
                    if (targetPlatformBlockId.startsWith("BLK-" + curStn + "-P") && !targetPlatformBlockId.equals(curBlk)) {
                        if (currentBlock != null && train.getTrainId().equals(currentBlock.getOccupyingTrainId())) {
                            currentBlock.vacate();
                        }
                        train.setCurrentBlockId(targetPlatformBlockId);
                        currentBlockId = targetPlatformBlockId;
                        currentBlock = trackBlocks.get(currentBlockId);
                    }
                }

                // Train berthed at platform: completes dwell and departs normally even if platform was closed
                if (dep != null && !simulationTime.isBefore(dep)) {
                    // Train departure after scheduled dwell
                    // Terminal check: train departs from its final destination inside corridor, or corridor end (MMCT for UP, VR for DOWN)
                    String trainDest = train.getDestinationStation() != null ? train.getDestinationStation().toUpperCase() : "";
                    String tgtUpper = targetStation != null ? targetStation.toUpperCase() : "";
                    String trainDir = determineTrainDirection(train);
                    boolean isDestInsideCorridor = corridorGraph.getStations().containsKey(trainDest);

                    List<String> stops = train.getStopStations();
                    boolean isAtFinalDest;
                    if (stops != null && !stops.isEmpty()) {
                        String lastStop = stops.get(stops.size() - 1);
                        isAtFinalDest = lastStop.equalsIgnoreCase(tgtUpper);
                    } else if (isDestInsideCorridor && trainDest.equalsIgnoreCase(tgtUpper)) {
                        isAtFinalDest = true;
                    } else if ("UP".equalsIgnoreCase(trainDir) && "MMCT".equalsIgnoreCase(tgtUpper)) {
                        isAtFinalDest = true;
                    } else if ("DOWN".equalsIgnoreCase(trainDir) && "VR".equalsIgnoreCase(tgtUpper)) {
                        isAtFinalDest = true;
                    } else {
                        isAtFinalDest = false;
                    }

                    if (isAtFinalDest) {
                        train.setStatus(TrainStatus.DEPARTED);
                        if (targetPlatformBlockId != null) {
                            train.setCurrentBlockId(targetPlatformBlockId);
                        }
                        train.setProgressPercent(100.0);
                        train.setSpeedKmH(0.0);
                        train.setSignalAspect(null);
                        train.setNextSignalId(null);
                        train.setWaitingReason("Terminated at " + targetStation + "; service completed");
                        train.setActualDeparture(simulationTime);
                        if (currentBlock != null) currentBlock.vacate();
                        if (assignedPlatId != null) {
                            platformRepository.findById(assignedPlatId).ifPresent(p -> {
                                p.setIsOccupied(false);
                                p.setCurrentTrainId(null);
                                p.setCurrentTrainNumber(null);
                                platformRepository.save(p);
                            });
                        }
                        eventRepository.save(new SimulationEvent("PLATFORM_RELEASED", Severity.INFO,
                                String.format("Platform %s at %s RELEASED by train %s upon termination", assignedPlatNum != null ? assignedPlatNum : "?", targetStation, train.getTrainNumber()),
                                train.getTrainNumber(), assignedPlatNum, targetStation));
                    } else {
                        // Through train continues along corridor to next scheduled stop
                        String departStation = (currentBlock != null && currentBlock.getFromStationCode() != null) ? currentBlock.getFromStationCode() : targetStation;
                        String departBlockId = currentBlock != null ? currentBlock.getNextBlockId() : null;
                        if (departBlockId == null && departStation != null) {
                            List<TrackEdge> edges = corridorGraph.getOutgoingEdges(departStation);
                            boolean isFast = (train.getTrainCategory() == TrainCategory.FAST_LOCAL
                                    || train.getTrainCategory() == TrainCategory.EXPRESS
                                    || train.getTrainCategory() == TrainCategory.MAIL
                                    || train.getTrainCategory() == TrainCategory.SUPERFAST
                                    || train.getTrainCategory() == TrainCategory.PREMIUM);
                            LineType preferredLine = isFast ?
                                    ("UP".equalsIgnoreCase(trainDir) ? LineType.FAST_UP : LineType.FAST_DOWN) :
                                    ("UP".equalsIgnoreCase(trainDir) ? LineType.SLOW_UP : LineType.SLOW_DOWN);
                            LineType fallbackLine = "UP".equalsIgnoreCase(trainDir) ? LineType.SLOW_UP : LineType.SLOW_DOWN;
                            for (TrackEdge e : edges) {
                                if (e.getTrackType() == preferredLine) {
                                    departBlockId = "BLK-" + e.getFromStationCode() + "-" + e.getToStationCode() + "-" + corridorGraph.getLineCode(e.getTrackType());
                                    break;
                                }
                            }
                            if (departBlockId == null) {
                                for (TrackEdge e : edges) {
                                    if (e.getTrackType() == fallbackLine) {
                                        departBlockId = "BLK-" + e.getFromStationCode() + "-" + e.getToStationCode() + "-" + corridorGraph.getLineCode(e.getTrackType());
                                        break;
                                    }
                                }
                            }
                        }

                        TrackBlock depBlk = departBlockId != null ? trackBlocks.get(departBlockId) : null;
                        boolean isDepartBlockBlocked = depBlk != null && (depBlk.isClosed() || (depBlk.isOccupied() && !train.getTrainId().equals(depBlk.getOccupyingTrainId())));

                        String starterSigId = (targetStation != null && assignedPlatNum != null)
                                ? "SIG-" + targetStation + "-P" + assignedPlatNum + "-STARTER"
                                : null;
                        Signal starterSig = starterSigId != null ? signals.get(starterSigId) : null;
                        boolean isStarterRed = (starterSig != null && starterSig.getCurrentAspect() == SignalAspect.RED) || isDepartBlockBlocked;

                        if (isStarterRed) {
                            // Hold at platform until starter signal clears
                            train.setStatus(TrainStatus.AT_PLATFORM);
                            train.setSpeedKmH(0.0);
                            train.setProgressPercent(50.0);
                            train.setSignalAspect(SignalAspect.RED);
                            train.setNextSignalId(starterSigId);
                            train.setWaitingReason("Held at platform: starter signal red, block ahead occupied");
                            if (currentBlock != null) currentBlock.occupy(train.getTrainId(), train.getTrainNumber());
                        } else {
                            // Starter signal is permissive!
                            // If train is still stopped with RED aspect, observe the permissive starter signal
                            // at the platform berth BEFORE moving on the next tick.
                            if (train.getSignalAspect() == SignalAspect.RED) {
                                train.setStatus(TrainStatus.AT_PLATFORM);
                                train.setSpeedKmH(0.0);
                                train.setProgressPercent(50.0);
                                train.setSignalAspect(starterSig != null ? starterSig.getCurrentAspect() : SignalAspect.GREEN);
                                train.setNextSignalId(starterSigId);
                                train.setWaitingReason("Starter signal cleared (" + train.getSignalAspect() + "); ready for departure");
                                if (currentBlock != null) currentBlock.occupy(train.getTrainId(), train.getTrainNumber());
                            } else {
                                if (departBlockId == null && departStation != null) {
                                    List<TrackEdge> edges = corridorGraph.getOutgoingEdges(departStation);
                                    boolean isFast = (train.getTrainCategory() == TrainCategory.FAST_LOCAL
                                            || train.getTrainCategory() == TrainCategory.EXPRESS
                                            || train.getTrainCategory() == TrainCategory.MAIL
                                            || train.getTrainCategory() == TrainCategory.SUPERFAST
                                            || train.getTrainCategory() == TrainCategory.PREMIUM);
                                    LineType preferredLine = isFast ?
                                            ("UP".equalsIgnoreCase(trainDir) ? LineType.FAST_UP : LineType.FAST_DOWN) :
                                            ("UP".equalsIgnoreCase(trainDir) ? LineType.SLOW_UP : LineType.SLOW_DOWN);
                                    LineType fallbackLine = "UP".equalsIgnoreCase(trainDir) ? LineType.SLOW_UP : LineType.SLOW_DOWN;
                                    for (TrackEdge e : edges) {
                                        if (e.getTrackType() == preferredLine) {
                                            departBlockId = "BLK-" + e.getFromStationCode() + "-" + e.getToStationCode() + "-" + corridorGraph.getLineCode(e.getTrackType());
                                            break;
                                        }
                                    }
                                    if (departBlockId == null) {
                                        for (TrackEdge e : edges) {
                                            if (e.getTrackType() == fallbackLine) {
                                                departBlockId = "BLK-" + e.getFromStationCode() + "-" + e.getToStationCode() + "-" + corridorGraph.getLineCode(e.getTrackType());
                                                break;
                                            }
                                        }
                                    }
                                }

                                String nextTarget = getNextScheduledStop(train, departStation, trainDir);
                                train.setTargetStationCode(nextTarget);
                                train.setNextStopStationCode(nextTarget);

                                if (currentBlock != null) currentBlock.vacate();
                                if (assignedPlatId != null) {
                                    platformRepository.findById(assignedPlatId).ifPresent(p -> {
                                        p.setIsOccupied(false);
                                        p.setCurrentTrainId(null);
                                        p.setCurrentTrainNumber(null);
                                        platformRepository.save(p);
                                    });
                                }
                                eventRepository.save(new SimulationEvent("PLATFORM_RELEASED", Severity.INFO,
                                        String.format("Platform %s at %s RELEASED by train %s upon departure", assignedPlatNum != null ? assignedPlatNum : "?", targetStation, train.getTrainNumber()),
                                        train.getTrainNumber(), assignedPlatNum, targetStation));

                                train.setAssignedPlatformId(null);
                                train.setAssignedPlatformNumber(null);
                                train.setStatus(TrainStatus.APPROACHING);
                                train.setProgressPercent(0.0);
                                train.setSpeedKmH(0.0);
                                train.setSignalAspect(starterSig != null ? starterSig.getCurrentAspect() : SignalAspect.GREEN);
                                train.setNextSignalId(starterSig != null ? starterSig.getNextSignalId() : null);
                                train.setWaitingReason(null);

                                if (departBlockId != null && trackBlocks.containsKey(departBlockId)) {
                                    train.setCurrentBlockId(departBlockId);
                                    TrackBlock newBlk = trackBlocks.get(departBlockId);
                                    newBlk.occupy(train.getTrainId(), train.getTrainNumber());
                                    train.setCurrentStationCode(newBlk.getFromStationCode());
                                    train.setNextStationCode(newBlk.getToStationCode());
                                }
                            }
                        }
                    }
                } else {
                    // Still at platform dwell
                    train.setStatus(TrainStatus.AT_PLATFORM);
                    train.setProgressPercent(50.0);
                    train.setSpeedKmH(0.0);
                    String starterSigId = (targetStation != null && assignedPlatNum != null)
                            ? "SIG-" + targetStation + "-P" + assignedPlatNum + "-STARTER"
                            : null;
                    Signal starterSig = starterSigId != null ? signals.get(starterSigId) : null;
                    train.setSignalAspect(starterSig != null ? starterSig.getCurrentAspect() : SignalAspect.RED);
                    train.setNextSignalId(starterSigId);
                    train.setWaitingReason(null);
                    if (currentBlock != null) currentBlock.occupy(train.getTrainId(), train.getTrainNumber());
                    if (assignedPlatId != null) {
                        platformRepository.findById(assignedPlatId).ifPresent(p -> {
                            p.setIsOccupied(true);
                            p.setCurrentTrainId(train.getTrainId());
                            p.setCurrentTrainNumber(train.getTrainNumber());
                            platformRepository.save(p);
                        });
                    }
                }
            } else {
                // Train in transit on track block or approaching station platform
                String nextBlockId = currentBlock != null ? currentBlock.getNextBlockId() : null;
                String nextSigId = nextBlockId != null ? "SIG-" + nextBlockId.substring(4) : null;
                Signal nextSig = nextSigId != null ? signals.get(nextSigId) : null;

                String trainDir = determineTrainDirection(train);
                boolean stopsAtStation = train.stopsAtStation(targetStation);

                boolean isInsidePlatformBlock = currentBlock != null && currentBlock.getBlockType() == BlockType.PLATFORM;
                boolean isEnteringStation = !isInsidePlatformBlock && currentBlock != null && targetStation != null &&
                        currentBlock.getToStationCode().equalsIgnoreCase(targetStation) && stopsAtStation;

                String platformReason = null;
                // Dynamic platform allocation / reallocation on approach
                if (isEnteringStation && (assignedPlatId == null || platformClosedOrOccupied)) {
                    List<Platform> stationPlatforms = platformRepository.findByStationCodeOrderByPlatformNumberAsc(targetStation);
                    List<Platform> candidatePlatforms = new ArrayList<>();
                    for (Platform cand : stationPlatforms) {
                        if (Boolean.TRUE.equals(cand.getIsClosed()) || Boolean.TRUE.equals(cand.getIsOccupied())) {
                            continue;
                        }
                        String pbId = "BLK-" + targetStation + "-P" + cand.getPlatformNumber();
                        TrackBlock pb = trackBlocks.get(pbId);
                        if (pb != null && pb.isOccupied()) {
                            continue;
                        }
                        if (cand.getSupportedTrainTypes() != null && !cand.getSupportedTrainTypes().isEmpty()
                                && !cand.getSupportedTrainTypes().contains(train.getTrainType())) {
                            continue;
                        }
                        String pDir = cand.getDirection();
                        if (pDir != null && !"COMMON".equalsIgnoreCase(pDir) && !pDir.equalsIgnoreCase(trainDir)) {
                            continue;
                        }
                        String candKey = targetStation.toUpperCase() + "-P" + cand.getPlatformNumber();
                        if (claimedStationPlatforms.contains(candKey)) {
                            continue;
                        }
                        candidatePlatforms.add(cand);
                    }

                    boolean isFast = (train.getTrainCategory() == TrainCategory.FAST_LOCAL
                            || train.getTrainCategory() == TrainCategory.EXPRESS
                            || train.getTrainCategory() == TrainCategory.MAIL
                            || train.getTrainCategory() == TrainCategory.SUPERFAST
                            || train.getTrainCategory() == TrainCategory.PREMIUM);

                    // Pass 1: Strict line type match per PlatformLineConfig (same direction and line type)
                    Platform compatibleFree = null;
                    for (Platform cand : candidatePlatforms) {
                        boolean isFastPlat = (cand.getLineType() == LineType.FAST_UP || cand.getLineType() == LineType.FAST_DOWN);
                        if (cand.getLineType() == LineType.COMMON || isFast == isFastPlat) {
                            compatibleFree = cand;
                            break;
                        }
                    }

                    // Pass 2: Fallback to opposite line type in same direction if no primary platform exists
                    if (compatibleFree == null && !candidatePlatforms.isEmpty()) {
                        compatibleFree = candidatePlatforms.get(0);
                    }

                    if (compatibleFree != null) {
                        Integer oldP = assignedPlatNum;
                        if (oldP != null) {
                            claimedStationPlatforms.remove(targetStation.toUpperCase() + "-P" + oldP);
                        }
                        claimedStationPlatforms.add(targetStation.toUpperCase() + "-P" + compatibleFree.getPlatformNumber());
                        train.setAssignedPlatformId(compatibleFree.getPlatformId());
                        train.setAssignedPlatformNumber(compatibleFree.getPlatformNumber());
                        assignedPlatId = compatibleFree.getPlatformId();
                        assignedPlatNum = compatibleFree.getPlatformNumber();
                        targetPlatformBlockId = "BLK-" + targetStation + "-P" + compatibleFree.getPlatformNumber();
                        homeSignalId = "SIG-" + targetStation + "-P" + compatibleFree.getPlatformNumber();
                        homeSignal = signals.get(homeSignalId);
                        train.setNextSignalId(homeSignalId);
                        if (homeSignal != null) {
                            homeSignal.setCurrentAspect(SignalAspect.GREEN);
                            homeSignal.setAspectReason("Platform " + compatibleFree.getPlatformNumber() + " route clear");
                        }
                        platformClosedOrOccupied = false;

                        if (oldP != null && !oldP.equals(compatibleFree.getPlatformNumber())) {
                            String reallocMsg = String.format("Reallocated %s from %s P%s to P%s",
                                    train.getTrainNumber(), targetStation, oldP, compatibleFree.getPlatformNumber());
                            eventRepository.save(new SimulationEvent("TRAIN_REALLOCATED", Severity.WARNING, reallocMsg,
                                    train.getTrainNumber(), compatibleFree.getPlatformNumber(), targetStation));
                        }
                    } else {
                        platformClosedOrOccupied = true;
                        platformReason = "all compatible platforms at " + targetStation + " are occupied or closed";
                    }
                }

                Signal aheadSignal = (isEnteringStation && homeSignal != null) ? homeSignal : nextSig;
                String aheadSignalId = (isEnteringStation && homeSignal != null) ? homeSignalId : nextSigId;

                boolean isAheadRed = (aheadSignal != null && aheadSignal.getCurrentAspect() == SignalAspect.RED) ||
                        (isEnteringStation && platformClosedOrOccupied);

                double currentProgress = train.getProgressPercent() != null ? train.getProgressPercent() : 0.0;
                double currentSpeed = train.getSpeedKmH() != null ? train.getSpeedKmH() : 0.0;
                double blockKm = (currentBlock != null && currentBlock.getLengthKm() > 0) ? currentBlock.getLengthKm() : 2.5;

                String waitSig = aheadSignalId != null ? aheadSignalId : (homeSignalId != null ? homeSignalId : "SIG-AUTO");
                String aspectReason = aheadSignal != null ? aheadSignal.getAspectReason() : null;

                String occTrainNum = "another train";
                if (assignedPlatId != null) {
                    Platform ap = platformRepository.findById(assignedPlatId).orElse(null);
                    if (ap != null && ap.getCurrentTrainNumber() != null) {
                        occTrainNum = ap.getCurrentTrainNumber();
                    }
                }
                String waitReason = (aspectReason != null) ? aspectReason :
                        (isEnteringStation && platformClosedOrOccupied ? String.format("%s P%s occupied by %s", targetStation, assignedPlatNum != null ? assignedPlatNum : "?", occTrainNum) : "block ahead occupied");
                String reasonText = String.format("%s held at %s: %s", train.getTrainNumber(), waitSig, waitReason);

                // Resume moving if train was WAITING and signal ahead is now clear
                if (train.getStatus() == TrainStatus.WAITING && !isAheadRed) {
                    train.setStatus(TrainStatus.APPROACHING);
                    train.setWaitingReason(null);
                }

                // If train is already held at RED signal
                if (train.getStatus() == TrainStatus.WAITING && isAheadRed) {
                    train.setSpeedKmH(0.0);
                    train.setProgressPercent(SignalSpeedConfig.STOP_PROGRESS_PERCENT);
                    train.setSignalAspect(SignalAspect.RED);
                    train.setNextSignalId(waitSig);
                    train.setWaitingReason(reasonText);
                    if (currentBlock != null) currentBlock.occupy(train.getTrainId(), train.getTrainNumber());
                } else {
                    // Sub-step the physics in 1-second steps inside each tick
                    for (int step = 0; step < secondsAdvanced; step++) {
                        if (isAheadRed) {
                            double stopTarget = SignalSpeedConfig.STOP_PROGRESS_PERCENT;
                            double dRemMeters = Math.max(0.0, (stopTarget - currentProgress) / 100.0 * blockKm * 1000.0);

                            if (currentProgress >= stopTarget || dRemMeters <= 0.0) {
                                currentProgress = stopTarget;
                                currentSpeed = 0.0;
                                boolean wasWaiting = train.getStatus() == TrainStatus.WAITING;
                                train.setStatus(TrainStatus.WAITING);
                                train.setSpeedKmH(0.0);
                                train.setProgressPercent(stopTarget);
                                train.setSignalAspect(SignalAspect.RED);
                                train.setNextSignalId(waitSig);
                                train.setWaitingReason(reasonText);
                                if (!wasWaiting) {
                                    eventRepository.save(new SimulationEvent("TRAIN_HELD", Severity.WARNING,
                                            String.format("Train %s HELD at %s: %s", train.getTrainNumber(), waitSig, waitReason),
                                            train.getTrainNumber(), assignedPlatNum, targetStation));
                                }
                                break;
                            }

                            // Compute braking distance required to stop from current speed: d = v^2 / (2 * a)
                            double vMS = currentSpeed / 3.6;
                            double dBrakeMeters = (vMS * vMS) / (2.0 * SignalSpeedConfig.BRAKING_M_S2);

                            double targetSpeed;
                            if (dRemMeters <= dBrakeMeters) {
                                targetSpeed = 0.0;
                            } else {
                                targetSpeed = SignalSpeedConfig.SPEED_YELLOW;
                            }

                            if (currentSpeed < targetSpeed) {
                                currentSpeed = Math.min(targetSpeed, currentSpeed + SignalSpeedConfig.ACCEL_KMH_PER_SEC);
                            } else if (currentSpeed > targetSpeed) {
                                currentSpeed = Math.max(targetSpeed, currentSpeed - SignalSpeedConfig.BRAKE_KMH_PER_SEC);
                            }

                            double distKm = (currentSpeed / 3600.0);
                            double deltaPct = (distKm / blockKm) * 100.0;
                            currentProgress = Math.min(stopTarget, currentProgress + deltaPct);

                            if (currentProgress >= stopTarget || (dRemMeters <= 2.0 && currentSpeed <= 0.5)) {
                                currentProgress = stopTarget;
                                currentSpeed = 0.0;
                                boolean wasWaiting = train.getStatus() == TrainStatus.WAITING;
                                train.setStatus(TrainStatus.WAITING);
                                train.setSpeedKmH(0.0);
                                train.setProgressPercent(stopTarget);
                                train.setSignalAspect(SignalAspect.RED);
                                train.setNextSignalId(waitSig);
                                train.setWaitingReason(reasonText);
                                if (!wasWaiting) {
                                    eventRepository.save(new SimulationEvent("TRAIN_HELD", Severity.WARNING,
                                            String.format("Train %s HELD at %s: %s", train.getTrainNumber(), waitSig, waitReason),
                                            train.getTrainNumber(), assignedPlatNum, targetStation));
                                }
                                break;
                            }

                            train.setStatus(TrainStatus.APPROACHING);
                            train.setSpeedKmH(currentSpeed);
                            train.setProgressPercent(currentProgress);
                            train.setSignalAspect(SignalAspect.RED);
                            train.setNextSignalId(aheadSignalId);
                            train.setWaitingReason(null);
                        } else {
                            // Ahead signal is permissive (GREEN, DOUBLE_YELLOW, or YELLOW)
                            SignalAspect aspect = aheadSignal != null ? aheadSignal.getCurrentAspect() : SignalAspect.GREEN;
                            double trainMax = SignalSpeedConfig.getMaxSpeed(train, currentBlock);
                            double targetSpeed = SignalSpeedConfig.getAspectSpeedLimit(aspect, trainMax);

                            double dRemToStationMeters = (currentBlock != null && blockKm > 0)
                                    ? Math.max(0.0, (100.0 - currentProgress) / 100.0 * blockKm * 1000.0) : 0.0;

                            if (isEnteringStation) {
                                // Smooth station approach: stopping train brakes when within braking distance of platform entry
                                double vEntryMS = 14.0 / 3.6; // 14 km/h entry speed ceiling (<= 15 km/h)
                                double curVMS = currentSpeed / 3.6;
                                double dBrakeToEntryMeters = Math.max(0.0, (curVMS * curVMS - vEntryMS * vEntryMS) / (2.0 * SignalSpeedConfig.BRAKING_M_S2)) * 1.5 + 40.0;
                                if (dRemToStationMeters <= dBrakeToEntryMeters) {
                                    targetSpeed = Math.min(targetSpeed, 14.0);
                                }
                            } else if (isInsidePlatformBlock) {
                                targetSpeed = 0.0;
                            }

                            double stepStartSpeed = currentSpeed;
                            if (currentSpeed < targetSpeed) {
                                currentSpeed = Math.min(targetSpeed, currentSpeed + SignalSpeedConfig.ACCEL_KMH_PER_SEC);
                            } else if (currentSpeed > targetSpeed) {
                                currentSpeed = Math.max(targetSpeed, currentSpeed - SignalSpeedConfig.BRAKE_KMH_PER_SEC);
                            }

                            double distKm = (currentSpeed / 3600.0);
                            double deltaPct = (distKm / blockKm) * 100.0;
                            double newProgress = currentProgress + deltaPct;

                            TrackBlock targetPBlock = targetPlatformBlockId != null ? trackBlocks.get(targetPlatformBlockId) : null;
                            boolean pBlockFree = targetPBlock != null && (!targetPBlock.isOccupied() || train.getTrainId().equals(targetPBlock.getOccupyingTrainId()));
                            boolean entersPlatformNow = isEnteringStation && targetPlatformBlockId != null && trackBlocks.containsKey(targetPlatformBlockId)
                                    && pBlockFree
                                    && (newProgress >= 100.0 || (dRemToStationMeters <= 25.0 && currentSpeed <= 15.0));

                            if (isInsidePlatformBlock) {
                                currentProgress = Math.min(50.0, newProgress);
                                if (currentSpeed <= 0.0) {
                                    currentSpeed = 0.0;
                                    train.setStatus(TrainStatus.AT_PLATFORM);
                                    train.setProgressPercent(50.0);
                                    train.setSpeedKmH(0.0);
                                    train.setSignalAspect(homeSignal != null ? homeSignal.getCurrentAspect() : SignalAspect.GREEN);
                                    train.setNextSignalId(null);
                                    train.setWaitingReason(null);
                                    int dwellSec = StoppingPatternConfig.getDwellSeconds(train.getTrainCategory(), targetStation, StoppingPatternConfig.isTerminalStation(targetStation));
                                    if (train.getActualDeparture() != null && train.getActualDeparture().isAfter(simulationTime)) {
                                        dwellSec = (int) java.time.Duration.between(simulationTime, train.getActualDeparture()).getSeconds();
                                    } else {
                                        train.setActualDeparture(simulationTime.plusSeconds(dwellSec));
                                    }
                                    train.setActualArrival(simulationTime);
                                    final int finalDwellSec = dwellSec;
                                    if (assignedPlatId != null) {
                                        platformRepository.findById(assignedPlatId).ifPresent(p -> {
                                            p.setIsOccupied(true);
                                            p.setCurrentTrainId(train.getTrainId());
                                            p.setCurrentTrainNumber(train.getTrainNumber());
                                            p.setOccupancyStart(simulationTime);
                                            p.setOccupancyEnd(simulationTime.plusSeconds(finalDwellSec));
                                            platformRepository.save(p);
                                        });
                                    }
                                    eventRepository.save(new SimulationEvent("PLATFORM_OCCUPIED", Severity.INFO,
                                            String.format("Platform %d at %s OCCUPIED by train %s (dwell: %ds)", assignedPlatNum, targetStation, train.getTrainNumber(), finalDwellSec),
                                            train.getTrainNumber(), assignedPlatNum, targetStation));
                                    break;
                                } else {
                                    train.setStatus(TrainStatus.APPROACHING);
                                    train.setProgressPercent(currentProgress);
                                    train.setSpeedKmH(currentSpeed);
                                    train.setSignalAspect(homeSignal != null ? homeSignal.getCurrentAspect() : SignalAspect.GREEN);
                                }
                            } else if (entersPlatformNow) {
                                // Enter platform block at low speed (<= 15 km/h)
                                if (currentBlock != null) currentBlock.vacate();
                                TrackBlock pBlock = trackBlocks.get(targetPlatformBlockId);
                                if (pBlock != null) pBlock.occupy(train.getTrainId(), train.getTrainNumber());
                                train.setCurrentBlockId(targetPlatformBlockId);
                                currentBlock = pBlock;
                                currentBlockId = targetPlatformBlockId;
                                train.setCurrentStationCode(targetStation);
                                isEnteringStation = false;
                                isInsidePlatformBlock = true;
                                currentProgress = 0.0;
                                blockKm = (pBlock.getLengthKm() > 0) ? pBlock.getLengthKm() : 0.8;

                                double brakeApplied = Math.max(0.0, stepStartSpeed - currentSpeed);
                                double maxBrake = secondsAdvanced * SignalSpeedConfig.BRAKE_KMH_PER_SEC;
                                double remainingBrakeAllowed = Math.max(0.0, maxBrake - brakeApplied);
                                if (remainingBrakeAllowed > 0.0) {
                                    currentSpeed = Math.max(0.0, currentSpeed - remainingBrakeAllowed);
                                }

                                if (currentSpeed <= 0.0) {
                                    currentSpeed = 0.0;
                                    train.setStatus(TrainStatus.AT_PLATFORM);
                                    train.setProgressPercent(50.0);
                                    train.setSpeedKmH(0.0);
                                    train.setSignalAspect(homeSignal != null ? homeSignal.getCurrentAspect() : SignalAspect.GREEN);
                                    train.setNextSignalId(null);
                                    train.setWaitingReason(null);
                                    int dwellSec = StoppingPatternConfig.getDwellSeconds(train.getTrainCategory(), targetStation, StoppingPatternConfig.isTerminalStation(targetStation));
                                    if (train.getActualDeparture() != null && train.getActualDeparture().isAfter(simulationTime)) {
                                        dwellSec = (int) java.time.Duration.between(simulationTime, train.getActualDeparture()).getSeconds();
                                    } else {
                                        train.setActualDeparture(simulationTime.plusSeconds(dwellSec));
                                    }
                                    train.setActualArrival(simulationTime);
                                    final int finalDwellSec = dwellSec;
                                    if (assignedPlatId != null) {
                                        platformRepository.findById(assignedPlatId).ifPresent(p -> {
                                            p.setIsOccupied(true);
                                            p.setCurrentTrainId(train.getTrainId());
                                            p.setCurrentTrainNumber(train.getTrainNumber());
                                            p.setOccupancyStart(simulationTime);
                                            p.setOccupancyEnd(simulationTime.plusSeconds(finalDwellSec));
                                            platformRepository.save(p);
                                        });
                                    }
                                    eventRepository.save(new SimulationEvent("PLATFORM_OCCUPIED", Severity.INFO,
                                            String.format("Platform %d at %s OCCUPIED by train %s (dwell: %ds)", assignedPlatNum, targetStation, train.getTrainNumber(), finalDwellSec),
                                            train.getTrainNumber(), assignedPlatNum, targetStation));
                                    break;
                                } else {
                                    train.setStatus(TrainStatus.APPROACHING);
                                    train.setProgressPercent(currentProgress);
                                    train.setSpeedKmH(currentSpeed);
                                    train.setSignalAspect(homeSignal != null ? homeSignal.getCurrentAspect() : SignalAspect.GREEN);
                                }
                            } else if (newProgress >= 100.0) {
                                if (nextBlockId != null && trackBlocks.containsKey(nextBlockId)) {
                                    // Move to next inter-station block
                                    if (currentBlock != null) currentBlock.vacate();
                                    TrackBlock nextBlock = trackBlocks.get(nextBlockId);
                                    nextBlock.occupy(train.getTrainId(), train.getTrainNumber());
                                    train.setCurrentBlockId(nextBlockId);
                                    currentBlock = nextBlock;
                                    currentBlockId = nextBlockId;
                                    double carryover = Math.min(95.0, (newProgress - 100.0) * (blockKm / (nextBlock.getLengthKm() > 0 ? nextBlock.getLengthKm() : blockKm)));
                                    currentProgress = carryover;
                                    train.setProgressPercent(carryover);
                                    train.setStatus(TrainStatus.APPROACHING);
                                    train.setCurrentStationCode(nextBlock.getFromStationCode());
                                    train.setNextStationCode(nextBlock.getToStationCode());
                                    if (targetStation == null || (!train.stopsAtStation(targetStation) && !targetStation.equalsIgnoreCase(train.getDestinationStation()))) {
                                        targetStation = getNextScheduledStop(train, nextBlock.getFromStationCode(), trainDir);
                                        train.setTargetStationCode(targetStation);
                                        train.setNextStopStationCode(targetStation);
                                    }
                                    blockKm = (nextBlock.getLengthKm() > 0) ? nextBlock.getLengthKm() : 2.5;

                                    // Refresh downstream lookahead for the newly entered block
                                    nextBlockId = currentBlock.getNextBlockId();
                                    nextSigId = nextBlockId != null ? "SIG-" + nextBlockId.substring(4) : null;
                                    nextSig = nextSigId != null ? signals.get(nextSigId) : null;
                                    isEnteringStation = targetStation != null && currentBlock.getToStationCode().equalsIgnoreCase(targetStation) && stopsAtStation;
                                    aheadSignal = (isEnteringStation && homeSignal != null) ? homeSignal : nextSig;
                                    aheadSignalId = (isEnteringStation && homeSignal != null) ? homeSignalId : nextSigId;
                                    isAheadRed = (aheadSignal != null && aheadSignal.getCurrentAspect() == SignalAspect.RED) ||
                                            (isEnteringStation && platformClosedOrOccupied);
                                    train.setSignalAspect(aheadSignal != null ? aheadSignal.getCurrentAspect() : SignalAspect.GREEN);
                                    train.setNextSignalId(aheadSignalId);
                                    train.setWaitingReason(null);
                                } else {
                                    // End of corridor route
                                    if (currentBlock != null) currentBlock.vacate();
                                    train.setStatus(TrainStatus.DEPARTED);
                                    // Keep currentBlockId so it has a valid drawable position at terminal for 30s
                                    train.setProgressPercent(100.0);
                                    train.setSpeedKmH(0.0);
                                    train.setActualDeparture(simulationTime);
                                    train.setWaitingReason("Reached end of corridor");
                                    break;
                                }
                            } else {
                                currentProgress = newProgress;
                                train.setProgressPercent(currentProgress);
                                train.setSpeedKmH(currentSpeed);
                                train.setStatus(TrainStatus.APPROACHING);
                                train.setSignalAspect(aheadSignal != null ? aheadSignal.getCurrentAspect() : SignalAspect.GREEN);
                                train.setNextSignalId(aheadSignalId);
                                train.setWaitingReason(null);
                                if (currentBlock != null) currentBlock.occupy(train.getTrainId(), train.getTrainNumber());
                            }
                        }
                    }
                }
            }

            RouteProgress progress = corridorGraph.computeRouteProgress(train);
            train.setLastStationCode(progress.getLastStationCode());
            train.setUpcomingStationCode(progress.getUpcomingStationCode());
            train.setCurrentStationCode(progress.getLastStationCode());
            train.setNextStopStationCode(progress.getNextStopStationCode());
            if (train.getStatus() != TrainStatus.AT_PLATFORM && (train.getCurrentBlockId() == null || !train.getCurrentBlockId().matches(".*-P\\d+"))) {
                train.setTargetStationCode(progress.getNextStopStationCode());
            }
            trainRepository.save(train);
        }

        recalculateSignalling();
    }

    /**
     * Executes the ready-to-run demonstration scenario for placement viva.
     */
    @Transactional
    public String runDemoScenario() {
        resetSimulation();
        allocationService.runStationAllocation("BVI");

        // Find an incoming express train to delay
        List<Train> bviTrains = trainRepository.findByTargetStationCode("BVI");
        Train targetExpress = bviTrains.stream()
                .filter(t -> t.getTrainType() == TrainType.SUPERFAST || t.getTrainType() == TrainType.EXPRESS_MAIL)
                .findFirst()
                .orElse(bviTrains.get(0));

        // Inject 15-minute delay to trigger dynamic reallocation
        allocationService.handleTrainDelay(targetExpress.getTrainId(), 15, "Demo Scenario: Signal failure between Dahisar and Borivali");

        startSimulation();
        return String.format("Demo Scenario initiated: Allocated Borivali platforms, injected 15-min delay on Train %s, and executed dynamic reallocation.", targetExpress.getTrainNumber());
    }

    /**
     * Produces the central authoritative snapshot of the entire corridor for live visualization.
     */
    @Transactional(readOnly = true)
    public synchronized CorridorStateDto getCorridorState() {
        List<Train> trains = trainRepository.findByIsActiveTrue();
        List<Platform> platforms = platformRepository.findAll();
        List<ConflictReport> conflicts = (allocationService != null)
                ? allocationService.detectActiveConflicts(null)
                : Collections.emptyList();

        List<TrainSnapshotDto> trainSnapshots = trains.stream()
                .map(this::toTrainSnapshot)
                .collect(Collectors.toList());

        List<TrackBlockSnapshotDto> blockSnapshots = trackBlocks.values().stream()
                .map(this::toBlockSnapshot)
                .collect(Collectors.toList());

        List<SignalSnapshotDto> signalSnapshots = signals.values().stream()
                .map(this::toSignalSnapshot)
                .collect(Collectors.toList());

        List<PlatformSnapshotDto> platformSnapshots = platforms.stream()
                .map(this::toPlatformSnapshot)
                .collect(Collectors.toList());

        return new CorridorStateDto(
                simulationTime,
                isRunning,
                speedMultiplier,
                trainSnapshots,
                blockSnapshots,
                signalSnapshots,
                platformSnapshots,
                conflicts
        );
    }

    private TrainSnapshotDto toTrainSnapshot(Train t) {
        TrainSnapshotDto dto = new TrainSnapshotDto();
        dto.setTrainId(t.getTrainId());
        dto.setTrainNumber(t.getTrainNumber());
        dto.setTrainName(t.getTrainName());
        dto.setTrainType(t.getTrainType());
        dto.setStatus(t.getStatus());
        dto.setSourceStation(t.getSourceStation());
        dto.setDestinationStation(t.getDestinationStation());
        dto.setStationCode(t.getStationCode());
        RouteProgress progress = corridorGraph.computeRouteProgress(t);
        dto.setCurrentStationCode(progress.getLastStationCode());
        dto.setLastStationCode(progress.getLastStationCode());
        dto.setUpcomingStationCode(progress.getUpcomingStationCode());
        dto.setCurrentStationDisplay(progress.getCurrentStationDisplay());
        dto.setTargetStationCode(t.getTargetStationCode());
        dto.setNextStopStationCode(progress.getNextStopStationCode());
        dto.setCurrentBlockId(t.getCurrentBlockId());
        dto.setNextSignalId(t.getNextSignalId());
        dto.setSignalAspect(t.getSignalAspect());
        dto.setWaitingReason(t.getWaitingReason());
        dto.setDelayMinutes(t.getDelayMinutes());
        dto.setAssignedPlatform(t.getAssignedPlatformNumber());
        dto.setAssignedPlatformId(t.getAssignedPlatformId());
        dto.setActualArrival(t.getActualArrival());
        dto.setActualDeparture(t.getActualDeparture());
        dto.setScheduledArrival(t.getScheduledArrival());
        dto.setScheduledDeparture(t.getScheduledDeparture());
        dto.setProgressPercent(t.getProgressPercent() != null ? t.getProgressPercent() : 0.0);
        dto.setSpeedKmH(t.getSpeedKmH() != null ? t.getSpeedKmH() : 0.0);

        String dir = determineTrainDirection(t);
        dto.setDirection(dir);
        dto.setTrainCategory(t.getTrainCategory());
        dto.setStopStations(t.getStopStations());
        List<String> corridorRoute = "DOWN".equalsIgnoreCase(dir) ? StoppingPatternConfig.CORRIDOR_STATIONS_DN : StoppingPatternConfig.CORRIDOR_STATIONS_UP;
        dto.setSkipStations(t.getSkipStations(corridorRoute));
        return dto;
    }

    public CorridorGraph getCorridorGraph() {
        return corridorGraph;
    }

    public String getNextScheduledStop(Train train, String currentStation, String trainDir) {
        if (train == null) return "MMCT";
        List<String> stops = train.getStopStations();
        if (stops == null || stops.isEmpty()) {
            boolean isDestInside = corridorGraph.getStations().containsKey(train.getDestinationStation());
            return isDestInside ? train.getDestinationStation() : ("UP".equalsIgnoreCase(trainDir) ? "MMCT" : "VR");
        }
        int curIdx = -1;
        for (int i = 0; i < stops.size(); i++) {
            if (stops.get(i).equalsIgnoreCase(currentStation)) {
                curIdx = i;
                break;
            }
        }
        if (curIdx >= 0 && curIdx < stops.size() - 1) {
            return stops.get(curIdx + 1);
        }
        List<String> corridor = "DOWN".equalsIgnoreCase(trainDir)
                ? StoppingPatternConfig.CORRIDOR_STATIONS_DN
                : StoppingPatternConfig.CORRIDOR_STATIONS_UP;
        int stnPos = corridor.indexOf(currentStation);
        if (stnPos >= 0) {
            for (int i = stnPos + 1; i < corridor.size(); i++) {
                String cand = corridor.get(i);
                if (train.stopsAtStation(cand)) {
                    return cand;
                }
            }
        }
        return stops.get(stops.size() - 1);
    }

    public String determineTrainDirection(Train t) {
        if (t == null) return "UP";
        String src = t.getSourceStation();
        String dest = t.getDestinationStation();
        boolean isUpDest = "CCG".equalsIgnoreCase(dest) || "MMCT".equalsIgnoreCase(dest)
                || "JNPT".equalsIgnoreCase(dest) || "BDTS".equalsIgnoreCase(dest);
        boolean isDownDest = "VR".equalsIgnoreCase(dest) || "DRD".equalsIgnoreCase(dest)
                || "GNC".equalsIgnoreCase(dest) || "NDLS".equalsIgnoreCase(dest)
                || "NZM".equalsIgnoreCase(dest) || "ADI".equalsIgnoreCase(dest)
                || "BHUJ".equalsIgnoreCase(dest) || "ST".equalsIgnoreCase(dest);
        if (isUpDest) {
            return "UP";
        } else if (isDownDest) {
            return "DOWN";
        } else if ("VR".equalsIgnoreCase(src) || "BSR".equalsIgnoreCase(src) || "BYR".equalsIgnoreCase(src)) {
            return "UP";
        } else if ("MMCT".equalsIgnoreCase(src) || "CCG".equalsIgnoreCase(src) || "BDTS".equalsIgnoreCase(src)) {
            return "DOWN";
        } else {
            return "UP";
        }
    }

    private TrackBlockSnapshotDto toBlockSnapshot(TrackBlock b) {
        TrackBlockSnapshotDto dto = new TrackBlockSnapshotDto();
        dto.setBlockId(b.getBlockId());
        dto.setFromStationCode(b.getFromStationCode());
        dto.setToStationCode(b.getToStationCode());
        dto.setLineType(b.getLineType());
        dto.setBlockType(b.getBlockType());
        dto.setOccupied(b.isOccupied());
        dto.setOccupyingTrainId(b.getOccupyingTrainId());
        dto.setOccupyingTrainNumber(b.getOccupyingTrainNumber());
        dto.setClosed(b.isClosed());
        dto.setDirection(b.getDirection());
        dto.setLengthKm(b.getLengthKm());
        dto.setNextBlockId(b.getNextBlockId());
        return dto;
    }

    private SignalSnapshotDto toSignalSnapshot(Signal s) {
        SignalSnapshotDto dto = new SignalSnapshotDto();
        dto.setSignalId(s.getSignalId());
        dto.setProtectedBlockId(s.getProtectedBlockId());
        dto.setStationCode(s.getStationCode());
        dto.setDirection(s.getDirection());
        dto.setLineType(s.getLineType());
        dto.setAspect(s.getCurrentAspect());
        dto.setReason(s.getAspectReason());
        dto.setNextSignalId(s.getNextSignalId());
        return dto;
    }

    private PlatformSnapshotDto toPlatformSnapshot(Platform p) {
        PlatformSnapshotDto dto = new PlatformSnapshotDto();
        dto.setPlatformId(p.getPlatformId());
        dto.setStationCode(p.getStationCode());
        dto.setPlatformNumber(p.getPlatformNumber());
        dto.setLineType(p.getLineType());
        dto.setOccupied(Boolean.TRUE.equals(p.getIsOccupied()));
        dto.setClosed(Boolean.TRUE.equals(p.getIsClosed()));
        dto.setClosureReason(p.getClosureReason());
        dto.setCurrentTrainId(p.getCurrentTrainId());
        dto.setCurrentTrainNumber(p.getCurrentTrainNumber());
        dto.setOccupancyStart(p.getOccupancyStart());
        dto.setOccupancyEnd(p.getOccupancyEnd());
        dto.setDirection(p.getDirection());

        // Dwell seconds remaining
        if (dto.isOccupied() && p.getCurrentTrainId() != null) {
            Train occupyingTrain = trainRepository.findById(p.getCurrentTrainId()).orElse(null);
            if (occupyingTrain != null && occupyingTrain.getActualDeparture() != null) {
                long rem = java.time.Duration.between(simulationTime, occupyingTrain.getActualDeparture()).getSeconds();
                dto.setDwellSecondsRemaining((int) Math.max(0, rem));
            } else {
                dto.setDwellSecondsRemaining(0);
            }
        } else {
            dto.setDwellSecondsRemaining(0);
        }

        // Next waiting train for this platform
        List<Train> stationTrains = trainRepository.findByTargetStationCode(p.getStationCode());
        Train waitingTrain = stationTrains.stream()
                .filter(t -> Boolean.TRUE.equals(t.getIsActive()) && !Objects.equals(t.getTrainId(), p.getCurrentTrainId()))
                .filter(t -> Objects.equals(t.getAssignedPlatformId(), p.getPlatformId()))
                .filter(t -> t.getStatus() == TrainStatus.WAITING || t.getStatus() == TrainStatus.APPROACHING)
                .filter(t -> {
                    String pDir = p.getDirection();
                    if (pDir == null || "COMMON".equalsIgnoreCase(pDir)) return true;
                    return pDir.equalsIgnoreCase(t.getDirection());
                })
                .sorted((a, b) -> {
                    boolean aWait = a.getStatus() == TrainStatus.WAITING;
                    boolean bWait = b.getStatus() == TrainStatus.WAITING;
                    if (aWait && !bWait) return -1;
                    if (!aWait && bWait) return 1;
                    double progA = a.getProgressPercent() != null ? a.getProgressPercent() : 0.0;
                    double progB = b.getProgressPercent() != null ? b.getProgressPercent() : 0.0;
                    return Double.compare(progB, progA);
                })
                .findFirst()
                .orElse(null);
        if (waitingTrain != null) {
            dto.setNextWaitingTrainNumber(waitingTrain.getTrainNumber());
        }
        // Home & Starter Signals
        String homeSigId = "SIG-" + p.getStationCode() + "-P" + p.getPlatformNumber();
        Signal homeSig = signals.get(homeSigId);
        dto.setHomeSignalId(homeSigId);
        dto.setHomeSignalAspect(homeSig != null ? homeSig.getCurrentAspect() : null);

        String starterSigId = homeSigId + "-STARTER";
        Signal starterSig = signals.get(starterSigId);
        dto.setStarterSignalId(starterSigId);
        dto.setStarterSignalAspect(starterSig != null ? starterSig.getCurrentAspect() : null);

        return dto;
    }

    private void executeTrainTurnaround(Train train) {
        String tgtUpper = train.getTargetStationCode() != null ? train.getTargetStationCode() :
                (train.getCurrentStationCode() != null ? train.getCurrentStationCode() :
                        ("UP".equalsIgnoreCase(determineTrainDirection(train)) ? "MMCT" : "VR"));
        String trainDir = determineTrainDirection(train);
        String newDir = "UP".equalsIgnoreCase(trainDir) ? "DOWN" : "UP";
        boolean isFast = (train.getTrainCategory() == TrainCategory.FAST_LOCAL
                || train.getTrainCategory() == TrainCategory.EXPRESS
                || train.getTrainCategory() == TrainCategory.MAIL
                || train.getTrainCategory() == TrainCategory.SUPERFAST
                || train.getTrainCategory() == TrainCategory.PREMIUM);
        LineType targetLineType = "DOWN".equalsIgnoreCase(newDir)
                ? (isFast ? LineType.FAST_DOWN : LineType.SLOW_DOWN)
                : (isFast ? LineType.FAST_UP : LineType.SLOW_UP);

        List<Platform> stationPlatforms = platformRepository.findByStationCodeOrderByPlatformNumberAsc(tgtUpper);
        Platform turnaroundPlat = null;
        for (Platform p : stationPlatforms) {
            if (p.getLineType() == targetLineType && !Boolean.TRUE.equals(p.getIsClosed())) {
                String pBlockId = "BLK-" + tgtUpper + "-P" + p.getPlatformNumber();
                TrackBlock tb = trackBlocks.get(pBlockId);
                if (tb != null && (!tb.isOccupied() || train.getTrainId().equals(tb.getOccupyingTrainId()))) {
                    turnaroundPlat = p;
                    break;
                }
            }
        }
        if (turnaroundPlat == null) {
            for (Platform p : stationPlatforms) {
                if (!Boolean.TRUE.equals(p.getIsClosed()) && (newDir.equalsIgnoreCase(p.getDirection()) || "COMMON".equalsIgnoreCase(p.getDirection()))) {
                    String pBlockId = "BLK-" + tgtUpper + "-P" + p.getPlatformNumber();
                    TrackBlock tb = trackBlocks.get(pBlockId);
                    if (tb != null && (!tb.isOccupied() || train.getTrainId().equals(tb.getOccupyingTrainId()))) {
                        turnaroundPlat = p;
                        break;
                    }
                }
            }
        }

        train.setDirection(newDir);
        train.setSourceStation(tgtUpper);
        String newDest = "DOWN".equalsIgnoreCase(newDir) ? "VR" : "MMCT";
        train.setDestinationStation(newDest);

        List<String> newStops = StoppingPatternConfig.generateDefaultStopStations(train.getTrainCategory(), tgtUpper, newDest, newDir);
        train.setStopStations(newStops);
        String firstTarget = getNextScheduledStop(train, tgtUpper, newDir);
        train.setTargetStationCode(tgtUpper);
        train.setNextStopStationCode(firstTarget);

        if (turnaroundPlat != null) {
            train.setAssignedPlatformId(turnaroundPlat.getPlatformId());
            train.setAssignedPlatformNumber(turnaroundPlat.getPlatformNumber());
            String newPBlockId = "BLK-" + tgtUpper + "-P" + turnaroundPlat.getPlatformNumber();
            train.setCurrentBlockId(newPBlockId);
            TrackBlock newPBlock = trackBlocks.get(newPBlockId);
            if (newPBlock != null) {
                newPBlock.occupy(train.getTrainId(), train.getTrainNumber());
            }
            turnaroundPlat.setIsOccupied(true);
            turnaroundPlat.setCurrentTrainId(train.getTrainId());
            turnaroundPlat.setCurrentTrainNumber(train.getTrainNumber());
            platformRepository.save(turnaroundPlat);
        }

        train.setStatus(TrainStatus.AT_PLATFORM);
        train.setProgressPercent(50.0);
        train.setSpeedKmH(0.0);
        int dwellSec = StoppingPatternConfig.getDwellSeconds(train.getTrainCategory(), tgtUpper, true);
        if (dwellSec <= 0) dwellSec = 60;
        train.setActualArrival(simulationTime);
        train.setActualDeparture(simulationTime.plusSeconds(dwellSec));
        train.setWaitingReason(null);
        train.setIsActive(true);

        RouteProgress rp = corridorGraph.computeRouteProgress(train);
        train.setLastStationCode(rp.getLastStationCode());
        train.setUpcomingStationCode(rp.getUpcomingStationCode());
        train.setNextStopStationCode(rp.getNextStopStationCode());

        trainRepository.save(train);

        eventRepository.save(new SimulationEvent("TRAIN_TURNAROUND", Severity.INFO,
                String.format("Train %s turnaround at %s: reversed to %s toward %s on P%s",
                        train.getTrainNumber(), tgtUpper, newDir, newDest, train.getAssignedPlatformNumber() != null ? train.getAssignedPlatformNumber() : "?"),
                train.getTrainNumber(), train.getAssignedPlatformNumber(), tgtUpper));
    }

    public boolean isPeakHour() {
        return isPeakHour;
    }

    public int getActiveFleetCount() {
        return trainRepository.findByIsActiveTrue().size();
    }

    @Transactional
    public synchronized void setPeakHourMode(boolean enabled) {
        if (this.isPeakHour == enabled) return;
        this.isPeakHour = enabled;

        if (enabled) {
            LocalDateTime now = simulationTime != null ? simulationTime : LocalDateTime.of(2026, 9, 18, 10, 0, 0);
            List<PeakHourFleetConfig.PeakTrainSpec> specs = PeakHourFleetConfig.getPeakHourTrainSpecs();
            int addedCount = 0;
            for (PeakHourFleetConfig.PeakTrainSpec spec : specs) {
                if (trainRepository.findByTrainNumber(spec.trainNumber).isPresent()) continue;

                Train t = new Train(spec.trainNumber, spec.trainName, spec.trainType,
                        spec.source, spec.destination, 5,
                        now, now.plusMinutes(45), null, spec.destination);
                t.setTrainCategory(spec.category);
                t.setDirection(spec.direction);
                t.setCurrentBlockId(spec.startBlockId);
                t.setProgressPercent(spec.progress);
                t.setSpeedKmH(spec.speed);
                t.setStatus(TrainStatus.APPROACHING);
                t.setIsActive(true);
                t.setDelayMinutes(0);

                List<String> defaultStops = StoppingPatternConfig.generateDefaultStopStations(spec.category, spec.source, spec.destination, spec.direction);
                t.setStopStations(defaultStops);

                RouteProgress rp = corridorGraph.computeRouteProgress(t);
                t.setLastStationCode(rp.getLastStationCode());
                t.setUpcomingStationCode(rp.getUpcomingStationCode());
                t.setCurrentStationCode(rp.getLastStationCode());
                t.setNextStopStationCode(rp.getNextStopStationCode());
                t.setTargetStationCode(rp.getNextStopStationCode());

                t = trainRepository.save(t);
                if (trackBlocks.containsKey(spec.startBlockId)) {
                    trackBlocks.get(spec.startBlockId).occupy(t.getTrainId(), t.getTrainNumber());
                }
                addedCount++;
            }
            recalculateSignalling();
            eventRepository.save(new SimulationEvent("PEAK_HOUR_ACTIVATED", Severity.INFO,
                    "Peak hour mode activated: added " + addedCount + " trains to corridor", null, null, null));
        } else {
            List<Train> peakTrains = trainRepository.findAll().stream()
                    .filter(t -> t.getTrainNumber().startsWith("SIM-PK-"))
                    .toList();
            for (Train t : peakTrains) {
                if (t.getCurrentBlockId() != null && trackBlocks.containsKey(t.getCurrentBlockId())) {
                    TrackBlock tb = trackBlocks.get(t.getCurrentBlockId());
                    if (t.getTrainId().equals(tb.getOccupyingTrainId())) {
                        tb.vacate();
                    }
                }
                if (t.getAssignedPlatformId() != null) {
                    platformRepository.findById(t.getAssignedPlatformId()).ifPresent(p -> {
                        if (t.getTrainId().equals(p.getCurrentTrainId())) {
                            p.setIsOccupied(false);
                            p.setCurrentTrainId(null);
                            p.setCurrentTrainNumber(null);
                            platformRepository.save(p);
                        }
                    });
                }
                trainRepository.delete(t);
            }
            recalculateSignalling();
            eventRepository.save(new SimulationEvent("PEAK_HOUR_DEACTIVATED", Severity.INFO,
                    "Peak hour mode deactivated: returned to standard 27-train fleet", null, null, null));
        }
    }

    public synchronized PlatformUtilizationMetrics getRollingPlatformUtilizationMetrics(int windowMinutes) {
        List<Platform> allPlatforms = platformRepository.findAll();
        long totalPlatforms = allPlatforms.size();
        long currentOccupied = allPlatforms.stream().filter(Platform::getIsOccupied).count();
        long currentClosed = allPlatforms.stream().filter(Platform::getIsClosed).count();
        long currentAvailable = Math.max(1, totalPlatforms - currentClosed);
        double snapshotUtil = (double) currentOccupied / currentAvailable * 100.0;

        LocalDateTime cutoff = simulationTime.minusMinutes(windowMinutes);
        List<PlatformStateSlice> validSlices = platformHistory.stream()
                .filter(s -> !s.getTime().isBefore(cutoff))
                .toList();

        if (validSlices.isEmpty()) {
            Map<String, Double> lineTypeMap = new LinkedHashMap<>();
            for (LineType lt : List.of(LineType.FAST_UP, LineType.FAST_DOWN, LineType.SLOW_UP, LineType.SLOW_DOWN)) {
                List<Platform> linePlats = allPlatforms.stream().filter(p -> p.getLineType() == lt).toList();
                long occ = linePlats.stream().filter(Platform::getIsOccupied).count();
                long avail = linePlats.stream().filter(p -> !p.getIsClosed()).count();
                double u = avail > 0 ? (double) occ / avail * 100.0 : 0.0;
                lineTypeMap.put(lt.name(), Math.round(u * 10.0) / 10.0);
            }

            Map<String, List<Platform>> stationMap = allPlatforms.stream()
                    .collect(Collectors.groupingBy(Platform::getStationCode));
            List<StationUtilizationDto> busiest = stationMap.entrySet().stream()
                    .map(e -> {
                        String code = e.getKey();
                        String name = corridorGraph.getStation(code) != null ? corridorGraph.getStation(code).getName() : code;
                        long occ = e.getValue().stream().filter(Platform::getIsOccupied).count();
                        long avail = e.getValue().stream().filter(p -> !p.getIsClosed()).count();
                        double u = avail > 0 ? (double) occ / avail * 100.0 : 0.0;
                        return new StationUtilizationDto(code, name, Math.round(u * 10.0) / 10.0, occ * 1.0, avail * 1.0);
                    })
                    .sorted(Comparator.comparingDouble(StationUtilizationDto::getUtilizationPercent).reversed())
                    .limit(3)
                    .toList();

            return new PlatformUtilizationMetrics(
                    Math.round(snapshotUtil * 10.0) / 10.0,
                    Math.round(snapshotUtil * 10.0) / 10.0,
                    currentOccupied * 1.0,
                    currentAvailable * 1.0,
                    busiest,
                    lineTypeMap
            );
        }

        Map<Long, Long> platOccupiedSeconds = new HashMap<>();
        Map<Long, Long> platClosedSeconds = new HashMap<>();
        long totalWindowSeconds = 0;

        for (PlatformStateSlice slice : validSlices) {
            totalWindowSeconds += slice.getDurationSeconds();
            for (Long pid : slice.getOccupiedPlatformIds()) {
                platOccupiedSeconds.merge(pid, slice.getDurationSeconds(), Long::sum);
            }
            for (Long pid : slice.getClosedPlatformIds()) {
                platClosedSeconds.merge(pid, slice.getDurationSeconds(), Long::sum);
            }
        }

        double totalOccupiedMinutes = 0.0;
        double totalAvailableMinutes = 0.0;

        Map<String, Double> stnOccupiedMinutes = new HashMap<>();
        Map<String, Double> stnAvailableMinutes = new HashMap<>();

        Map<String, Double> lineOccupiedMinutes = new HashMap<>();
        Map<String, Double> lineAvailableMinutes = new HashMap<>();

        for (Platform p : allPlatforms) {
            Long pid = p.getPlatformId();
            long closedSec = platClosedSeconds.getOrDefault(pid, 0L);
            long availSec = Math.max(0L, totalWindowSeconds - closedSec);
            long occSec = platOccupiedSeconds.getOrDefault(pid, 0L);

            double occMin = occSec / 60.0;
            double availMin = availSec / 60.0;

            totalOccupiedMinutes += occMin;
            totalAvailableMinutes += availMin;

            String stn = p.getStationCode();
            stnOccupiedMinutes.merge(stn, occMin, Double::sum);
            stnAvailableMinutes.merge(stn, availMin, Double::sum);

            String lt = p.getLineType() != null ? p.getLineType().name() : "COMMON";
            lineOccupiedMinutes.merge(lt, occMin, Double::sum);
            lineAvailableMinutes.merge(lt, availMin, Double::sum);
        }

        double overallUtil = totalAvailableMinutes > 0 ? (totalOccupiedMinutes / totalAvailableMinutes) * 100.0 : 0.0;

        Map<String, Double> lineTypeMap = new LinkedHashMap<>();
        for (LineType lt : List.of(LineType.FAST_UP, LineType.FAST_DOWN, LineType.SLOW_UP, LineType.SLOW_DOWN)) {
            double occ = lineOccupiedMinutes.getOrDefault(lt.name(), 0.0);
            double avail = lineAvailableMinutes.getOrDefault(lt.name(), 0.0);
            double u = avail > 0 ? (occ / avail) * 100.0 : 0.0;
            lineTypeMap.put(lt.name(), Math.round(u * 10.0) / 10.0);
        }

        List<StationUtilizationDto> busiest = stnAvailableMinutes.keySet().stream()
                .map(code -> {
                    String name = corridorGraph.getStation(code) != null ? corridorGraph.getStation(code).getName() : code;
                    double occ = stnOccupiedMinutes.getOrDefault(code, 0.0);
                    double avail = stnAvailableMinutes.getOrDefault(code, 0.0);
                    double u = avail > 0 ? (occ / avail) * 100.0 : 0.0;
                    return new StationUtilizationDto(code, name, Math.round(u * 10.0) / 10.0, Math.round(occ * 10.0) / 10.0, Math.round(avail * 10.0) / 10.0);
                })
                .sorted(Comparator.comparingDouble(StationUtilizationDto::getUtilizationPercent).reversed())
                .limit(3)
                .toList();

        return new PlatformUtilizationMetrics(
                Math.round(overallUtil * 10.0) / 10.0,
                Math.round(snapshotUtil * 10.0) / 10.0,
                Math.round(totalOccupiedMinutes * 10.0) / 10.0,
                Math.round(totalAvailableMinutes * 10.0) / 10.0,
                busiest,
                lineTypeMap
        );
    }

    public static class PlatformStateSlice {
        private final LocalDateTime time;
        private final long durationSeconds;
        private final Set<Long> occupiedPlatformIds;
        private final Set<Long> closedPlatformIds;

        public PlatformStateSlice(LocalDateTime time, long durationSeconds, Set<Long> occupiedPlatformIds, Set<Long> closedPlatformIds) {
            this.time = time;
            this.durationSeconds = durationSeconds;
            this.occupiedPlatformIds = occupiedPlatformIds;
            this.closedPlatformIds = closedPlatformIds;
        }

        public LocalDateTime getTime() { return time; }
        public long getDurationSeconds() { return durationSeconds; }
        public Set<Long> getOccupiedPlatformIds() { return occupiedPlatformIds; }
        public Set<Long> getClosedPlatformIds() { return closedPlatformIds; }
    }

    public static class StationUtilizationDto {
        private final String stationCode;
        private final String stationName;
        private final double utilizationPercent;
        private final double occupiedMinutes;
        private final double availableMinutes;

        public StationUtilizationDto(String stationCode, String stationName, double utilizationPercent, double occupiedMinutes, double availableMinutes) {
            this.stationCode = stationCode;
            this.stationName = stationName;
            this.utilizationPercent = utilizationPercent;
            this.occupiedMinutes = occupiedMinutes;
            this.availableMinutes = availableMinutes;
        }

        public String getStationCode() { return stationCode; }
        public String getStationName() { return stationName; }
        public double getUtilizationPercent() { return utilizationPercent; }
        public double getOccupiedMinutes() { return occupiedMinutes; }
        public double getAvailableMinutes() { return availableMinutes; }
    }

    public static class PlatformUtilizationMetrics {
        private final double overallUtilizationPercent;
        private final double snapshotUtilizationPercent;
        private final double totalOccupiedPlatformMinutes;
        private final double totalAvailablePlatformMinutes;
        private final List<StationUtilizationDto> busiestStations;
        private final Map<String, Double> lineTypeUtilization;

        public PlatformUtilizationMetrics(double overallUtilizationPercent, double snapshotUtilizationPercent,
                                        double totalOccupiedPlatformMinutes, double totalAvailablePlatformMinutes,
                                        List<StationUtilizationDto> busiestStations, Map<String, Double> lineTypeUtilization) {
            this.overallUtilizationPercent = overallUtilizationPercent;
            this.snapshotUtilizationPercent = snapshotUtilizationPercent;
            this.totalOccupiedPlatformMinutes = totalOccupiedPlatformMinutes;
            this.totalAvailablePlatformMinutes = totalAvailablePlatformMinutes;
            this.busiestStations = busiestStations;
            this.lineTypeUtilization = lineTypeUtilization;
        }

        public double getOverallUtilizationPercent() { return overallUtilizationPercent; }
        public double getSnapshotUtilizationPercent() { return snapshotUtilizationPercent; }
        public double getTotalOccupiedPlatformMinutes() { return totalOccupiedPlatformMinutes; }
        public double getTotalAvailablePlatformMinutes() { return totalAvailablePlatformMinutes; }
        public List<StationUtilizationDto> getBusiestStations() { return busiestStations; }
        public Map<String, Double> getLineTypeUtilization() { return lineTypeUtilization; }
    }
}
