package com.railway.simulator.controller;

import com.railway.simulator.algorithm.dto.AllocationDecision;
import com.railway.simulator.controller.dto.ApiResponse;
import com.railway.simulator.service.AllocationService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/reallocation")
public class ReallocationController {

    private final AllocationService allocationService;

    public ReallocationController(AllocationService allocationService) {
        this.allocationService = allocationService;
    }

    @PostMapping("/run")
    public ResponseEntity<ApiResponse<List<AllocationDecision>>> runReallocation(
            @RequestParam(defaultValue = "BVI") String stationCode) {
        List<AllocationDecision> decisions = allocationService.runStationAllocation(stationCode);
        return ResponseEntity.ok(ApiResponse.ok("Reallocation engine executed for " + stationCode, decisions));
    }
}
