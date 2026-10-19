package com.railway.simulator.controller;

import com.railway.simulator.algorithm.dto.ReallocationResult;
import com.railway.simulator.controller.dto.ApiResponse;
import com.railway.simulator.controller.dto.TrainDelayRequest;
import com.railway.simulator.domain.Train;
import com.railway.simulator.domain.TrainStatus;
import com.railway.simulator.domain.TrainType;
import com.railway.simulator.service.AllocationService;
import com.railway.simulator.service.TrainService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/trains")
public class TrainController {

    private final TrainService trainService;
    private final AllocationService allocationService;

    public TrainController(TrainService trainService, AllocationService allocationService) {
        this.trainService = trainService;
        this.allocationService = allocationService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<Train>>> getTrains(
            @RequestParam(required = false) String station,
            @RequestParam(required = false) TrainType type,
            @RequestParam(required = false) TrainStatus status) {

        List<Train> trains;
        if (station != null && !station.isBlank()) {
            trains = trainService.getTrainsByStation(station);
        } else if (type != null) {
            trains = trainService.getTrainsByType(type);
        } else if (status != null) {
            trains = trainService.getTrainsByStatus(status);
        } else {
            trains = trainService.getAllTrains();
        }

        return ResponseEntity.ok(ApiResponse.ok("Retrieved trains", trains));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<Train>> getTrainById(@PathVariable Long id) {
        return trainService.getTrainById(id)
                .map(t -> ResponseEntity.ok(ApiResponse.ok("Train found", t)))
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<ApiResponse<Train>> createTrain(@Valid @RequestBody Train train) {
        Train created = trainService.createTrain(train);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Train registered into simulation", created));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<Train>> updateTrain(@PathVariable Long id, @RequestBody Train train) {
        Train updated = trainService.updateTrain(id, train);
        return ResponseEntity.ok(ApiResponse.ok("Train updated", updated));
    }

    @PutMapping("/{id}/delay")
    public ResponseEntity<ApiResponse<ReallocationResult>> injectDelay(
            @PathVariable Long id,
            @Valid @RequestBody TrainDelayRequest request) {
        ReallocationResult result = allocationService.handleTrainDelay(
                id, request.getDelayMinutes(), request.getReason());
        return ResponseEntity.ok(ApiResponse.ok("Delay applied and dynamic reallocation executed", result));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Train>> cancelTrain(
            @PathVariable Long id,
            @RequestParam(required = false, defaultValue = "Manual operational cancellation") String reason) {
        Train cancelled = trainService.cancelTrain(id, reason);
        return ResponseEntity.ok(ApiResponse.ok("Train cancelled", cancelled));
    }
}
