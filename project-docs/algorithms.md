# Algorithms & Data Structures Specification

## 1. Min-Heap Priority Queue (`TrainPriorityQueue`)
### Purpose
Manages upcoming train arrival events and configurable simulation priority ordering. Trains with higher operational priority (or earlier arrival times in case of tie-breaks) are evaluated first for platform scheduling.

### Data Structure
Binary Min-Heap (`java.util.PriorityQueue<TrainArrivalEvent>`) utilizing a composite comparator:
$$(\text{Simulation Priority Tier}, \text{Effective Arrival Time}, \text{Train ID})$$

### Priority Policy
Configurable Simulation Priority Policy with six configurable priority tiers:
* Tier 1: Emergency / Special Service (Weight: 100)
* Tier 2: Premium (Rajdhani/Shatabdi simulated service) (Weight: 80)
* Tier 3: Superfast Express (Weight: 60)
* Tier 4: Express / Mail (Weight: 40)
* Tier 5: Suburban Local (Weight: 30)
* Tier 6: Freight / Goods (Weight: 20)

> **Important**: The example ordering above is used internally for simulation demonstration under a configurable simulation priority ordering, and must NEVER be described as official Indian Railways priority. Priority policies are fully configurable simulation assumptions.

### Complexity
* **Insertion (Offer)**: $O(\log N)$
* **Removal (Poll)**: $O(\log N)$
* **Peek**: $O(1)$
* **Space Complexity**: $O(N)$ where $N$ is the number of active scheduled trains.

---

## 2. Corridor Graph (`CorridorGraph`)
### Purpose
Models the railway network from Virar to Mumbai Central, tracks connections, line directions (UP towards Churchgate/Mumbai Central, DOWN towards Virar), track types (Slow Line, Fast Line), and enables connectivity and transit time validation.

### Data Structure
Adjacency List representation:
* Vertices: Stations ($V = 24$)
* Directed Weighted Edges: Track links between adjacent stations with track attributes (TrackType: SLOW_UP, SLOW_DOWN, FAST_UP, FAST_DOWN), distance in km, and transit time in minutes ($E \approx 46$ directional edges).

### Complexity
* **Vertex Lookup**: $O(1)$ via `HashMap<String, StationNode>`
* **Edge Traversal / Path Finding (BFS / Dijkstra)**: $O(V + E \log V)$
* **Space Complexity**: $O(V + E)$

---

## 3. Interval Conflict Detection (`IntervalConflictDetector`)
### Purpose
Determines whether two train platform occupancy intervals overlap on the same platform, incorporating a configurable safety buffer (e.g., 3 minutes clearance time for platform clearing and passenger disembarkation/embarkation).

### Interval Representation
$$\text{Occupancy Interval} = [T_{\text{arr}} - \Delta_{\text{buffer}}, T_{\text{dep}} + \Delta_{\text{buffer}}]$$

### Overlap Condition
Two intervals $A = [s_A, e_A]$ and $B = [s_B, e_B]$ on platform $P$ conflict if and only if:
$$\max(s_A, s_B) < \min(e_A, e_B)$$

### Algorithm
* **Pairwise Check**: $O(1)$
* **Platform Conflict Scan**: Given $M$ scheduled trains on platform $P$ sorted by arrival time, checking feasibility for candidate interval takes $O(\log M + K)$ using binary search / sorted interval lookup.
* **Corridor Sweepline Conflict Detection**: Sorting $T$ train intervals and sweeping through event endpoints takes $O(T \log T)$ time and $O(T)$ auxiliary space.

---

## 4. Platform Scoring Engine (`PlatformScoringEngine`)
### Purpose
When multiple conflict-free platforms are compatible with a train's type and line, this engine calculates an explainable score to select the optimal platform, balancing line efficiency with passenger convenience.

### Scoring Factors
1. **Binary Feasibility Gates (Mandatory)**:
   * Platform must NOT be closed (`isClosed == false`).
   * Platform must support train type (`supportedTypes.contains(trainType)`).
   * Interval must be conflict-free (`hasConflict == false`).
2. **Line Type Compatibility Match**:
   * $+50$ pts if platform line type matches train operating corridor (e.g. Fast Line train on Fast Platform).
3. **Platform Idle Spacing**:
   * $+20 \times \min(1.0, \frac{\text{idleMinutes}}{15})$ to avoid back-to-back bottleneck pressure.
4. **Disruption Minimization**:
   * $+30$ pts if platform equals the originally scheduled timetable platform (avoids confusing passengers).
   * $-10 \times |\text{CandidatePlatform} - \text{ScheduledPlatform}|$ for physical walking displacement.
5. **Delay Impact Penalty**:
   * Subtraction of estimated delay if secondary holding is incurred.

### Complexity
* For station with $P$ platforms: $O(P)$ evaluations.

---

## 5. Dynamic Cascading Reallocation Engine (`DynamicReallocationEngine`)
### Purpose
Triggered whenever a platform is closed, a train is delayed by $\Delta > 0$, or an emergency train is inserted. The engine detects resulting conflicts and reallocates affected trains algorithmically without violating higher-priority train reservations.

### Execution Flow
1. **Identify Perturbation**: Delay on Train $T_i$ or Closure on Platform $P_k$.
2. **Detect Overlaps**: Scan all active allocations on affected platform(s).
3. **Prioritize Affected Set**: Enqueue affected trains into a temporary `TrainPriorityQueue` by simulation priority.
4. **Iterative Reallocation**:
   * Poll highest-priority affected train.
   * Query compatible, open platforms at the station.
   * Evaluate `PlatformScoringEngine` against existing non-conflicting allocations.
   * If a feasible platform is found, assign and commit to `AllocationHistory` with detailed explanation.
   * If no conflict-free platform exists, check if lower-priority train can be preempted/shifted or if train must be marked WAITING with estimated delay.
5. **Audit Trail**: Every reassignment records previous platform, new platform, reason, and conflict avoidance metric.

### Complexity
* $O(K \cdot P \cdot M)$ where $K$ is number of affected trains, $P$ is platform count, and $M$ is allocations per platform. Typically $K \le 5$, $P \le 8$, resulting in $< 5\text{ ms}$ real-time response.

---

## 6. Simulation Signalling State Engine (`SignalStateEngine`)
### Purpose
Determines the operational aspect (`RED`, `YELLOW`, `DOUBLE_YELLOW`, `GREEN`) and human-readable explanation for every signal along the corridor based on track-block occupancies, downstream route clarity, and platform availability.

### Algorithmic Rules (4-Aspect Color Light Simulation)
1. **Restrictive Gate (RED)**: If immediate protected track block $B$ is occupied, or if target platform is closed or occupied:
   $$\text{Aspect} = \text{RED}, \quad \text{Reason} = \text{"Protected block/platform is occupied/closed."}$$
2. **Caution Gate (YELLOW)**: If block $B$ is clear, but the next sequential signal $S_{\text{next}}$ is RED:
   $$\text{Aspect} = \text{YELLOW}, \quad \text{Reason} = \text{"Next signal is RED. Prepare to stop."}$$
3. **Attention Gate (DOUBLE_YELLOW)**: If block $B$ is clear, next signal is YELLOW, and downstream signal is RED:
   $$\text{Aspect} = \text{DOUBLE\_YELLOW}, \quad \text{Reason} = \text{"Next signal is YELLOW. Advanced caution."}$$
4. **Proceed Gate (GREEN)**: If block $B$ and at least two downstream sections are clear:
   $$\text{Aspect} = \text{GREEN}, \quad \text{Reason} = \text{"Proceed: Route is clear."}$$

### Complexity
* For $S$ signals and sequential topology: $O(S)$ deterministic linear evaluation.
* Real-time execution: $< 1\text{ ms}$.
* Note: This is an educational simulation algorithm and does not claim to control real railway hardware.
