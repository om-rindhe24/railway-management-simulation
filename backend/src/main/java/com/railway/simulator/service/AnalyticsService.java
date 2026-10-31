package com.railway.simulator.service;

import com.railway.simulator.domain.*;
import com.railway.simulator.repository.*;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class AnalyticsService {

    private final TrainRepository trainRepository;
    private final PlatformRepository platformRepository;
    private final AllocationHistoryRepository historyRepository;
    private final SimulationService simulationService;

    public AnalyticsService(TrainRepository trainRepository,
                            PlatformRepository platformRepository,
                            AllocationHistoryRepository historyRepository,
                            @Lazy SimulationService simulationService) {
        this.trainRepository = trainRepository;
        this.platformRepository = platformRepository;
        this.historyRepository = historyRepository;
        this.simulationService = simulationService;
    }

    public Map<String, Object> getDashboardKpis() {
        List<Train> activeTrains = trainRepository.findByIsActiveTrue();
        List<Platform> platforms = platformRepository.findAll();
        List<AllocationHistory> history = historyRepository.findAll();
        LocalDateTime simTime = simulationService != null ? simulationService.getSimulationTime() : null;

        long totalActiveTrains = activeTrains.size();
        long trainsAtPlatform = activeTrains.stream().filter(t -> t.getStatus() == TrainStatus.AT_PLATFORM).count();

        // Calculate real delays: actual arrival vs scheduled arrival, and waiting trains past scheduled arrival
        long delayedTrains = 0;
        double totalDelayMinutes = 0;
        for (Train t : activeTrains) {
            int delay = 0;
            if (t.getActualArrival() != null && t.getScheduledArrival() != null) {
                long d = Duration.between(t.getScheduledArrival(), t.getActualArrival()).toMinutes();
                if (d > 0) delay = (int) d;
            }
            if (simTime != null && t.getScheduledArrival() != null && simTime.isAfter(t.getScheduledArrival())) {
                long d = Duration.between(t.getScheduledArrival(), simTime).toMinutes();
                if (d > delay) delay = (int) d;
            }
            if (t.getDelayMinutes() != null && t.getDelayMinutes() > delay) {
                delay = t.getDelayMinutes();
            }
            if (delay > 0) {
                delayedTrains++;
                totalDelayMinutes += delay;
            }
        }
        double avgDelayMinutes = activeTrains.isEmpty() ? 0.0 : totalDelayMinutes / activeTrains.size();

        long totalPlatforms = platforms.size();
        long occupiedPlatforms = platforms.stream().filter(Platform::getIsOccupied).count();
        long closedPlatforms = platforms.stream().filter(Platform::getIsClosed).count();
        long availablePlatforms = totalPlatforms - occupiedPlatforms - closedPlatforms;

        double snapshotUtilizationPercent = (totalPlatforms - closedPlatforms) > 0
                ? ((double) occupiedPlatforms / (totalPlatforms - closedPlatforms)) * 100.0
                : 0.0;

        // Dynamic reallocations: counts DYNAMIC_REALLOCATION, TRAIN_REALLOCATED, and TRAIN_HELD audit records
        long totalConflictsResolved = history.stream().mapToInt(AllocationHistory::getConflictsAvoided).sum();
        long totalReallocations = history.stream().filter(h ->
                h.getChangeType() == ChangeType.DYNAMIC_REALLOCATION ||
                h.getChangeType() == ChangeType.TRAIN_REALLOCATED ||
                h.getChangeType() == ChangeType.TRAIN_HELD
        ).count();

        // Rolling 60-simulated-minute platform utilization metrics from simulation engine
        SimulationService.PlatformUtilizationMetrics rollingMetrics = simulationService != null
                ? simulationService.getRollingPlatformUtilizationMetrics(60)
                : null;

        double rolling60MinUtil = rollingMetrics != null ? rollingMetrics.getOverallUtilizationPercent() : 0.0;
        double occupiedPlatMinutes = rollingMetrics != null ? rollingMetrics.getTotalOccupiedPlatformMinutes() : 0.0;
        double availablePlatMinutes = rollingMetrics != null ? rollingMetrics.getTotalAvailablePlatformMinutes() : 0.0;
        int historyMinutes = rollingMetrics != null ? rollingMetrics.getHistoryMinutes() : 0;
        List<SimulationService.StationUtilizationDto> busiestStations = rollingMetrics != null ? rollingMetrics.getBusiestStations() : Collections.emptyList();
        Map<String, Double> lineTypeUtilization = rollingMetrics != null ? rollingMetrics.getLineTypeUtilization() : Collections.emptyMap();

        Map<String, Object> kpis = new LinkedHashMap<>();
        kpis.put("activeTrains", totalActiveTrains);
        kpis.put("trainsAtPlatform", trainsAtPlatform);
        kpis.put("delayedTrains", delayedTrains);
        kpis.put("averageDelayMinutes", Math.round(avgDelayMinutes * 10.0) / 10.0);
        kpis.put("totalPlatforms", totalPlatforms);
        kpis.put("availablePlatforms", availablePlatforms);
        kpis.put("occupiedPlatforms", occupiedPlatforms);
        kpis.put("closedPlatforms", closedPlatforms);

        // Utilization metrics
        kpis.put("platformUtilizationPercent", Math.round(rolling60MinUtil * 10.0) / 10.0);
        kpis.put("snapshotPlatformUtilizationPercent", Math.round(snapshotUtilizationPercent * 10.0) / 10.0);
        kpis.put("rolling60MinUtilizationPercent", Math.round(rolling60MinUtil * 10.0) / 10.0);
        kpis.put("occupiedPlatformMinutes", Math.round(occupiedPlatMinutes * 10.0) / 10.0);
        kpis.put("availablePlatformMinutes", Math.round(availablePlatMinutes * 10.0) / 10.0);
        kpis.put("historyMinutes", historyMinutes);
        kpis.put("busiestStations", busiestStations);
        kpis.put("lineTypeUtilization", lineTypeUtilization);

        kpis.put("conflictsResolved", totalConflictsResolved);
        kpis.put("totalReallocations", totalReallocations);
        kpis.put("isPeakHour", simulationService != null && simulationService.isPeakHour());

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
