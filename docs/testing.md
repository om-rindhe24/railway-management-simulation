# Testing Strategy & Verification Plan

## 1. Algorithmic Invariant Testing Principles
In accordance with Rule 13, unit tests verify algorithmic correctness and structural invariants rather than brittle hardcoded platform assumptions:
* **Compatibility Invariant**: An allocated platform must strictly belong to the set of platforms supporting the train's line type and service class.
* **Non-Overlap Invariant**: For any two trains allocated to platform $P$, their time intervals $[A_{\text{arr}} - \Delta, A_{\text{dep}} + \Delta]$ and $[B_{\text{arr}} - \Delta, B_{\text{dep}} + \Delta]$ must not intersect.
* **Priority Ordering Invariant**: Higher priority trains must never be starved or unallocated while lower priority trains occupy compatible platforms.
* **Audit Trail Invariant**: Every dynamic reallocation event must generate an immutable entry in `AllocationHistory` with valid timestamps, previous platform, new platform, and explainable rationale.

---

## 2. Unit Test Suite (`backend/src/test/java`)

### `TrainPriorityQueueTest`
* `testMinHeapOrderingByPriority()`: Verifies that Tier 1 (Emergency) is dequeued before Tier 2 (Premium) and Tier 5 (Local).
* `testTieBreakingByArrivalTime()`: Verifies that when two trains share the same priority tier, the train with the earlier scheduled arrival time is dequeued first.
* `testDynamicDelayUpdatesPriorityKey()`: Verifies that priority ordering adjusts correctly when delays are applied.

### `IntervalConflictDetectorTest`
* `testNonOverlappingIntervals()`: Sequential intervals separated by $> \text{buffer}$ return `hasConflict == false`.
* `testOverlappingIntervals()`: Intervals with overlapping departure and arrival within buffer return `hasConflict == true` with exact overlap duration.
* `testContainedIntervals()`: Shorter local train interval completely enveloped within an express train interval triggers conflict.
* `testSafetyBufferBoundary()`: Exactly touching intervals without buffer versus with 3-minute buffer correctly triggers separation requirement.

### `CorridorGraphTest`
* `testCorridorConnectivityVirarToMumbaiCentral()`: Verifies 24 nodes are connected sequentially in both UP and DOWN directions.
* `testFastVsSlowLinePath()`: Verifies that express trains traverse only fast-compatible tracks and stations.
* `testTransitTimeComputation()`: Verifies route distance and transit time calculations.

### `PlatformScoringEngineTest`
* `testClosedPlatformRejection()`: Platform with `isClosed == true` receives negative infinite score / instant rejection.
* `testIncompatibleTrainTypeRejection()`: Platform restricted to Slow Suburban locals rejects Freight / Mainline Express.
* `testExplainableScoringBreakdown()`: Output contains all 5 scoring factors and rationale.

### `DynamicReallocationEngineTest`
* `testPlatformClosureEvacuation()`: Close a configured simulation platform → detect affected allocations → identify compatible available platforms → run the platform scoring/allocation algorithm → verify that the affected train is reassigned according to the configured algorithm → verify Allocation History. Does not hard-code Platform 4 or Platform 5 as the expected result unless the test fixture explicitly defines that deterministic scenario.
* `testDelayInducedConflictResolution()`: Introducing a delay to an incoming train resolves the platform conflict according to priority ordering without hardcoding arbitrary platform targets.
* `testAllocationHistoryGeneration()`: Verifies history audit records are accurately populated.

---

## 3. Integration & End-to-End Tests
* `StationControllerTest`: Validates REST response schemas for corridor retrieval.
* `SimulationWorkflowTest`: Executes full lifecycle: load corridor -> run allocation -> inject delay -> detect conflict -> dynamic reallocation -> verify state.

---

## 4. Geographic Map & Spatial Movement Verification (Phase 10)
* **Station Coordinates Invariant**: All 24 corridor stations (`VR` to `MMCT`) return verified non-null `latitude` and `longitude` fields from `/api/stations`.
* **Spatial Monotonicity Invariant**: Southbound station latitudes decrease monotonically from Virar ($19.4538^\circ\text{N}$) to Mumbai Central ($18.9696^\circ\text{N}$).
* **Linear Interpolation Invariant**: Moving train coordinates satisfy $\text{Lat}(0) = \text{Lat}_{\text{from}}$ and $\text{Lat}(100) = \text{Lat}_{\text{to}}$ within floating-point tolerance.
* **4-Aspect Signal Integrity**: Every signal in `/api/simulation/corridor-state` maps to a valid block and renders an authentic 4-aspect aspect head without client-side aspect synthesis.
* **Build Verification**: Frontend compilation verified cleanly with `npm.cmd run build` (0 TypeScript / Vite errors, ~600ms).

