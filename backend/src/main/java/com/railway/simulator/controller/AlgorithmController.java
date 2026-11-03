package com.railway.simulator.controller;

import com.railway.simulator.controller.dto.AlgorithmMetadataDto;
import com.railway.simulator.controller.dto.ApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Controller exposing technical metadata regarding internal algorithms and data structures.
 * 
 * NOTE: Unused by frontend UI following the retirement of Algorithm Lab.
 * Retained for backend technical verification, architectural documentation, and API reference.
 */
@RestController
@RequestMapping("/api/algorithms")
public class AlgorithmController {

    /**
     * Unused endpoint: GET /api/algorithms/metadata
     * Formerly consumed by the Algorithm Lab UI. Retained for technical reference.
     */
    @GetMapping("/metadata")
    public ResponseEntity<ApiResponse<AlgorithmMetadataDto>> getAlgorithmMetadata() {
        List<AlgorithmMetadataDto.AlgorithmInfo> list = List.of(
                new AlgorithmMetadataDto.AlgorithmInfo(
                        "Min-Heap Priority Queue",
                        "Binary Min-Heap (java.util.PriorityQueue)",
                        "Orders incoming trains by composite key: Simulation Priority Tier (1-6) -> Effective Arrival Time -> Train ID.",
                        "Offer: O(log N) | Poll: O(log N) | Peek: O(1)",
                        "O(N) where N is number of active trains",
                        "TrainPriorityQueue.java - Used in initial scheduling and prioritizing affected trains during reallocation.",
                        List.of(
                                "Prevents priority inversion so premium/emergency services get preferential platform access.",
                                "Tie-breaks on actual arrival time ensuring FIFO among equal-tier trains.",
                                "Dynamic updates reheapify when delay offsets change effective arrival times."
                        )
                ),
                new AlgorithmMetadataDto.AlgorithmInfo(
                        "Corridor Network Graph",
                        "Adjacency List Graph with Weighted Directed Edges",
                        "Models the 24 Western Railway stations from Virar to Mumbai Central, Slow/Fast corridor tracks, and line direction.",
                        "Lookup: O(1) | Route Validation: O(V + E) | Shortest Path: O(V + E log V)",
                        "O(V + E) where V=24 stations, E=46 directional track segments",
                        "CorridorGraph.java - Validates consecutive routes and calculates transit time between stations.",
                        List.of(
                                "Differentiates between Slow lines (suburban stops) and Fast lines (express bypasses).",
                                "Models directional track flow (UP southbound towards Mumbai Central, DOWN northbound towards Virar).",
                                "Configurable distance and transit parameters ensure realism without claiming official IR signalling data."
                        )
                ),
                new AlgorithmMetadataDto.AlgorithmInfo(
                        "Interval Overlap Conflict Detector",
                        "Sweep-Line & Interval Intersection Logic",
                        "Evaluates whether two platform occupancy intervals [Arr - Buffer, Dep + Buffer] collide on the same track.",
                        "Pairwise: O(1) | Platform Scan: O(M) | Global Sweep: O(T log T)",
                        "O(1) auxiliary per check, O(T) for event endpoints",
                        "IntervalConflictDetector.java - Core safety gate preventing train collisions and holding bottlenecks.",
                        List.of(
                                "Incorporates configurable 3-minute safety clearance buffer for passenger embarkation and track clearance.",
                                "Condition: max(s1, s2) < min(e1, e2) correctly identifies partial, touching, and fully contained overlaps.",
                                "Returns exact overlap duration and affected train IDs for automated explanation."
                        )
                ),
                new AlgorithmMetadataDto.AlgorithmInfo(
                        "Explainable Multi-Factor Platform Scoring Engine",
                        "Greedy Multi-Criteria Evaluation Function",
                        "Scores all conflict-free, open platforms balancing line match (+50), idle spacing buffer (+25), disruption penalty (-8/platform diff), and priority bonus.",
                        "O(P * M) where P = platform count at station, M = allocations per platform",
                        "O(P) auxiliary storage for candidate score breakdowns",
                        "PlatformScoringEngine.java - Eliminates magic numbers by exposing human-readable decision logs.",
                        List.of(
                                "Hard binary gates (isClosed == false, isCompatible == true, hasConflict == false) immediately filter invalid platforms.",
                                "Soft criteria score proximity to scheduled platform to minimize passenger confusion across foot overbridges.",
                                "Exposes full mathematical factor breakdown for interview inspection."
                        )
                ),
                new AlgorithmMetadataDto.AlgorithmInfo(
                        "Dynamic Cascading Reallocation Engine",
                        "Perturbation Event Handler with Configurable Simulation Priority Ordering",
                        "Triggered upon train delay, platform closure, or emergency train insertion to re-evaluate schedules and resolve ripple effects.",
                        "O(K * P * M) where K = affected trains, typically K <= 5, executing in < 5ms",
                        "O(K) temporary working heap",
                        "DynamicReallocationEngine.java - Automates platform reallocation and commits full audit history.",
                        List.of(
                                "Never hardcodes replacement platforms; optimal targets are determined strictly by algorithm.",
                                "Maintains higher-priority constraints by enqueuing evicted trains into a Min-Heap before re-allocating.",
                                "Logs every before/after platform diff, reason, and conflicts avoided into AllocationHistory."
                        )
                ),
                new AlgorithmMetadataDto.AlgorithmInfo(
                        "Simulation Signalling State Engine",
                        "Deterministic 4-Aspect Track-Block Interlocking State Machine",
                        "Evaluates operational aspects (RED, YELLOW, DOUBLE_YELLOW, GREEN) and human-readable explanations based on block occupancy, platform availability, and downstream lookahead.",
                        "O(S) deterministic linear evaluation where S is signal count (< 1ms)",
                        "O(S) memory footprint for signal aspect states",
                        "SignalStateEngine.java - Enforces signal-constrained train movement: trains halt at RED and resume when permissive.",
                        List.of(
                                "Pipeline: Track Block -> Occupancy -> SignalStateEngine -> Signal Aspect -> Train Movement.",
                                "Strict Restrictive Gate: Prohibits train entry into closed or occupied blocks/platforms under all circumstances.",
                                "Downstream Lookahead: Implements progressive caution (YELLOW) and attention (DOUBLE_YELLOW) cascades.",
                                "Educational Simulation: Configurable railway decision-support simulation, does not claim official IR signalling rules."
                        )
                )
        );

        return ResponseEntity.ok(ApiResponse.ok("Retrieved algorithm metadata", new AlgorithmMetadataDto(list)));
    }
}
