package com.railway.simulator.algorithm;

import com.railway.simulator.algorithm.dto.AllocationDecision;
import com.railway.simulator.algorithm.dto.ConflictReport;
import com.railway.simulator.algorithm.dto.ReallocationResult;
import com.railway.simulator.domain.Platform;
import com.railway.simulator.domain.PlatformAllocation;
import com.railway.simulator.domain.Train;
import com.railway.simulator.domain.TrainStatus;

import java.time.LocalDateTime;
import java.util.*;

/**
 * Dynamic Cascading Reallocation Engine.
 *
 * Algorithm Flow:
 * 1. Perturbation Trigger (Train Delay, Platform Closure, Emergency Insertion).
 * 2. Scan and identify all conflicting or evicted allocations.
 * 3. Enqueue affected trains into a temporary TrainPriorityQueue (Min-Heap).
 * 4. Dequeue in strict priority order (higher priority gets first choice of alternative platforms).
 * 5. Score all available open platforms algorithmically via PlatformAllocationEngine.
 * 6. Commit best feasible reassignment without violating higher-priority constraints.
 * 7. Track resolved conflicts and generate explainable audit trail.
 */
public class DynamicReallocationEngine {

    private final PlatformAllocationEngine allocationEngine;
    private final IntervalConflictDetector conflictDetector;

    public DynamicReallocationEngine(PlatformAllocationEngine allocationEngine,
                                     IntervalConflictDetector conflictDetector) {
        this.allocationEngine = allocationEngine;
        this.conflictDetector = conflictDetector;
    }

    public DynamicReallocationEngine() {
        IntervalConflictDetector detector = new IntervalConflictDetector(3);
        this.conflictDetector = detector;
        this.allocationEngine = new PlatformAllocationEngine(new PlatformScoringEngine(detector));
    }

    /**
     * Resolves conflicts caused by platform closure.
     */
    public ReallocationResult handlePlatformClosure(Long closedPlatformId,
                                                   Integer closedPlatformNumber,
                                                   String stationCode,
                                                   List<Platform> stationPlatforms,
                                                   List<Train> stationTrains,
                                                   List<PlatformAllocation> allStationAllocations) {
        List<Train> evictedTrains = new ArrayList<>();
        Map<Long, List<PlatformAllocation>> allocationsByPlatform = new HashMap<>();

        // Group allocations by platform, excluding the closed platform
        for (PlatformAllocation alloc : allStationAllocations) {
            if (alloc.getPlatformId().equals(closedPlatformId)) {
                // Find matching train
                stationTrains.stream()
                        .filter(t -> t.getTrainId().equals(alloc.getTrainId()))
                        .findFirst()
                        .ifPresent(t -> {
                            if (!evictedTrains.contains(t)) {
                                evictedTrains.add(t);
                            }
                        });
            } else {
                allocationsByPlatform.computeIfAbsent(alloc.getPlatformId(), k -> new ArrayList<>()).add(alloc);
            }
        }

        // Also include any active trains assigned to this platform that may not have an active allocation row
        for (Train t : stationTrains) {
            if ((closedPlatformId.equals(t.getAssignedPlatformId()) || Objects.equals(closedPlatformNumber, t.getAssignedPlatformNumber()))
                    && !evictedTrains.contains(t)) {
                evictedTrains.add(t);
            }
        }

        // Requirement (d): Train already AT_PLATFORM when closed completes dwell and departs normally.
        // It must NOT be evicted or reallocated away to another platform.
        evictedTrains.removeIf(t -> t.getStatus() == TrainStatus.AT_PLATFORM);

        // Filter available candidate platforms strictly excluding the closed platform and any already closed platforms
        List<Platform> availablePlatforms = stationPlatforms.stream()
                .filter(p -> !p.getPlatformId().equals(closedPlatformId) && !Boolean.TRUE.equals(p.getIsClosed()))
                .toList();

        return resolveAffectedTrains(evictedTrains, availablePlatforms, allocationsByPlatform,
                "Platform " + closedPlatformNumber + " closed for maintenance/incident simulation");
    }

    /**
     * Resolves conflicts caused by train delay.
     */
    public ReallocationResult handleTrainDelay(Train delayedTrain,
                                              int delayMinutes,
                                              List<Platform> stationPlatforms,
                                              List<Train> stationTrains,
                                              List<PlatformAllocation> allStationAllocations) {
        List<Train> affectedTrains = new ArrayList<>();
        affectedTrains.add(delayedTrain);

        // Update delayed train effective times
        LocalDateTime newArr = delayedTrain.getScheduledArrival().plusMinutes(delayMinutes);
        LocalDateTime newDep = delayedTrain.getScheduledDeparture().plusMinutes(delayMinutes);
        delayedTrain.setActualArrival(newArr);
        delayedTrain.setActualDeparture(newDep);
        delayedTrain.setDelayMinutes(delayMinutes);

        // Check if new interval conflicts with any existing train on its assigned platform
        Long assignedPlatformId = delayedTrain.getAssignedPlatformId();
        if (assignedPlatformId != null) {
            for (PlatformAllocation alloc : allStationAllocations) {
                if (alloc.getPlatformId().equals(assignedPlatformId) && !alloc.getTrainId().equals(delayedTrain.getTrainId())) {
                    if (conflictDetector.hasConflict(newArr, newDep, alloc.getStartTime(), alloc.getEndTime())) {
                        // Conflict found! Add conflicting train to affected pool
                        stationTrains.stream()
                                .filter(t -> t.getTrainId().equals(alloc.getTrainId()))
                                .findFirst()
                                .ifPresent(affectedTrains::add);
                    }
                }
            }
        }

        // Build working allocation map excluding affected trains
        Map<Long, List<PlatformAllocation>> workingAllocations = new HashMap<>();
        Set<Long> affectedIds = new HashSet<>();
        for (Train t : affectedTrains) {
            affectedIds.add(t.getTrainId());
        }

        for (PlatformAllocation alloc : allStationAllocations) {
            if (!affectedIds.contains(alloc.getTrainId())) {
                workingAllocations.computeIfAbsent(alloc.getPlatformId(), k -> new ArrayList<>()).add(alloc);
            }
        }

        List<Platform> availablePlatforms = stationPlatforms.stream()
                .filter(p -> !Boolean.TRUE.equals(p.getIsClosed()))
                .toList();

        return resolveAffectedTrains(affectedTrains, availablePlatforms, workingAllocations,
                String.format("Delay of %d min on Train %s triggered platform conflict", delayMinutes, delayedTrain.getTrainNumber()));
    }

    /**
     * Core dynamic reallocation solver using Min-Heap priority queue.
     */
    private ReallocationResult resolveAffectedTrains(List<Train> affectedTrains,
                                                    List<Platform> stationPlatforms,
                                                    Map<Long, List<PlatformAllocation>> workingAllocations,
                                                    String causeDescription) {
        if (affectedTrains == null || affectedTrains.isEmpty()) {
            return new ReallocationResult(0, 0, 0, 0, 0,
                    Collections.emptyList(), "No trains affected by perturbation.");
        }

        // Priority Queue (Min-Heap) orders affected trains: highest simulation priority first!
        TrainPriorityQueue priorityQueue = new TrainPriorityQueue();
        for (Train t : affectedTrains) {
            priorityQueue.offer(t);
        }

        List<AllocationDecision> decisions = new ArrayList<>();
        int reallocatedCount = 0;
        int waitingCount = 0;
        int additionalDelay = 0;

        while (!priorityQueue.isEmpty()) {
            Train candidateTrain = priorityQueue.poll();
            AllocationDecision decision = allocationEngine.allocatePlatform(candidateTrain, stationPlatforms, workingAllocations);
            decisions.add(decision);

            if (decision.isAllocated()) {
                reallocatedCount++;
                // Commit temporary allocation to working state so subsequent lower-priority trains see it
                PlatformAllocation tempAlloc = new PlatformAllocation(
                        candidateTrain.getTrainId(),
                        candidateTrain.getTrainNumber(),
                        candidateTrain.getTrainName(),
                        decision.getSelectedPlatformId(),
                        decision.getSelectedPlatformNumber(),
                        1L,
                        candidateTrain.getTargetStationCode(),
                        candidateTrain.getActualArrival(),
                        candidateTrain.getActualDeparture(),
                        decision.getPrimaryReason(),
                        decision.getDetailedBreakdown(),
                        candidateTrain.getDelayMinutes()
                );
                workingAllocations.computeIfAbsent(decision.getSelectedPlatformId(), k -> new ArrayList<>()).add(tempAlloc);
            } else {
                waitingCount++;
                additionalDelay += decision.getEstimatedDelayMinutes();
            }
        }

        String summary = String.format(
                "Dynamic Reallocation completed: %d affected, %d reallocated, %d waiting. Cause: %s",
                affectedTrains.size(), reallocatedCount, waitingCount, causeDescription
        );

        return new ReallocationResult(
                affectedTrains.size(), reallocatedCount, waitingCount,
                additionalDelay, reallocatedCount, decisions, summary
        );
    }
}
