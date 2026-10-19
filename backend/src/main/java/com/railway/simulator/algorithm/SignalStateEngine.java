package com.railway.simulator.algorithm;

import com.railway.simulator.domain.BlockType;
import com.railway.simulator.domain.Platform;
import com.railway.simulator.domain.Signal;
import com.railway.simulator.domain.SignalAspect;
import com.railway.simulator.domain.TrackBlock;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Deterministic Signal State Engine for the educational railway simulation corridor.
 * Evaluates signal aspects and human-readable explanations from track block occupancy,
 * downstream route conditions, platform availability, and platform starter exit gates.
 *
 * NOTE: Educational simulation model only. Does not reproduce official Indian Railways rules.
 */
public class SignalStateEngine {

    /**
     * Evaluates the aspect and reason for a single signal given the block and signal topologies.
     */
    public void evaluateSignal(Signal signal,
                               Map<String, TrackBlock> blockMap,
                               Map<String, Signal> signalMap,
                               Map<String, Platform> platformMap) {
        evaluateSignal(signal, blockMap, signalMap, platformMap, null);
    }

    public void evaluateSignal(Signal signal,
                               Map<String, TrackBlock> blockMap,
                               Map<String, Signal> signalMap,
                               Map<String, Platform> platformMap,
                               LocalDateTime simulationTime) {
        if (!signal.isEnabled()) {
            signal.setCurrentAspect(SignalAspect.RED);
            signal.setAspectReason("Signal disabled for simulation maintenance");
            return;
        }

        // Special handling for Platform Starter (Exit) Signals
        if (signal.getSignalId().endsWith("-STARTER")) {
            evaluateStarterSignal(signal, blockMap, signalMap, platformMap, simulationTime);
            return;
        }

        String protectedBlockId = signal.getProtectedBlockId();
        if (protectedBlockId == null || !blockMap.containsKey(protectedBlockId)) {
            signal.setCurrentAspect(SignalAspect.RED);
            signal.setAspectReason("Protected block configuration missing");
            return;
        }

        TrackBlock protectedBlock = blockMap.get(protectedBlockId);

        // 1. Immediate Protected Block Closed Gate (RED)
        if (protectedBlock.isClosed()) {
            signal.setCurrentAspect(SignalAspect.RED);
            signal.setAspectReason("Protected block " + protectedBlockId + " is CLOSED for track maintenance");
            return;
        }

        // 2. Immediate Protected Block Occupied Gate (RED)
        if (protectedBlock.isOccupied()) {
            signal.setCurrentAspect(SignalAspect.RED);
            String occupyingInfo = protectedBlock.getOccupyingTrainNumber() != null
                    ? " by train " + protectedBlock.getOccupyingTrainNumber()
                    : "";
            signal.setAspectReason("Protected block " + protectedBlockId + " is OCCUPIED" + occupyingInfo);
            return;
        }

        // 3. Platform Block Interlocking Gate (RED for Home Signal)
        if (protectedBlock.getBlockType() == BlockType.PLATFORM && platformMap != null) {
            Platform platform = platformMap.get(protectedBlockId);
            if (platform != null) {
                if (platform.getIsClosed()) {
                    signal.setCurrentAspect(SignalAspect.RED);
                    signal.setAspectReason("Platform " + platform.getPlatformNumber() + " is CLOSED for maintenance");
                    return;
                }
                if (platform.getIsOccupied()) {
                    signal.setCurrentAspect(SignalAspect.RED);
                    String trainInfo = platform.getCurrentTrainNumber() != null ? " by train " + platform.getCurrentTrainNumber() : "";
                    signal.setAspectReason("Platform " + platform.getPlatformNumber() + " is OCCUPIED" + trainInfo);
                    return;
                }
                signal.setCurrentAspect(SignalAspect.GREEN);
                signal.setAspectReason("Platform route clear: platform open and unoccupied");
                return;
            }
        }

        // 4. Immediate Block is CLEAR: Look ahead to downstream blocks
        String nextSignalId = signal.getNextSignalId();
        if (nextSignalId == null || !signalMap.containsKey(nextSignalId)) {
            // End of monitored corridor or entering clear terminal section
            signal.setCurrentAspect(SignalAspect.GREEN);
            signal.setAspectReason("Route clear: Terminal/boundary section clear");
            return;
        }

        Signal nextSignal = signalMap.get(nextSignalId);
        String nextProtectedBlockId = nextSignal.getProtectedBlockId();
        TrackBlock nextBlock = nextProtectedBlockId != null ? blockMap.get(nextProtectedBlockId) : null;

        boolean nextBlockBlocked = false;
        String nextBlockReason = "";
        if (nextBlock != null) {
            if (nextBlock.isClosed()) {
                nextBlockBlocked = true;
                nextBlockReason = "closed";
            } else if (nextBlock.isOccupied()) {
                nextBlockBlocked = true;
                nextBlockReason = "occupied by " + (nextBlock.getOccupyingTrainNumber() != null ? nextBlock.getOccupyingTrainNumber() : "train");
            } else if (nextBlock.getBlockType() == BlockType.PLATFORM && platformMap != null) {
                Platform p = platformMap.get(nextProtectedBlockId);
                if (p != null && (p.getIsClosed() || p.getIsOccupied())) {
                    nextBlockBlocked = true;
                    nextBlockReason = p.getIsClosed() ? "platform closed" : "platform occupied";
                }
            }
        }

        if (nextBlockBlocked || (nextSignal.getCurrentAspect() == SignalAspect.RED)) {
            // Block 1 step ahead is blocked -> Caution (YELLOW)
            signal.setCurrentAspect(SignalAspect.YELLOW);
            signal.setAspectReason("Next signal " + nextSignalId + " is RED" + (nextBlockReason.isEmpty() ? "" : " (protected block " + nextBlockReason + ")"));
            return;
        }

        // If next signal is YELLOW, current signal must be DOUBLE_YELLOW (4-aspect signalling rule)
        if (nextSignal.getCurrentAspect() == SignalAspect.YELLOW) {
            signal.setCurrentAspect(SignalAspect.DOUBLE_YELLOW);
            signal.setAspectReason("Next signal " + nextSignalId + " is YELLOW (downstream section occupied/closed)");
            return;
        }

        // Look two blocks ahead
        String downstreamSignalId = nextSignal.getNextSignalId();
        if (downstreamSignalId == null || !signalMap.containsKey(downstreamSignalId)) {
            signal.setCurrentAspect(SignalAspect.GREEN);
            signal.setAspectReason("Route clear: protected block and downstream line clear");
            return;
        }

        Signal downstreamSignal = signalMap.get(downstreamSignalId);
        String downstreamBlockId = downstreamSignal.getProtectedBlockId();
        TrackBlock downstreamBlock = downstreamBlockId != null ? blockMap.get(downstreamBlockId) : null;

        boolean downstreamBlockBlocked = false;
        if (downstreamBlock != null) {
            if (downstreamBlock.isClosed() || downstreamBlock.isOccupied()) {
                downstreamBlockBlocked = true;
            } else if (downstreamBlock.getBlockType() == BlockType.PLATFORM && platformMap != null) {
                Platform p = platformMap.get(downstreamBlockId);
                if (p != null && (p.getIsClosed() || p.getIsOccupied())) {
                    downstreamBlockBlocked = true;
                }
            }
        }

        if (downstreamBlockBlocked || (downstreamSignal.getCurrentAspect() == SignalAspect.RED)) {
            // Block 2 steps ahead is blocked -> Attention / Advanced Caution (DOUBLE_YELLOW)
            signal.setCurrentAspect(SignalAspect.DOUBLE_YELLOW);
            signal.setAspectReason("Next signal " + nextSignalId + " is YELLOW (downstream section occupied/closed)");
            return;
        }

        // All downstream sections clear -> GREEN
        signal.setCurrentAspect(SignalAspect.GREEN);
        signal.setAspectReason("Route clear: protected block and downstream sections available");
    }

    public void evaluateAllSignals(Collection<Signal> signals,
                                   Map<String, TrackBlock> blockMap,
                                   Map<String, Signal> signalMap,
                                   Map<String, Platform> platformMap) {
        evaluateAllSignals(signals, blockMap, signalMap, platformMap, null);
    }

    public void evaluateAllSignals(Collection<Signal> signals,
                                   Map<String, TrackBlock> blockMap,
                                   Map<String, Signal> signalMap,
                                   Map<String, Platform> platformMap,
                                   LocalDateTime simulationTime) {
        List<Signal> ordered = getDownstreamToUpstreamOrder(signals, signalMap);
        for (Signal signal : ordered) {
            evaluateSignal(signal, blockMap, signalMap, platformMap, simulationTime);
        }
    }

    private void evaluateStarterSignal(Signal signal,
                                       Map<String, TrackBlock> blockMap,
                                       Map<String, Signal> signalMap,
                                       Map<String, Platform> platformMap,
                                       LocalDateTime simulationTime) {
        String pBlockId = "BLK-" + signal.getSignalId().substring(4, signal.getSignalId().length() - 8);
        Platform platform = platformMap != null ? platformMap.get(pBlockId) : null;
        TrackBlock pBlock = blockMap != null ? blockMap.get(pBlockId) : null;

        boolean isOccupied = (platform != null && Boolean.TRUE.equals(platform.getIsOccupied())) ||
                             (pBlock != null && pBlock.isOccupied());

        // Dwell time check:
        // While a train occupies the platform, the starter signal stays RED until BOTH (a) dwell time is over and (b) next block ahead is clear.
        if (isOccupied) {
            boolean dwellOver = false;
            if (platform != null && platform.getOccupancyEnd() != null && simulationTime != null) {
                dwellOver = !simulationTime.isBefore(platform.getOccupancyEnd());
            } else if (platform == null || platform.getOccupancyEnd() == null) {
                dwellOver = true;
            }

            if (!dwellOver) {
                signal.setCurrentAspect(SignalAspect.RED);
                signal.setAspectReason("Train dwelling at platform (dwell until " + (platform != null ? platform.getOccupancyEnd() : "") + ")");
                return;
            }
        }

        // Dwell is over. Check next block ahead:
        String protectedBlockId = signal.getProtectedBlockId();
        if (protectedBlockId == null || blockMap == null || !blockMap.containsKey(protectedBlockId)) {
            // End of line / boundary terminal starter
            signal.setCurrentAspect(SignalAspect.GREEN);
            signal.setAspectReason("Platform starter clear: boundary section clear");
            return;
        }

        TrackBlock departBlock = blockMap.get(protectedBlockId);
        if (departBlock.isClosed() || departBlock.isOccupied()) {
            signal.setCurrentAspect(SignalAspect.RED);
            signal.setAspectReason("Held at platform: starter signal red, block ahead occupied");
            return;
        }

        // Depart block is clear! Starter clears and shows proper aspect based on blocks ahead.
        String nextSignalId = signal.getNextSignalId();
        if (nextSignalId == null || signalMap == null || !signalMap.containsKey(nextSignalId)) {
            signal.setCurrentAspect(SignalAspect.GREEN);
            signal.setAspectReason("Platform starter clear: departure route clear");
            return;
        }

        Signal nextSignal = signalMap.get(nextSignalId);
        String nextProtectedBlockId = nextSignal.getProtectedBlockId();
        TrackBlock nextBlock = nextProtectedBlockId != null ? blockMap.get(nextProtectedBlockId) : null;

        boolean nextBlockBlocked = false;
        if (nextBlock != null) {
            if (nextBlock.isClosed() || nextBlock.isOccupied()) {
                nextBlockBlocked = true;
            } else if (nextBlock.getBlockType() == BlockType.PLATFORM && platformMap != null) {
                Platform p = platformMap.get(nextProtectedBlockId);
                if (p != null && (Boolean.TRUE.equals(p.getIsClosed()) || Boolean.TRUE.equals(p.getIsOccupied()))) {
                    nextBlockBlocked = true;
                }
            }
        }

        if (nextBlockBlocked || (nextSignal.getCurrentAspect() == SignalAspect.RED)) {
            signal.setCurrentAspect(SignalAspect.YELLOW);
            signal.setAspectReason("Platform starter CAUTION: Next signal " + nextSignalId + " is RED");
            return;
        }

        if (nextSignal.getCurrentAspect() == SignalAspect.YELLOW) {
            signal.setCurrentAspect(SignalAspect.DOUBLE_YELLOW);
            signal.setAspectReason("Platform starter ATTENTION: Next signal " + nextSignalId + " is YELLOW");
            return;
        }

        // Check 2 blocks ahead
        String downstreamSignalId = nextSignal.getNextSignalId();
        if (downstreamSignalId == null || !signalMap.containsKey(downstreamSignalId)) {
            signal.setCurrentAspect(SignalAspect.GREEN);
            signal.setAspectReason("Platform starter CLEAR: Route and downstream line clear");
            return;
        }

        Signal downstreamSignal = signalMap.get(downstreamSignalId);
        String downstreamBlockId = downstreamSignal.getProtectedBlockId();
        TrackBlock downstreamBlock = downstreamBlockId != null ? blockMap.get(downstreamBlockId) : null;

        boolean downstreamBlockBlocked = false;
        if (downstreamBlock != null) {
            if (downstreamBlock.isClosed() || downstreamBlock.isOccupied()) {
                downstreamBlockBlocked = true;
            } else if (downstreamBlock.getBlockType() == BlockType.PLATFORM && platformMap != null) {
                Platform p = platformMap.get(downstreamBlockId);
                if (p != null && (Boolean.TRUE.equals(p.getIsClosed()) || Boolean.TRUE.equals(p.getIsOccupied()))) {
                    downstreamBlockBlocked = true;
                }
            }
        }

        if (downstreamBlockBlocked || (downstreamSignal.getCurrentAspect() == SignalAspect.RED)) {
            signal.setCurrentAspect(SignalAspect.DOUBLE_YELLOW);
            signal.setAspectReason("Platform starter ATTENTION: Next signal " + nextSignalId + " is YELLOW (downstream section occupied/closed)");
            return;
        }

        signal.setCurrentAspect(SignalAspect.GREEN);
        signal.setAspectReason("Platform starter CLEAR: Departure block and downstream sections available");
    }

    /**
     * Topologically sorts signals so downstream signals are evaluated before upstream signals.
     * Uses post-order DFS along nextSignalId: a downstream node finishes and is added to the list
     * before its upstream callers, guaranteeing downstream-to-upstream order.
     */
    public List<Signal> getDownstreamToUpstreamOrder(Collection<Signal> signals, Map<String, Signal> signalMap) {
        List<Signal> ordered = new ArrayList<>();
        Set<String> visited = new HashSet<>();

        for (Signal signal : signals) {
            if (!visited.contains(signal.getSignalId())) {
                dfsDownstream(signal, signalMap, visited, ordered);
            }
        }
        return ordered;
    }

    private void dfsDownstream(Signal current, Map<String, Signal> signalMap, Set<String> visited, List<Signal> ordered) {
        visited.add(current.getSignalId());

        String nextId = current.getNextSignalId();
        if (nextId != null && signalMap.containsKey(nextId)) {
            Signal nextSignal = signalMap.get(nextId);
            if (nextSignal != null && !visited.contains(nextId)) {
                dfsDownstream(nextSignal, signalMap, visited, ordered);
            }
        }

        // Post-order addition: downstream finishes and is added first
        ordered.add(current);
    }
}
