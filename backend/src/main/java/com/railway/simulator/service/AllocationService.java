package com.railway.simulator.service;

import com.railway.simulator.algorithm.*;
import com.railway.simulator.algorithm.dto.AllocationDecision;
import com.railway.simulator.algorithm.dto.ConflictReport;
import com.railway.simulator.algorithm.dto.ReallocationResult;
import com.railway.simulator.domain.*;
import com.railway.simulator.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

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

        // Apply reallocation results to DB
        applyReallocationDecisions(result.getReallocationDecisions(), stationCode, ChangeType.PLATFORM_CLOSURE);

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
                String newBlockId = "BLK-" + stationCode.toUpperCase() + "-P" + dec.getSelectedPlatformNumber();
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

                // Record in audit log (AllocationHistory)
                historyRepository.save(new AllocationHistory(
                        train.getTrainNumber(),
                        train.getTrainName(),
                        stationCode,
                        oldPlatform,
                        dec.getSelectedPlatformNumber(),
                        changeType,
                        dec.getPrimaryReason() + " | " + dec.getDetailedBreakdown(),
                        dec.getEstimatedDelayMinutes(),
                        1
                ));

                // Record in simulation event log (SimulationEvent)
                eventRepository.save(new SimulationEvent(
                        "TRAIN_REALLOCATED",
                        Severity.WARNING,
                        String.format("Reallocated %s from %s P%s to P%s: %s",
                                train.getTrainNumber(), stationCode, oldPlatform != null ? oldPlatform : "?",
                                dec.getSelectedPlatformNumber(), dec.getPrimaryReason()),
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
                String waitMsg = String.format("Held at home signal %s: No alternative platform available after platform %d closed.", homeSigId, oldPlatform != null ? oldPlatform : 0);
                train.setWaitingReason(waitMsg);
                trainRepository.save(train);

                eventRepository.save(new SimulationEvent(
                        "TRAIN_HELD",
                        Severity.WARNING,
                        String.format("Train %s HELD at %s home signal %s: no compatible open platform after P%s closed",
                                train.getTrainNumber(), stationCode, homeSigId, oldPlatform != null ? oldPlatform : "?"),
                        train.getTrainNumber(),
                        oldPlatform,
                        stationCode
                ));
            }
        }
    }
}
