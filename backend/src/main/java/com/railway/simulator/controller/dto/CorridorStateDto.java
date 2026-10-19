package com.railway.simulator.controller.dto;

import com.railway.simulator.algorithm.dto.ConflictReport;

import java.time.LocalDateTime;
import java.util.List;

public class CorridorStateDto {
    private LocalDateTime simulationTime;
    private boolean running;
    private int speedMultiplier;
    private List<TrainSnapshotDto> trains;
    private List<TrackBlockSnapshotDto> trackBlocks;
    private List<SignalSnapshotDto> signals;
    private List<PlatformSnapshotDto> platforms;
    private List<ConflictReport> activeConflicts;

    public CorridorStateDto() {}

    public CorridorStateDto(LocalDateTime simulationTime,
                            boolean running,
                            int speedMultiplier,
                            List<TrainSnapshotDto> trains,
                            List<TrackBlockSnapshotDto> trackBlocks,
                            List<SignalSnapshotDto> signals,
                            List<PlatformSnapshotDto> platforms,
                            List<ConflictReport> activeConflicts) {
        this.simulationTime = simulationTime;
        this.running = running;
        this.speedMultiplier = speedMultiplier;
        this.trains = trains;
        this.trackBlocks = trackBlocks;
        this.signals = signals;
        this.platforms = platforms;
        this.activeConflicts = activeConflicts;
    }

    public LocalDateTime getSimulationTime() { return simulationTime; }
    public void setSimulationTime(LocalDateTime simulationTime) { this.simulationTime = simulationTime; }

    public boolean isRunning() { return running; }
    public void setRunning(boolean running) { this.running = running; }

    public int getSpeedMultiplier() { return speedMultiplier; }
    public void setSpeedMultiplier(int speedMultiplier) { this.speedMultiplier = speedMultiplier; }

    public List<TrainSnapshotDto> getTrains() { return trains; }
    public void setTrains(List<TrainSnapshotDto> trains) { this.trains = trains; }

    public List<TrackBlockSnapshotDto> getTrackBlocks() { return trackBlocks; }
    public void setTrackBlocks(List<TrackBlockSnapshotDto> trackBlocks) { this.trackBlocks = trackBlocks; }

    public List<SignalSnapshotDto> getSignals() { return signals; }
    public void setSignals(List<SignalSnapshotDto> signals) { this.signals = signals; }

    public List<PlatformSnapshotDto> getPlatforms() { return platforms; }
    public void setPlatforms(List<PlatformSnapshotDto> platforms) { this.platforms = platforms; }

    public List<ConflictReport> getActiveConflicts() { return activeConflicts; }
    public void setActiveConflicts(List<ConflictReport> activeConflicts) { this.activeConflicts = activeConflicts; }
}
