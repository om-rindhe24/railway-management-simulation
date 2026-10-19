package com.railway.simulator.controller;

import com.railway.simulator.algorithm.dto.AllocationDecision;
import com.railway.simulator.algorithm.dto.ConflictReport;
import com.railway.simulator.controller.dto.ApiResponse;
import com.railway.simulator.domain.AllocationHistory;
import com.railway.simulator.domain.PlatformAllocation;
import com.railway.simulator.domain.SimulationEvent;
import com.railway.simulator.repository.SimulationEventRepository;
import com.railway.simulator.service.AllocationService;
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
        List<PlatformAllocation> allocations = allocationService.getActiveAllocations(stationCode);
        return ResponseEntity.ok(ApiResponse.ok("Retrieved active platform allocations", allocations));
    }

    @PostMapping("/allocations/run")
    public ResponseEntity<ApiResponse<List<AllocationDecision>>> runAllocation(
            @RequestParam(defaultValue = "BVI") String stationCode) {
        List<AllocationDecision> decisions = allocationService.runStationAllocation(stationCode);
        return ResponseEntity.ok(ApiResponse.ok("Batch platform allocation completed for " + stationCode, decisions));
    }

    @GetMapping("/allocations/conflicts")
    public ResponseEntity<ApiResponse<List<ConflictReport>>> getConflicts(
            @RequestParam(defaultValue = "BVI") String stationCode) {
        List<ConflictReport> conflicts = allocationService.detectActiveConflicts(stationCode);
        return ResponseEntity.ok(ApiResponse.ok("Conflict scan completed", conflicts));
    }

    @GetMapping("/history")
    public ResponseEntity<ApiResponse<List<AllocationHistory>>> getHistory(
            @RequestParam(required = false) String stationCode) {
        List<AllocationHistory> history = allocationService.getAllocationHistory(stationCode);
        return ResponseEntity.ok(ApiResponse.ok("Retrieved allocation history", history));
    }

    @GetMapping({"/events", "/audit/events"})
    public ResponseEntity<ApiResponse<List<SimulationEvent>>> getEvents(
            @RequestParam(required = false) String stationCode,
            @RequestParam(required = false) String eventType) {
        List<SimulationEvent> events = simulationEventRepository.findTop50ByOrderByTimestampDesc();
        if (stationCode != null && !stationCode.isBlank()) {
            events = events.stream().filter(e -> stationCode.equalsIgnoreCase(e.getStationCode())).toList();
        }
        if (eventType != null && !eventType.isBlank()) {
            events = events.stream().filter(e -> eventType.equalsIgnoreCase(e.getEventType())).toList();
        }
        return ResponseEntity.ok(ApiResponse.ok("Retrieved simulation events", events));
    }
}
