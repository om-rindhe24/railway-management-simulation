package com.railway.simulator.controller;

import com.railway.simulator.controller.dto.ApiResponse;
import com.railway.simulator.controller.dto.CorridorStateDto;
import com.railway.simulator.controller.dto.SimulationSpeedRequest;
import com.railway.simulator.controller.dto.SimulationStateDto;
import com.railway.simulator.service.SimulationService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/simulation")
public class SimulationController {

    private final SimulationService simulationService;

    public SimulationController(SimulationService simulationService) {
        this.simulationService = simulationService;
    }

    @GetMapping("/state")
    public ResponseEntity<ApiResponse<SimulationStateDto>> getSimulationState() {
        SimulationStateDto state = new SimulationStateDto(
                simulationService.getSimulationTime(),
                simulationService.isRunning(),
                simulationService.getSpeedMultiplier()
        );
        return ResponseEntity.ok(ApiResponse.ok("Simulation state retrieved", state));
    }

    @GetMapping("/corridor-state")
    public ResponseEntity<ApiResponse<CorridorStateDto>> getCorridorState() {
        CorridorStateDto corridorState = simulationService.getCorridorState();
        return ResponseEntity.ok(ApiResponse.ok("Authoritative corridor simulation state retrieved", corridorState));
    }

    @PostMapping("/start")
    public ResponseEntity<ApiResponse<String>> startSimulation() {
        simulationService.startSimulation();
        return ResponseEntity.ok(ApiResponse.ok("Simulation started", "Simulation clock running"));
    }

    @PostMapping("/pause")
    public ResponseEntity<ApiResponse<String>> pauseSimulation() {
        simulationService.pauseSimulation();
        return ResponseEntity.ok(ApiResponse.ok("Simulation paused", "Simulation clock paused"));
    }

    @PostMapping("/speed")
    public ResponseEntity<ApiResponse<Integer>> setSpeed(@RequestBody SimulationSpeedRequest request) {
        simulationService.setSpeed(request.getSpeed());
        return ResponseEntity.ok(ApiResponse.ok("Simulation speed updated", simulationService.getSpeedMultiplier()));
    }

    @PostMapping("/reset")
    public ResponseEntity<ApiResponse<String>> resetSimulation() {
        simulationService.resetSimulation();
        return ResponseEntity.ok(ApiResponse.ok("Simulation reset", "Simulation reset to baseline state"));
    }

    @PostMapping({"/demo", "/demo-scenario"})
    public ResponseEntity<ApiResponse<String>> runDemoScenario() {
        String msg = simulationService.runDemoScenario();
        return ResponseEntity.ok(ApiResponse.ok("Demo scenario activated", msg));
    }
}
