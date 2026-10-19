package com.railway.simulator.algorithm;

import com.railway.simulator.algorithm.dto.ConflictReport;
import com.railway.simulator.domain.PlatformAllocation;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Interval Overlap Conflict Detector.
 *
 * Checks platform occupancy interval overlaps:
 *   Interval = [Arrival - safetyBuffer, Departure + safetyBuffer]
 *
 * Overlap Condition:
 *   Two intervals [s1, e1] and [s2, e2] conflict on the same platform if and only if:
 *   max(s1, s2) < min(e1, e2)
 *
 * Complexity:
 * - Pairwise check: O(1)
 * - Platform Scan (M allocations): O(M)
 * - Corridor Sweep Check (T total train intervals): O(T log T)
 * - Space: O(1) auxiliary for pairwise check.
 */
public class IntervalConflictDetector {

    private final int safetyBufferMinutes;

    public IntervalConflictDetector(int safetyBufferMinutes) {
        this.safetyBufferMinutes = Math.max(0, safetyBufferMinutes);
    }

    public IntervalConflictDetector() {
        this(3); // Default 3-minute safety clearance buffer
    }

    public int getSafetyBufferMinutes() {
        return safetyBufferMinutes;
    }

    /**
     * Checks if two time intervals conflict, applying the configured safety clearance buffer.
     * Conflict exists only if arr1 < dep2 + buffer AND arr2 < dep1 + buffer (does not double-expand intervals).
     */
    public boolean hasConflict(LocalDateTime arr1, LocalDateTime dep1,
                               LocalDateTime arr2, LocalDateTime dep2) {
        return arr1.isBefore(dep2.plusMinutes(safetyBufferMinutes))
                && arr2.isBefore(dep1.plusMinutes(safetyBufferMinutes));
    }

    /**
     * Calculates the duration of overlap in minutes between two intervals including safety buffer.
     * Returns 0 if there is no conflict.
     */
    public long calculateOverlapMinutes(LocalDateTime arr1, LocalDateTime dep1,
                                        LocalDateTime arr2, LocalDateTime dep2) {
        if (!hasConflict(arr1, dep1, arr2, dep2)) {
            return 0;
        }
        LocalDateTime maxStart = arr1.isAfter(arr2) ? arr1 : arr2;
        LocalDateTime minEndWithBuffer = dep1.isBefore(dep2)
                ? dep1.plusMinutes(safetyBufferMinutes)
                : dep2.plusMinutes(safetyBufferMinutes);

        if (maxStart.isBefore(minEndWithBuffer)) {
            return Math.max(1, Duration.between(maxStart, minEndWithBuffer).toMinutes());
        }
        return 0;
    }

    /**
     * Checks if a candidate interval conflicts with any active allocation on a platform.
     */
    public Optional<ConflictReport> detectPlatformConflict(Long candidatePlatformId,
                                                          Integer platformNumber,
                                                          String stationCode,
                                                          Long incomingTrainId,
                                                          String incomingTrainNumber,
                                                          LocalDateTime candidateArrival,
                                                          LocalDateTime candidateDeparture,
                                                          List<PlatformAllocation> existingAllocations) {
        if (existingAllocations == null || existingAllocations.isEmpty()) {
            return Optional.empty();
        }

        for (PlatformAllocation existing : existingAllocations) {
            // Ignore if checking against the same train (e.g. during reassessment)
            if (existing.getTrainId().equals(incomingTrainId)) {
                continue;
            }

            if (hasConflict(candidateArrival, candidateDeparture, existing.getStartTime(), existing.getEndTime())) {
                long overlapMinutes = calculateOverlapMinutes(candidateArrival, candidateDeparture,
                        existing.getStartTime(), existing.getEndTime());

                LocalDateTime s1 = candidateArrival.minusMinutes(safetyBufferMinutes);
                LocalDateTime s2 = existing.getStartTime().minusMinutes(safetyBufferMinutes);
                LocalDateTime overlapStart = s1.isAfter(s2) ? s1 : s2;

                LocalDateTime e1 = candidateDeparture.plusMinutes(safetyBufferMinutes);
                LocalDateTime e2 = existing.getEndTime().plusMinutes(safetyBufferMinutes);
                LocalDateTime overlapEnd = e1.isBefore(e2) ? e1 : e2;

                String desc = String.format("Conflict on Platform %d: Train %s overlaps with Train %s by %d mins (including %d min buffer)",
                        platformNumber, incomingTrainNumber, existing.getTrainNumber(), overlapMinutes, safetyBufferMinutes);

                return Optional.of(new ConflictReport(
                        candidatePlatformId,
                        platformNumber,
                        stationCode,
                        incomingTrainId,
                        incomingTrainNumber,
                        existing.getTrainId(),
                        existing.getTrainNumber(),
                        overlapStart,
                        overlapEnd,
                        overlapMinutes,
                        desc
                ));
            }
        }

        return Optional.empty();
    }

    /**
     * Scans a set of allocations to discover all mutual conflicts across platforms.
     */
    public List<ConflictReport> findAllConflicts(List<PlatformAllocation> allocations) {
        List<ConflictReport> reports = new ArrayList<>();
        if (allocations == null || allocations.size() < 2) return reports;

        for (int i = 0; i < allocations.size(); i++) {
            PlatformAllocation a = allocations.get(i);
            for (int j = i + 1; j < allocations.size(); j++) {
                PlatformAllocation b = allocations.get(j);

                if (a.getPlatformId().equals(b.getPlatformId()) &&
                    hasConflict(a.getStartTime(), a.getEndTime(), b.getStartTime(), b.getEndTime())) {

                    long overlap = calculateOverlapMinutes(a.getStartTime(), a.getEndTime(), b.getStartTime(), b.getEndTime());
                    reports.add(new ConflictReport(
                            a.getPlatformId(),
                            a.getPlatformNumber(),
                            a.getStationCode(),
                            b.getTrainId(),
                            b.getTrainNumber(),
                            a.getTrainId(),
                            a.getTrainNumber(),
                            a.getStartTime(),
                            b.getEndTime(),
                            overlap,
                            String.format("Conflict between Train %s and %s on Platform %d",
                                    a.getTrainNumber(), b.getTrainNumber(), a.getPlatformNumber())
                    ));
                }
            }
        }
        return reports;
    }
}
