package com.railway.simulator.controller;

import com.railway.simulator.algorithm.dto.ReallocationResult;
import com.railway.simulator.controller.dto.ApiResponse;
import com.railway.simulator.controller.dto.PlatformClosureRequest;
import com.railway.simulator.controller.validation.StationValidationUtil;
import com.railway.simulator.domain.Platform;
import com.railway.simulator.service.AllocationService;
import com.railway.simulator.service.PlatformService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/platforms")
public class PlatformController {

    private final PlatformService platformService;
    private final AllocationService allocationService;

    public PlatformController(PlatformService platformService, AllocationService allocationService) {
        this.platformService = platformService;
        this.allocationService = allocationService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<Platform>>> getPlatforms(
            @RequestParam(required = false) String stationCode) {
        if (stationCode != null && !stationCode.isBlank()) {
            StationValidationUtil.validateStationCode(stationCode);
        }
        List<Platform> platforms = (stationCode != null && !stationCode.isBlank())
                ? platformService.getPlatformsByStation(stationCode)
                : platformService.getAllPlatforms();
        return ResponseEntity.ok(ApiResponse.ok("Retrieved platforms", platforms));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<Platform>> getPlatformById(@PathVariable Long id) {
        return platformService.getPlatformById(id)
                .map(p -> ResponseEntity.ok(ApiResponse.ok("Platform found", p)))
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/{id}/close")
    public ResponseEntity<ApiResponse<ReallocationResult>> closePlatform(
            @PathVariable Long id,
            @Valid @RequestBody(required = false) PlatformClosureRequest request) {
        String reason = (request != null && request.getReason() != null)
                ? request.getReason()
                : "Simulated emergency / maintenance closure";

        ReallocationResult result = allocationService.handlePlatformClosure(id, reason);
        return ResponseEntity.ok(ApiResponse.ok("Platform closed and dynamic reallocation completed", result));
    }

    @PostMapping("/{id}/open")
    public ResponseEntity<ApiResponse<Platform>> openPlatform(@PathVariable Long id) {
        Platform opened = platformService.openPlatform(id);
        return ResponseEntity.ok(ApiResponse.ok("Platform reopened successfully", opened));
    }
}
