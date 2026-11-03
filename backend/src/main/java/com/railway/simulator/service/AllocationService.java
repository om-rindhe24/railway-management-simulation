package com.railway.simulator.service;

import com.railway.simulator.algorithm.*;
import com.railway.simulator.algorithm.dto.AllocationDecision;
import com.railway.simulator.algorithm.dto.ConflictReport;
import com.railway.simulator.algorithm.dto.ReallocationResult;
import com.railway.simulator.domain.*;
import com.railway.simulator.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.railway.simulator.algorithm.dto.PlatformScoreResult;
import com.railway.simulator.config.PlatformLineConfig;
import com.railway.simulator.controller.dto.WhatIfPreviewResult;
import com.railway.simulator.controller.dto.WhatIfTrainDiff;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class AllocationService {

    private final PlatformRepository platformRepository;
    private final TrainRepository trainRepository;
    private final PlatformAllocationRepository allocationRepository;
    private final AllocationHistoryRepository historyRepository;
    private final SimulationEventRepository eventRepository;

    private final PlatformAllocationEngine allocationEngine;
    private final DynamicReallocationEngine reallocationEngine;
    private final IntervalConflictDetector conflictDetector;
    private final SimulationService simulationService;

    public AllocationService(PlatformRepository platformRepository,
                             TrainRepository trainRepository,
                             PlatformAllocationRepository allocationRepository,
                             AllocationHistoryRepository historyRepository,
                             SimulationEventRepository eventRepository,
                             @org.springframework.context.annotation.Lazy SimulationService simulationService) {
        this.platformRepository = platformRepository;
        this.trainRepository = trainRepository;
        this.allocationRepository = allocationRepository;
        this.historyRepository = historyRepository;
        this.eventRepository = eventRepository;
        this.simulationService = simulationService;

        this.conflictDetector = new IntervalConflictDetector(3); // 3-minute safety buffer
        PlatformScoringEngine scoringEngine = new PlatformScoringEngine(conflictDetector);
        this.allocationEngine = new PlatformAllocationEngine(scoringEngine);
        this.reallocationEngine = new DynamicReallocationEngine(allocationEngine, conflictDetector);
    }

    public List<PlatformAllocation> getActiveAllocations(String stationCode) {
        if (stationCode != null && !stationCode.isBlank()) {
            return allocationRepository.findByStationCodeAndStatus(stationCode.toUpperCase(), AllocationStatus.ACTIVE);
        }
        return allocationRepository.findByStatus(AllocationStatus.ACTIVE);
    }

    public List<AllocationHistory> getAllocationHistory(String stationCode) {
        if (stationCode != null && !stationCode.isBlank()) {
            return historyRepository.findByStationCodeOrderByTimestampDesc(stationCode.toUpperCase());
        }
        return historyRepository.findAllByOrderByTimestampDesc();
    }

    public List<ConflictReport> detectActiveConflicts(String stationCode) {
        List<PlatformAllocation> activeAllocations = getActiveAllocations(stationCode);
        return conflictDetector.findAllConflicts(activeAllocations);
    }

    /**
     * Executes initial or batch platform allocation for all trains at a station.
     */
    @Transactional
    public List<AllocationDecision> runStationAllocation(String stationCode) {
        List<Platform> platforms = platformRepository.findByStationCodeOrderByPlatformNumberAsc(stationCode.toUpperCase());
        List<Train> trains = trainRepository.findByTargetStationCode(stationCode.toUpperCase())
                .stream().filter(t -> t.stopsAtStation(stationCode)).toList();

        if (platforms.isEmpty() || trains.isEmpty()) {
            return Collections.emptyList();
        }

        // Clear existing active allocations for fresh scheduling run
        List<PlatformAllocation> existingActive = allocationRepository.findByStationCodeAndStatus(stationCode.toUpperCase(), AllocationStatus.ACTIVE);
        for (PlatformAllocation a : existingActive) {
            a.setStatus(AllocationStatus.COMPLETED);
        }
        allocationRepository.saveAll(existingActive);

        // Min-Heap priority queue orders trains for scheduling
        TrainPriorityQueue priorityQueue = new TrainPriorityQueue();
        for (Train t : trains) {
            if (t.getIsActive() && t.getStatus() != TrainStatus.CANCELLED) {
                priorityQueue.offer(t);
            }
        }

        Map<Long, List<PlatformAllocation>> allocationsByPlatform = new HashMap<>();
        List<AllocationDecision> decisions = new ArrayList<>();

        while (!priorityQueue.isEmpty()) {
            Train train = priorityQueue.poll();
            AllocationDecision decision = allocationEngine.allocatePlatform(train, platforms, allocationsByPlatform);
            decisions.add(decision);

            if (decision.isAllocated()) {
                PlatformAllocation allocation = new PlatformAllocation(
                        train.getTrainId(),
                        train.getTrainNumber(),
                        train.getTrainName(),
                        decision.getSelectedPlatformId(),
                        decision.getSelectedPlatformNumber(),
                        platforms.get(0).getStation().getStationId(),
                        stationCode.toUpperCase(),
                        train.getActualArrival(),
                        train.getActualDeparture(),
                        decision.getPrimaryReason(),
                        decision.getDetailedBreakdown(),
                        train.getDelayMinutes()
                );
                PlatformAllocation savedAlloc = allocationRepository.save(allocation);
                allocationsByPlatform.computeIfAbsent(decision.getSelectedPlatformId(), k -> new ArrayList<>()).add(savedAlloc);

                // Update train state
                Integer previousPlatform = train.getAssignedPlatformNumber();
                train.setAssignedPlatformId(decision.getSelectedPlatformId());
                train.setAssignedPlatformNumber(decision.getSelectedPlatformNumber());
                if (train.getStatus() == TrainStatus.AT_PLATFORM) {
                    train.setCurrentBlockId("BLK-" + stationCode.toUpperCase() + "-P" + decision.getSelectedPlatformNumber());
                }
                trainRepository.save(train);

                // Record in audit log
                historyRepository.save(new AllocationHistory(
                        train.getTrainNumber(),
                        train.getTrainName(),
                        stationCode.toUpperCase(),
                        previousPlatform,
                        decision.getSelectedPlatformNumber(),
                        ChangeType.INITIAL_ALLOCATION,
                        decision.getPrimaryReason(),
                        0,
                        1
                ));
            } else {
                train.setStatus(TrainStatus.WAITING);
                trainRepository.save(train);
            }
        }

        if (simulationService != null) {
            simulationService.syncSignallingState();
        }

        eventRepository.save(new SimulationEvent(
                "BATCH_ALLOCATION_RUN",
                Severity.SUCCESS,
                String.format("Allocated %d trains across %d platforms at %s", decisions.stream().filter(AllocationDecision::isAllocated).count(), platforms.size(), stationCode),
                null, null, stationCode
        ));

        return decisions;
    }

    /**
     * Resolves dynamic conflicts when a platform is closed.
     */
    @Transactional
    public ReallocationResult handlePlatformClosure(Long platformId, String reason) {
        Platform platform = platformRepository.findById(platformId)
                .orElseThrow(() -> new IllegalArgumentException("Platform not found: " + platformId));

        platform.setIsClosed(true);
        platform.setClosureReason(reason);
        platformRepository.save(platform);

        // Immediate synchronization: platform block closes, signal turns RED, approaching trains halt
        if (simulationService != null) {
            simulationService.syncSignallingState();
        }

        String stationCode = platform.getStationCode();
        List<Platform> stationPlatforms = platformRepository.findByStationCodeOrderByPlatformNumberAsc(stationCode);
        List<Train> stationTrains = new ArrayList<>(trainRepository.findByTargetStationCode(stationCode));
        for (Train t : trainRepository.findAll()) {
            if (platformId.equals(t.getAssignedPlatformId()) && stationTrains.stream().noneMatch(st -> st.getTrainId().equals(t.getTrainId()))) {
                stationTrains.add(t);
            }
        }
        List<PlatformAllocation> allAllocations = allocationRepository.findByStationCodeAndStatus(stationCode, AllocationStatus.ACTIVE);

        ReallocationResult result = reallocationEngine.handlePlatformClosure(
                platform.getPlatformId(),
                platform.getPlatformNumber(),
                stationCode,
                stationPlatforms,
                stationTrains,
                allAllocations
        );

        // Record platform closure event in audit history
        historyRepository.save(new AllocationHistory(
                "SYSTEM",
                "Platform Closure Event",
                stationCode,
                platform.getPlatformNumber(),
                null,
                ChangeType.PLATFORM_CLOSURE,
                String.format("Platform %d closed at %s: %s. %d affected train(s) re-evaluated.",
                        platform.getPlatformNumber(), stationCode, reason, result.getReallocatedCount()),
                0,
                result.getReallocatedCount()
        ));

        // Apply reallocation results to DB
        applyReallocationDecisions(result.getReallocationDecisions(), stationCode, ChangeType.TRAIN_REALLOCATED);

        // Secondary synchronization: reallocated trains receive new platform signals, resume if permissive
        if (simulationService != null) {
            simulationService.syncSignallingState();
        }

        eventRepository.save(new SimulationEvent(
                "REALLOCATION_CASCADE",
                Severity.WARNING,
                String.format("Platform %d closed at %s. Evicted and reallocated %d trains.",
                        platform.getPlatformNumber(), stationCode, result.getReallocatedCount()),
                null, platform.getPlatformNumber(), stationCode
        ));

        return result;
    }

    /**
     * Resolves dynamic conflicts when a train is delayed.
     */
    @Transactional
    public ReallocationResult handleTrainDelay(Long trainId, int delayMinutes, String reason) {
        Train train = trainRepository.findById(trainId)
                .orElseThrow(() -> new IllegalArgumentException("Train not found: " + trainId));

        String stationCode = train.getTargetStationCode();
        List<Platform> stationPlatforms = platformRepository.findByStationCodeOrderByPlatformNumberAsc(stationCode);
        List<Train> stationTrains = trainRepository.findByTargetStationCode(stationCode);
        List<PlatformAllocation> allAllocations = allocationRepository.findByStationCodeAndStatus(stationCode, AllocationStatus.ACTIVE);

        ReallocationResult result = reallocationEngine.handleTrainDelay(
                train,
                delayMinutes,
                stationPlatforms,
                stationTrains,
                allAllocations
        );

        applyReallocationDecisions(result.getReallocationDecisions(), stationCode, ChangeType.DYNAMIC_REALLOCATION);

        if (simulationService != null) {
            simulationService.syncSignallingState();
        }

        eventRepository.save(new SimulationEvent(
                "DELAY_RESOLVED",
                Severity.INFO,
                String.format("Train %s delayed by %d min. Dynamic reallocation completed (%d reassigned).",
                        train.getTrainNumber(), delayMinutes, result.getReallocatedCount()),
                train.getTrainNumber(), train.getAssignedPlatformNumber(), stationCode
        ));

        return result;
    }

    private void applyReallocationDecisions(List<AllocationDecision> decisions, String stationCode, ChangeType changeType) {
        if (decisions == null) return;

        for (AllocationDecision dec : decisions) {
            Optional<Train> optTrain = trainRepository.findById(dec.getTrainId());
            if (optTrain.isEmpty()) continue;
            Train train = optTrain.get();

            Integer oldPlatform = train.getAssignedPlatformNumber();

            // When a train is evicted or reassigned, set the old platform TrackBlock occupied=false and clear its occupying train before assigning the new block
            if (simulationService != null && train.getStatus() != TrainStatus.AT_PLATFORM) {
                if (oldPlatform != null) {
                    String oldBlockId = "BLK-" + stationCode.toUpperCase() + "-P" + oldPlatform;
                    TrackBlock oldBlock = simulationService.getTrackBlocks().get(oldBlockId);
                    if (oldBlock != null) {
                        oldBlock.vacate();
                    }
                }
                if (train.getCurrentBlockId() != null && train.getCurrentBlockId().matches(".*-P\\d+")) {
                    TrackBlock currentPlatBlock = simulationService.getTrackBlocks().get(train.getCurrentBlockId());
                    if (currentPlatBlock != null) {
                        currentPlatBlock.vacate();
                    }
                }
            }

            // Mark previous active allocation as REALLOCATED
            allocationRepository.findByTrainIdAndStatus(train.getTrainId(), AllocationStatus.ACTIVE)
                    .ifPresent(a -> {
                        a.setStatus(AllocationStatus.REALLOCATED);
                        allocationRepository.save(a);
                    });

            if (dec.isAllocated()) {
                train.setAssignedPlatformId(dec.getSelectedPlatformId());
                train.setAssignedPlatformNumber(dec.getSelectedPlatformNumber());
                String newHomeSigId = "SIG-" + stationCode.toUpperCase() + "-P" + dec.getSelectedPlatformNumber();
                train.setNextSignalId(newHomeSigId);
                train.setSignalAspect(SignalAspect.GREEN);
                if (train.getStatus() == TrainStatus.WAITING) {
                    train.setStatus(TrainStatus.APPROACHING);
                    train.setWaitingReason(null);
                }
                trainRepository.save(train);

                // Insert new allocation
                PlatformAllocation newAlloc = new PlatformAllocation(
                        train.getTrainId(),
                        train.getTrainNumber(),
                        train.getTrainName(),
                        dec.getSelectedPlatformId(),
                        dec.getSelectedPlatformNumber(),
                        1L,
                        stationCode,
                        train.getActualArrival(),
                        train.getActualDeparture(),
                        dec.getPrimaryReason(),
                        dec.getDetailedBreakdown(),
                        train.getDelayMinutes()
                );
                allocationRepository.save(newAlloc);

                String explicitReason = buildExplicitReallocationReason(train, oldPlatform, dec, stationCode);

                // Record in audit log (AllocationHistory)
                ChangeType recordType = (changeType == ChangeType.PLATFORM_CLOSURE || changeType == ChangeType.DYNAMIC_REALLOCATION)
                        ? ChangeType.TRAIN_REALLOCATED : changeType;

                historyRepository.save(new AllocationHistory(
                        train.getTrainNumber(),
                        train.getTrainName(),
                        stationCode,
                        oldPlatform,
                        dec.getSelectedPlatformNumber(),
                        recordType,
                        explicitReason,
                        dec.getEstimatedDelayMinutes(),
                        1
                ));

                // Record in simulation event log (SimulationEvent)
                eventRepository.save(new SimulationEvent(
                        "TRAIN_REALLOCATED",
                        Severity.WARNING,
                        explicitReason,
                        train.getTrainNumber(),
                        dec.getSelectedPlatformNumber(),
                        stationCode
                ));
            } else {
                train.setStatus(TrainStatus.WAITING);
                train.setSpeedKmH(0.0);
                train.setSignalAspect(SignalAspect.RED);
                String homeSigId = (stationCode != null && oldPlatform != null) ? "SIG-" + stationCode.toUpperCase() + "-P" + oldPlatform : "SIG-AUTO";
                train.setNextSignalId(homeSigId);
                String waitMsg = String.format("%s held: Allocated platform %d is closed and no alternative platform available",
                        train.getTrainNumber(), oldPlatform != null ? oldPlatform : 0);
                train.setWaitingReason(waitMsg);
                trainRepository.save(train);

                // Record in audit log (AllocationHistory) for UI visibility
                historyRepository.save(new AllocationHistory(
                        train.getTrainNumber(),
                        train.getTrainName(),
                        stationCode,
                        oldPlatform,
                        null,
                        ChangeType.TRAIN_HELD,
                        waitMsg,
                        15,
                        0
                ));

                // Record in simulation event log (SimulationEvent)
                eventRepository.save(new SimulationEvent(
                        "TRAIN_HELD",
                        Severity.WARNING,
                        waitMsg,
                        train.getTrainNumber(),
                        oldPlatform,
                        stationCode
                ));
            }
        }
    }

    private String buildExplicitReallocationReason(Train train, Integer oldPlatform, AllocationDecision dec, String stationCode) {
        if (!dec.isAllocated()) {
            return String.format("Held at home signal: No alternative platform available after platform %s closed.",
                    oldPlatform != null ? oldPlatform : "?");
        }

        int newPlatform = dec.getSelectedPlatformNumber();
        LineType newLine = com.railway.simulator.config.PlatformLineConfig.getPlatformLineType(stationCode, newPlatform);
        String dir = train.getDirection() != null ? train.getDirection().toUpperCase() : "UP";

        boolean isFast = (train.getTrainCategory() == TrainCategory.FAST_LOCAL
                || train.getTrainType() == TrainType.SPECIAL
                || train.getTrainType() == TrainType.PREMIUM
                || train.getTrainType() == TrainType.SUPERFAST
                || train.getTrainType() == TrainType.EXPRESS_MAIL);

        LineType preferredLine = "UP".equals(dir)
                ? (isFast ? LineType.FAST_UP : LineType.SLOW_UP)
                : (isFast ? LineType.FAST_DOWN : LineType.SLOW_DOWN);

        if (newLine == preferredLine) {
            if (oldPlatform != null && !oldPlatform.equals(newPlatform)) {
                return String.format("P%d closed; reallocated to P%d (%s, same line and direction)",
                        oldPlatform, newPlatform, newLine);
            }
            return String.format("Assigned to P%d (%s, optimal line match)", newPlatform, newLine);
        } else {
            // Fell back to a different line type in the same direction
            String sameLineStatus = null;
            if (dec.getCandidateScores() != null) {
                for (PlatformScoreResult score : dec.getCandidateScores()) {
                    LineType candLine = PlatformLineConfig.getPlatformLineType(stationCode, score.getPlatformNumber());
                    if (candLine == preferredLine && (oldPlatform == null || !oldPlatform.equals(score.getPlatformNumber()))) {
                        sameLineStatus = String.format("P%d (same line) occupied", score.getPlatformNumber());
                        break;
                    }
                }
            }
            if (sameLineStatus == null) {
                sameLineStatus = "same line occupied";
            }
            if (oldPlatform != null) {
                return String.format("P%d closed; %s; fell back to P%d (%s, same direction)",
                        oldPlatform, sameLineStatus, newPlatform, newLine);
            } else {
                return String.format("%s; fell back to P%d (%s, same direction)",
                        sameLineStatus, newPlatform, newLine);
            }
        }
    }

    /**
     * Previews the operational impact of a train delay on an isolated copy of simulation data.
     * Does NOT mutate database state or simulation state.
     */
    @Transactional(readOnly = true)
    public WhatIfPreviewResult previewTrainDelay(Long trainId, int delayMinutes, String reason) {
        Train train = trainRepository.findById(trainId)
                .orElseThrow(() -> new IllegalArgumentException("Train not found: " + trainId));

        String stationCode = train.getTargetStationCode() != null ? train.getTargetStationCode() : "BVI";
        List<Platform> realPlatforms = platformRepository.findByStationCodeOrderByPlatformNumberAsc(stationCode);
        List<Train> realTrains = trainRepository.findByTargetStationCode(stationCode);
        List<PlatformAllocation> realAllocations = allocationRepository.findByStationCodeAndStatus(stationCode, AllocationStatus.ACTIVE);

        // Create isolated copies
        List<Platform> clonedPlatforms = realPlatforms.stream().map(this::clonePlatform).toList();
        List<Train> clonedTrains = realTrains.stream().map(this::cloneTrain).toList();
        List<PlatformAllocation> clonedAllocations = realAllocations.stream().map(this::cloneAllocation).toList();

        Train targetTrain = clonedTrains.stream()
                .filter(t -> t.getTrainId().equals(trainId))
                .findFirst()
                .orElse(cloneTrain(train));

        Map<Long, Train> originalTrainMap = realTrains.stream().collect(Collectors.toMap(Train::getTrainId, t -> t, (a, b) -> a));
        if (!originalTrainMap.containsKey(train.getTrainId())) {
            originalTrainMap.put(train.getTrainId(), train);
        }

        ReallocationResult result = reallocationEngine.handleTrainDelay(
                targetTrain,
                delayMinutes,
                clonedPlatforms,
                clonedTrains,
                clonedAllocations
        );

        return buildWhatIfPreviewResult(result, originalTrainMap, targetTrain, delayMinutes);
    }

    /**
     * Previews the operational impact of a platform closure on an isolated copy of simulation data.
     * Does NOT mutate database state or simulation state.
     */
    @Transactional(readOnly = true)
    public WhatIfPreviewResult previewPlatformClosure(Long platformId, String reason) {
        Platform platform = platformRepository.findById(platformId)
                .orElseThrow(() -> new IllegalArgumentException("Platform not found: " + platformId));

        String stationCode = platform.getStationCode();
        List<Platform> realPlatforms = platformRepository.findByStationCodeOrderByPlatformNumberAsc(stationCode);
        List<Train> realTrains = trainRepository.findByTargetStationCode(stationCode);
        List<PlatformAllocation> realAllocations = allocationRepository.findByStationCodeAndStatus(stationCode, AllocationStatus.ACTIVE);

        // Create isolated copies
        List<Platform> clonedPlatforms = realPlatforms.stream().map(this::clonePlatform).toList();
        List<Train> clonedTrains = realTrains.stream().map(this::cloneTrain).toList();
        List<PlatformAllocation> clonedAllocations = realAllocations.stream().map(this::cloneAllocation).toList();

        Platform targetPlatform = clonedPlatforms.stream()
                .filter(p -> p.getPlatformId().equals(platformId))
                .findFirst()
                .orElse(clonePlatform(platform));

        Map<Long, Train> originalTrainMap = realTrains.stream().collect(Collectors.toMap(Train::getTrainId, t -> t, (a, b) -> a));

        ReallocationResult result = reallocationEngine.handlePlatformClosure(
                targetPlatform.getPlatformId(),
                targetPlatform.getPlatformNumber(),
                stationCode,
                clonedPlatforms,
                clonedTrains,
                clonedAllocations
        );

        return buildWhatIfPreviewResult(result, originalTrainMap, null, 0);
    }

    private WhatIfPreviewResult buildWhatIfPreviewResult(ReallocationResult result,
                                                        Map<Long, Train> originalTrainMap,
                                                        Train perturbedTrain,
                                                        int perturbationDelay) {
        WhatIfPreviewResult preview = new WhatIfPreviewResult();
        preview.setIsolatedCopy(true);
        preview.setReallocationResult(result);
        preview.setTotalAffectedTrains(result.getTotalAffectedTrains());
        preview.setReallocatedCount(result.getReallocatedCount());
        preview.setConflictsResolved(result.getConflictsResolved());
        preview.setAdditionalDelayMinutes(result.getAdditionalDelayMinutes());
        preview.setSummaryMessage(result.getSummaryMessage());

        List<WhatIfTrainDiff> diffs = new ArrayList<>();
        int heldCount = 0;

        if (result.getReallocationDecisions() != null) {
            for (AllocationDecision dec : result.getReallocationDecisions()) {
                Train orig = originalTrainMap.get(dec.getTrainId());
                String tNum = orig != null ? orig.getTrainNumber() : dec.getTrainNumber();
                String tName = orig != null ? orig.getTrainName() : dec.getTrainName();
                Integer beforePlat = orig != null ? orig.getAssignedPlatformNumber() : null;
                Integer afterPlat = dec.isAllocated() ? dec.getSelectedPlatformNumber() : null;
                int beforeDelay = orig != null && orig.getDelayMinutes() != null ? orig.getDelayMinutes() : 0;
                int afterDelay = dec.getEstimatedDelayMinutes();
                if (perturbedTrain != null && perturbedTrain.getTrainId().equals(dec.getTrainId())) {
                    afterDelay = Math.max(afterDelay, beforeDelay + perturbationDelay);
                }
                String beforeStatus = orig != null && orig.getStatus() != null ? orig.getStatus().name() : "SCHEDULED";
                String afterStatus;
                boolean isHeld = false;
                boolean isReallocated = false;

                if (!dec.isAllocated()) {
                    afterStatus = "HELD";
                    isHeld = true;
                    heldCount++;
                } else if (beforePlat != null && !beforePlat.equals(afterPlat)) {
                    afterStatus = "REALLOCATED";
                    isReallocated = true;
                } else {
                    afterStatus = "CONFIRMED";
                }

                diffs.add(new WhatIfTrainDiff(
                        dec.getTrainId(),
                        tNum,
                        tName,
                        beforePlat,
                        afterPlat,
                        beforeDelay,
                        afterDelay,
                        beforeStatus,
                        afterStatus,
                        dec.getDetailedBreakdown() != null ? dec.getDetailedBreakdown() : dec.getPrimaryReason(),
                        isHeld,
                        isReallocated
                ));
            }
        }

        if (perturbedTrain != null && diffs.stream().noneMatch(d -> d.getTrainId().equals(perturbedTrain.getTrainId()))) {
            Train orig = originalTrainMap.get(perturbedTrain.getTrainId());
            int beforeDelay = orig != null && orig.getDelayMinutes() != null ? orig.getDelayMinutes() : 0;
            Integer plat = orig != null ? orig.getAssignedPlatformNumber() : null;
            String beforeStatus = orig != null && orig.getStatus() != null ? orig.getStatus().name() : "SCHEDULED";
            diffs.add(0, new WhatIfTrainDiff(
                    perturbedTrain.getTrainId(),
                    perturbedTrain.getTrainNumber(),
                    perturbedTrain.getTrainName(),
                    plat,
                    plat,
                    beforeDelay,
                    beforeDelay + perturbationDelay,
                    beforeStatus,
                    beforeStatus.equals("SCHEDULED") ? "DELAYED" : beforeStatus,
                    "Injected perturbation delay of +" + perturbationDelay + " min (no platform conflicts triggered)",
                    false,
                    false
            ));
        }

        preview.setHeldCount(heldCount);
        preview.setDiffs(diffs);
        return preview;
    }

    private Train cloneTrain(Train src) {
        Train copy = new Train();
        copy.setTrainId(src.getTrainId());
        copy.setTrainNumber(src.getTrainNumber());
        copy.setTrainName(src.getTrainName());
        copy.setTrainType(src.getTrainType());
        copy.setTrainCategory(src.getTrainCategory());
        copy.setStopStationsStr(src.getStopStationsStr());
        if (src.getStopStations() != null) {
            copy.setStopStations(new ArrayList<>(src.getStopStations()));
        }
        copy.setNextStopStationCode(src.getNextStopStationCode());
        copy.setSourceStation(src.getSourceStation());
        copy.setDestinationStation(src.getDestinationStation());
        copy.setSimulationPriority(src.getSimulationPriority());
        copy.setScheduledArrival(src.getScheduledArrival());
        copy.setScheduledDeparture(src.getScheduledDeparture());
        copy.setActualArrival(src.getActualArrival());
        copy.setActualDeparture(src.getActualDeparture());
        copy.setDelayMinutes(src.getDelayMinutes());
        copy.setStatus(src.getStatus());
        copy.setCurrentStationCode(src.getCurrentStationCode());
        copy.setNextStationCode(src.getNextStationCode());
        copy.setLastStationCode(src.getLastStationCode());
        copy.setUpcomingStationCode(src.getUpcomingStationCode());
        copy.setDirection(src.getDirection());
        copy.setTargetStationCode(src.getTargetStationCode());
        copy.setAssignedPlatformId(src.getAssignedPlatformId());
        copy.setAssignedPlatformNumber(src.getAssignedPlatformNumber());
        copy.setCurrentBlockId(src.getCurrentBlockId());
        copy.setNextSignalId(src.getNextSignalId());
        copy.setSignalAspect(src.getSignalAspect());
        copy.setWaitingReason(src.getWaitingReason());
        copy.setProgressPercent(src.getProgressPercent());
        copy.setSpeedKmH(src.getSpeedKmH());
        copy.setIsActive(src.getIsActive());
        copy.setIsSimulated(src.getIsSimulated());
        return copy;
    }

    private Platform clonePlatform(Platform src) {
        Platform copy = new Platform();
        copy.setPlatformId(src.getPlatformId());
        copy.setStationCode(src.getStationCode());
        copy.setPlatformNumber(src.getPlatformNumber());
        copy.setLineType(src.getLineType());
        if (src.getSupportedTrainTypes() != null) {
            copy.setSupportedTrainTypes(new HashSet<>(src.getSupportedTrainTypes()));
        }
        copy.setIsOccupied(src.getIsOccupied());
        copy.setIsClosed(src.getIsClosed());
        copy.setClosureReason(src.getClosureReason());
        copy.setCurrentTrainId(src.getCurrentTrainId());
        copy.setCurrentTrainNumber(src.getCurrentTrainNumber());
        copy.setOccupancyStart(src.getOccupancyStart());
        copy.setOccupancyEnd(src.getOccupancyEnd());
        copy.setDirection(src.getDirection());
        return copy;
    }

    private PlatformAllocation cloneAllocation(PlatformAllocation src) {
        PlatformAllocation copy = new PlatformAllocation();
        copy.setAllocationId(src.getAllocationId());
        copy.setTrainId(src.getTrainId());
        copy.setTrainNumber(src.getTrainNumber());
        copy.setTrainName(src.getTrainName());
        copy.setPlatformId(src.getPlatformId());
        copy.setPlatformNumber(src.getPlatformNumber());
        copy.setStationId(src.getStationId());
        copy.setStationCode(src.getStationCode());
        copy.setStartTime(src.getStartTime());
        copy.setEndTime(src.getEndTime());
        copy.setAllocatedAt(src.getAllocatedAt());
        copy.setStatus(src.getStatus());
        copy.setSelectionReason(src.getSelectionReason());
        copy.setScoringBreakdown(src.getScoringBreakdown());
        copy.setDelayMinutes(src.getDelayMinutes());
        return copy;
    }
}
