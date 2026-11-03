package com.railway.simulator.controller;

import com.railway.simulator.algorithm.dto.AllocationDecision;
import com.railway.simulator.algorithm.dto.ConflictReport;
import com.railway.simulator.controller.dto.ApiResponse;
import com.railway.simulator.controller.dto.PlatformClosureRequest;
import com.railway.simulator.controller.dto.TrainDelayRequest;
import com.railway.simulator.controller.dto.WhatIfPreviewResult;
import com.railway.simulator.controller.validation.StationValidationUtil;
import com.railway.simulator.domain.AllocationHistory;
import com.railway.simulator.domain.PlatformAllocation;
import com.railway.simulator.domain.SimulationEvent;
import com.railway.simulator.repository.SimulationEventRepository;
import com.railway.simulator.service.AllocationService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
public class AllocationController {

    private final AllocationService allocationService;
    private final SimulationEventRepository simulationEventRepository;

    public AllocationController(AllocationService allocationService, SimulationEventRepository simulationEventRepository) {
        this.allocationService = allocationService;
        this.simulationEventRepository = simulationEventRepository;
    }

    @GetMapping("/allocations")
    public ResponseEntity<ApiResponse<List<PlatformAllocation>>> getActiveAllocations(
            @RequestParam(required = false) String stationCode) {
        if (stationCode != null && !stationCode.isBlank()) {
            StationValidationUtil.validateStationCode(stationCode);
        }
        List<PlatformAllocation> allocations = allocationService.getActiveAllocations(stationCode);
        return ResponseEntity.ok(ApiResponse.ok("Retrieved active platform allocations", allocations));
    }

    @PostMapping("/allocations/run")
    public ResponseEntity<ApiResponse<List<AllocationDecision>>> runAllocation(
            @RequestParam(defaultValue = "BVI") String stationCode) {
        StationValidationUtil.validateStationCode(stationCode);
        List<AllocationDecision> decisions = allocationService.runStationAllocation(stationCode);
        return ResponseEntity.ok(ApiResponse.ok("Batch platform allocation completed for " + stationCode, decisions));
    }

    @GetMapping("/allocations/conflicts")
    public ResponseEntity<ApiResponse<List<ConflictReport>>> getConflicts(
            @RequestParam(defaultValue = "BVI") String stationCode) {
        StationValidationUtil.validateStationCode(stationCode);
        List<ConflictReport> conflicts = allocationService.detectActiveConflicts(stationCode);
        return ResponseEntity.ok(ApiResponse.ok("Conflict scan completed", conflicts));
    }

    @GetMapping("/history")
    public ResponseEntity<ApiResponse<List<AllocationHistory>>> getHistory(
            @RequestParam(required = false) String stationCode) {
        if (stationCode != null && !stationCode.isBlank()) {
            StationValidationUtil.validateStationCode(stationCode);
        }
        List<AllocationHistory> history = allocationService.getAllocationHistory(stationCode);
        return ResponseEntity.ok(ApiResponse.ok("Retrieved allocation history", history));
    }

    @GetMapping({"/events", "/audit/events"})
    public ResponseEntity<ApiResponse<List<SimulationEvent>>> getEvents(
            @RequestParam(required = false) String stationCode,
            @RequestParam(required = false) String eventType) {
        if (stationCode != null && !stationCode.isBlank()) {
            StationValidationUtil.validateStationCode(stationCode);
        }
        List<SimulationEvent> events = simulationEventRepository.findTop50ByOrderByTimestampDesc();
        if (stationCode != null && !stationCode.isBlank()) {
            events = events.stream().filter(e -> stationCode.equalsIgnoreCase(e.getStationCode())).toList();
        }
        if (eventType != null && !eventType.isBlank()) {
            events = events.stream().filter(e -> eventType.equalsIgnoreCase(e.getEventType())).toList();
        }
        return ResponseEntity.ok(ApiResponse.ok("Retrieved simulation events", events));
    }

    @PostMapping("/allocations/what-if/trains/{trainId}/delay")
    public ResponseEntity<ApiResponse<WhatIfPreviewResult>> previewTrainDelay(
            @PathVariable Long trainId,
            @Valid @RequestBody TrainDelayRequest request) {
        WhatIfPreviewResult result = allocationService.previewTrainDelay(
                trainId, request.getDelayMinutes(), request.getReason());
        return ResponseEntity.ok(ApiResponse.ok("What-If delay cascade evaluated (isolated simulation)", result));
    }

    @PostMapping("/allocations/what-if/platforms/{platformId}/close")
    public ResponseEntity<ApiResponse<WhatIfPreviewResult>> previewPlatformClosure(
            @PathVariable Long platformId,
            @Valid @RequestBody PlatformClosureRequest request) {
        WhatIfPreviewResult result = allocationService.previewPlatformClosure(
                platformId, request.getReason());
        return ResponseEntity.ok(ApiResponse.ok("What-If platform closure evaluated (isolated simulation)", result));
    }
}
