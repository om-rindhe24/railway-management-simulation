package com.railway.simulator.service;

import com.railway.simulator.domain.*;
import com.railway.simulator.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class AnalyticsService {

    private final TrainRepository trainRepository;
    private final PlatformRepository platformRepository;
    private final AllocationHistoryRepository historyRepository;
    private final PlatformAllocationRepository allocationRepository;

    public AnalyticsService(TrainRepository trainRepository,
                            PlatformRepository platformRepository,
                            AllocationHistoryRepository historyRepository,
                            PlatformAllocationRepository allocationRepository) {
        this.trainRepository = trainRepository;
        this.platformRepository = platformRepository;
        this.historyRepository = historyRepository;
        this.allocationRepository = allocationRepository;
    }

    public Map<String, Object> getDashboardKpis() {
        List<Train> activeTrains = trainRepository.findByIsActiveTrue();
        List<Platform> platforms = platformRepository.findAll();
        List<AllocationHistory> history = historyRepository.findAll();

        long totalActiveTrains = activeTrains.size();
        long trainsAtPlatform = activeTrains.stream().filter(t -> t.getStatus() == TrainStatus.AT_PLATFORM).count();
        long delayedTrains = activeTrains.stream().filter(t -> t.getDelayMinutes() > 0).count();
        double avgDelayMinutes = activeTrains.stream().mapToInt(Train::getDelayMinutes).average().orElse(0.0);

        long totalPlatforms = platforms.size();
        long occupiedPlatforms = platforms.stream().filter(Platform::getIsOccupied).count();
        long closedPlatforms = platforms.stream().filter(Platform::getIsClosed).count();
        long availablePlatforms = totalPlatforms - occupiedPlatforms - closedPlatforms;

        double utilizationPercent = totalPlatforms > 0 ? ((double) occupiedPlatforms / totalPlatforms) * 100.0 : 0.0;
        long totalConflictsResolved = history.stream().mapToInt(AllocationHistory::getConflictsAvoided).sum();
        long totalReallocations = history.stream().filter(h -> h.getChangeType() == ChangeType.DYNAMIC_REALLOCATION).count();

        Map<String, Object> kpis = new LinkedHashMap<>();
        kpis.put("activeTrains", totalActiveTrains);
        kpis.put("trainsAtPlatform", trainsAtPlatform);
        kpis.put("delayedTrains", delayedTrains);
        kpis.put("averageDelayMinutes", Math.round(avgDelayMinutes * 10.0) / 10.0);
        kpis.put("totalPlatforms", totalPlatforms);
        kpis.put("availablePlatforms", availablePlatforms);
        kpis.put("occupiedPlatforms", occupiedPlatforms);
        kpis.put("closedPlatforms", closedPlatforms);
        kpis.put("platformUtilizationPercent", Math.round(utilizationPercent * 10.0) / 10.0);
        kpis.put("conflictsResolved", totalConflictsResolved);
        kpis.put("totalReallocations", totalReallocations);

        // Train category distribution
        Map<String, Long> categoryCount = activeTrains.stream()
                .collect(Collectors.groupingBy(t -> t.getTrainType().name(), Collectors.counting()));
        kpis.put("categoryDistribution", categoryCount);

        // Train status distribution
        Map<String, Long> statusCount = activeTrains.stream()
                .collect(Collectors.groupingBy(t -> t.getStatus().name(), Collectors.counting()));
        kpis.put("statusDistribution", statusCount);

        return kpis;
    }
}
