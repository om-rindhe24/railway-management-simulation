package com.railway.simulator.controller;

import com.railway.simulator.algorithm.dto.AllocationDecision;
import com.railway.simulator.algorithm.dto.ConflictReport;
import com.railway.simulator.controller.dto.ApiResponse;
import com.railway.simulator.domain.AllocationHistory;
import com.railway.simulator.domain.PlatformAllocation;
import com.railway.simulator.service.AllocationService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
public class AllocationController {

    private final AllocationService allocationService;

    public AllocationController(AllocationService allocationService) {
        this.allocationService = allocationService;
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
}
