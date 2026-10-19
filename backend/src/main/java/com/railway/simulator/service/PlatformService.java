package com.railway.simulator.service;

import com.railway.simulator.domain.Platform;
import com.railway.simulator.domain.Severity;
import com.railway.simulator.domain.SimulationEvent;
import com.railway.simulator.repository.PlatformRepository;
import com.railway.simulator.repository.SimulationEventRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@Transactional(readOnly = true)
public class PlatformService {

    private final PlatformRepository platformRepository;
    private final SimulationEventRepository eventRepository;
    private final SimulationService simulationService;

    public PlatformService(PlatformRepository platformRepository,
                           SimulationEventRepository eventRepository,
                           @org.springframework.context.annotation.Lazy SimulationService simulationService) {
        this.platformRepository = platformRepository;
        this.eventRepository = eventRepository;
        this.simulationService = simulationService;
    }

    public List<Platform> getAllPlatforms() {
        return platformRepository.findAll();
    }

    public List<Platform> getPlatformsByStation(String stationCode) {
        return platformRepository.findByStationCodeOrderByPlatformNumberAsc(stationCode.toUpperCase());
    }

    public Optional<Platform> getPlatformById(Long platformId) {
        return platformRepository.findById(platformId);
    }

    public Optional<Platform> getPlatformByStationAndNumber(String stationCode, Integer platformNumber) {
        return platformRepository.findByStationCodeAndPlatformNumber(stationCode.toUpperCase(), platformNumber);
    }

    @Transactional
    public Platform closePlatform(Long platformId, String reason) {
        Platform platform = platformRepository.findById(platformId)
                .orElseThrow(() -> new IllegalArgumentException("Platform not found with id: " + platformId));

        platform.setIsClosed(true);
        platform.setClosureReason(reason != null ? reason : "Temporary operational maintenance");
        Platform saved = platformRepository.save(platform);

        if (simulationService != null) {
            simulationService.syncSignallingState();
        }

        eventRepository.save(new SimulationEvent(
                "PLATFORM_CLOSED",
                Severity.WARNING,
                String.format("Platform %d at %s closed: %s", platform.getPlatformNumber(), platform.getStationCode(), platform.getClosureReason()),
                null,
                platform.getPlatformNumber(),
                platform.getStationCode()
        ));

        return saved;
    }

    @Transactional
    public Platform openPlatform(Long platformId) {
        Platform platform = platformRepository.findById(platformId)
                .orElseThrow(() -> new IllegalArgumentException("Platform not found with id: " + platformId));

        platform.setIsClosed(false);
        platform.setClosureReason(null);
        Platform saved = platformRepository.save(platform);

        if (simulationService != null) {
            simulationService.syncSignallingState();
        }

        eventRepository.save(new SimulationEvent(
                "PLATFORM_OPENED",
                Severity.INFO,
                String.format("Platform %d at %s reopened for regular traffic", platform.getPlatformNumber(), platform.getStationCode()),
                null,
                platform.getPlatformNumber(),
                platform.getStationCode()
        ));

        return saved;
    }

    @Transactional
    public Platform savePlatform(Platform platform) {
        return platformRepository.save(platform);
    }
}
