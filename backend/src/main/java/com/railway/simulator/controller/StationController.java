package com.railway.simulator.controller;

import com.railway.simulator.controller.dto.ApiResponse;
import com.railway.simulator.domain.Station;
import com.railway.simulator.service.StationService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/stations")
public class StationController {

    private final StationService stationService;

    public StationController(StationService stationService) {
        this.stationService = stationService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<Station>>> getAllStations() {
        return ResponseEntity.ok(ApiResponse.ok("Retrieved corridor stations", stationService.getAllStations()));
    }

    @GetMapping("/{code}")
    public ResponseEntity<ApiResponse<Station>> getStationByCode(@PathVariable String code) {
        return stationService.getStationByCode(code)
                .map(s -> ResponseEntity.ok(ApiResponse.ok("Station found", s)))
                .orElse(ResponseEntity.notFound().build());
    }
}
