# Educational Simulation Signalling & Track-Block Architecture Specification

> [!IMPORTANT]
> **SIMULATION SIGNALLING DISCLAIMER**:
> *This module is an educational simulation of railway signalling and track-block division designed for academic study, visualization, and decision-support modeling. It is NOT connected to, and does NOT claim to reproduce or control, real-world Indian Railways signalling, interlocking, or train protection systems (such as KAVACH, TPWS, or solid-state electronic interlocking).*

---

## 1. Executive Summary & Core Principle

### Core Principle: Single Authoritative Simulation State
There must be **ONE authoritative simulation state** hosted strictly in the backend.
- Train positions, train statuses, track-block occupancies, platform allocations, signal aspects, map visualizer nodes, delay cascades, and dynamic reallocations **must all derive from the exact same simulation state**.
- Independent frontend-only train animations, mock signal flashers, or desynchronized UI timers are **STRICTLY PROHIBITED**.
- If the simulation clock is paused, all train movements, block occupancies, and signal transitions freeze simultaneously.

---

## 2. Existing Architecture Inspection Findings

Before formulating this design, a complete inspection of the running application was performed:

| Architectural Dimension | Existing Implementation in Codebase | Signalling Layer Integration Strategy |
| :--- | :--- | :--- |
| **1. Simulation State Location** | In-memory clock in `SimulationService.java` (`simulationTime`, `isRunning`, `speedMultiplier`), combined with JPA entities in database (`Train`, `Platform`, `PlatformAllocation`). | Extend `SimulationService` to manage an in-memory & persisted `CentralSimulationState` linking `TrackBlock` and `Signal` states with trains and platforms. |
| **2. Train Position Calculation** | `Train.java` stores `currentStationCode`, `targetStationCode`, and `status`. Transitioned in `SimulationService.advanceTrainStates()` based on time delta from `actualArrival`. | Add `currentBlockId`, `nextSignalId`, `signalAspect`, and `waitingReason` to `Train`. Calculate position along discrete `TrackBlock` segments. |
| **3. Clock / Tick Mechanism** | `@Scheduled(fixedRate = 1000)` in `SimulationService.onClockTick()` advances simulation time by `speedMultiplier * 5` seconds and calls `advanceTrainStates()`. | Hook `SignalStateEngine.evaluateAllSignals()` and `advanceTrainMovementWithSignals()` into `onClockTick()` so signal constraints govern train advancement. |
| **4. Platform Occupancy Representation** | `Platform.java` flags `isOccupied`, `currentTrainId`, `currentTrainNumber`, `occupancyStart`, `occupancyEnd`. Updated on `AT_PLATFORM` and `DEPARTED`. | Platform occupancy maps directly to platform track blocks (`PLAT_{STATION}_P{NUM}`). Platform home signals query `platform.isOccupied` and `platform.isClosed`. |
| **5. Allocation / Reallocation Representation** | `AllocationService` & `DynamicReallocationEngine` assign platforms, evict on perturbation, calculate explainable scores, and log to `AllocationHistory`. | When a platform is occupied or closed, the entry signal turns `RED`. When `DynamicReallocationEngine` assigns a new open platform, the entry signal clears to `GREEN`/`YELLOW`. |
| **6. Corridor Map Train Positions** | `CorridorMap.tsx` filters trains where `currentStationCode == station.code` or `targetStationCode == station.code`, rendering badges on station cards. | Upgrade `CorridorMap.tsx` to render track blocks between stations with interactive block occupancy rectangles, signal heads (aspect colored), and moving train markers. |
| **7. Existing APIs Reused** | `/api/simulation/state`, `/api/trains`, `/api/stations`, `/api/platforms`, `/api/allocations`. | Enrich `/api/simulation/state` or provide `/api/simulation/corridor-state` returning unified state (trains, blocks, signals, platforms, conflicts). Add `/api/signals`. |

---

## 3. Track-Block Model (`TrackBlock`)

Track blocks represent discrete physical sections of railway track between corridor stations and platform lines, derived directly from the existing `CorridorGraph` (which defines ~46 directional track edges between adjacent stations from Virar to Mumbai Central).

### Data Model
```
+-------------------------------------------------------------+
|                          TrackBlock                         |
+-------------------------------------------------------------+
| - blockId: String (PK, e.g. "BLK-VR-NSP-SU1", "BLK-BVI-P5") |
| - fromStationCode: String (e.g. "VR", "BVI")                |
| - toStationCode: String (e.g. "NSP", "BVI")                  |
| - lineType: LineType (SLOW_UP, FAST_UP, SLOW_DOWN, COMMON)  |
| - direction: Direction (UP, DOWN)                           |
| - blockType: BlockType (CORRIDOR_SECTION, APPROACH, PLATFORM)|
| - isOccupied: boolean                                       |
| - occupyingTrainId: Long (nullable)                         |
| - occupyingTrainNumber: String (nullable)                   |
| - isClosed: boolean (for maintenance / disruption)          |
| - lengthKm: double                                          |
| - nextBlockId: String (downstream block reference)          |
+-------------------------------------------------------------+
```

### Topology Alignment
1. **Station-to-Station Track Blocks**: Derived for each `TrackEdge` in `CorridorGraph` (e.g., Virar $\leftrightarrow$ Nalasopara Slow UP block: `BLK-VR-NSP-SU`, Fast UP block: `BLK-VR-NSP-FU`).
2. **Station Approach Blocks**: Near major interlocked stations (e.g., Dahisar $\to$ Borivali Approach: `BLK-DIC-BVI-APP-FU`).
3. **Platform Track Blocks**: For each platform at a station (e.g., Borivali Platform 5: `BLK-BVI-P5`).

---

## 4. Signal Model (`Signal`) & Simulation Aspects

Signals are placed at block boundaries protecting the entrance into downstream track blocks or platform tracks.

### Signal Aspects (Educational 4-Aspect Color-Light Simulation)
* **`RED` (Stop)**: Restrictive. Train must NOT enter the protected block.
* **`YELLOW` (Caution)**: Permissive. Next signal is at RED; prepare to stop.
* **`DOUBLE_YELLOW` (Attention / Advanced Caution)**: Permissive. Next signal is at YELLOW; run at regulated speed.
* **`GREEN` (Proceed)**: Fully Permissive. Next two or more downstream blocks are clear.

### Data Model
```
+-------------------------------------------------------------+
|                            Signal                           |
+-------------------------------------------------------------+
| - signalId: String (PK, e.g. "SIG-VR-NSP-SU", "SIG-BVI-H5") |
| - stationCode: String (associated station or section)       |
| - locationDescription: String (e.g. "Borivali UP Home Pl.5")|
| - lineType: LineType                                        |
| - direction: Direction (UP, DOWN)                           |
| - protectingBlockId: String (FK -> TrackBlock)              |
| - nextSignalId: String (downstream signal in sequence)      |
| - currentAspect: SignalAspect (RED, YELLOW, D_YELLOW, GREEN)|
| - aspectReason: String (Human-readable explanation)         |
| - isEnabled: boolean                                        |
+-------------------------------------------------------------+
```

---

## 5. Signal State Engine (`SignalStateEngine`)

A deterministic, pure Java algorithmic evaluator (`com.railway.simulator.algorithm.SignalStateEngine`) that calculates the aspect and human-readable explanation for every signal.

### Aspect Evaluation Rules
```text
For each Signal S protecting TrackBlock B:
  1. If B.isClosed == true:
       S.aspect = RED
       S.reason = "Protected block/platform is CLOSED for maintenance/incident."

  2. Else if B.isOccupied == true:
       S.aspect = RED
       S.reason = "Protected block is OCCUPIED by Train " + B.occupyingTrainNumber + "."

  3. Else if S is a Platform Home Signal and target platform P is occupied or unassigned:
       S.aspect = RED
       S.reason = "Platform " + P.number + " is currently OCCUPIED / Route locked."

  4. Else (Immediate Block B is CLEAR):
       Let nextSignal = S.nextSignal
       If nextSignal == null:
           S.aspect = GREEN
           S.reason = "Corridor clear to terminus section."
       Else if nextSignal.aspect == RED:
           S.aspect = YELLOW
           S.reason = "Next signal (" + nextSignal.id + ") is RED. Caution: Prepare to stop."
       Else if nextSignal.aspect == YELLOW:
           S.aspect = DOUBLE_YELLOW
           S.reason = "Next signal is YELLOW. Attention: Advanced caution."
       Else:
           S.aspect = GREEN
           S.reason = "Proceed: Protected block and downstream route are clear."
```

### Recalculation Triggers
The `SignalStateEngine` is executed synchronously whenever:
1. `SimulationService.onClockTick()` runs (train progression).
2. A train arrives, changes blocks, or departs.
3. A platform is closed (`/api/platforms/{id}/close`) or opened.
4. A train delay is injected (`/api/trains/{id}/delay`).
5. `DynamicReallocationEngine` assigns a new platform.
6. A simulation scenario is applied or reset.

---

## 6. Train Movement & Signal Interaction

### Enforcing Restrictive Signals
* When a train moves along the corridor towards a downstream track block:
  - If the signal protecting that block is **RED**:
    - The train **CANNOT enter** the block.
    - Status transitions to **`TrainStatus.WAITING`** (or `STOPPED_AT_SIGNAL`).
    - `train.waitingReason` is set to: `"Stopped at Signal " + signalId + " (" + signal.aspectReason + ")"`.
    - The train halts before the signal boundary.
  - When the downstream block clears (e.g. leading train departs, or platform reallocated):
    - `SignalStateEngine` turns the signal to **`YELLOW`** or **`GREEN`**.
    - The waiting train resumes movement: transitions back to `APPROACHING` / `ARRIVING`.
    - `train.waitingReason` is cleared.

### Authoritative Position Resolution
* `Train` entity is extended with:
  - `currentBlockId`: Exact block occupied.
  - `nextSignalId`: Next signal facing the train.
  - `signalAspect`: Aspect currently visible to train driver.
  - `waitingReason`: Explanation if halted.
* Map visualizer and Train Management read these exact fields from the backend DTO.

---

## 7. Platform + Signal Interconnection & Delay Cascade

```
[ Train Approaches Hub (e.g. Borivali) ]
                   |
                   v
[ Check Assigned Platform (e.g. P5) ]
     /                           \
(P5 Open & Clear)          (P5 Occupied or Closed)
     |                                 |
[ Home Signal GREEN ]        [ Home Signal RED ]
     |                                 |
[ Train Enters Platform ]    [ Train Halts with WAITING ]
     |                       [ Reason: "P5 Closed / Occupied" ]
     v                                 |
[ Normal Operations ]        [ DynamicReallocationEngine Triggered ]
                                       |
                             [ Train Reassigned to P7 ]
                                       |
                             [ Home Signal for P7 Clears to GREEN ]
                                       |
                             [ Train Resumes & Pulls into P7 ]
                                       |
                             [ AllocationHistory Audited ]
```

### Delay Cascade
1. Leading train `T1` delayed by 15 mins $\to$ dwells on block `B1`.
2. Following train `T2` approaches `B1` $\to$ signal `S1` is **RED** with reason `"Protected block occupied by T1"`.
3. `T2` stops at `S1` $\to$ status becomes `WAITING`.
4. Delay propagates to `T2` $\to$ dynamic reallocation engine evaluates options for `T2` without starving higher-priority services.

---

## 8. Frontend Live Map & Inspection Panels

### Corridor Network Map Enhancement (`CorridorMap.tsx`)
1. **Interactive Track Blocks**:
   - Linear track segments between stations divided into track blocks (`BLK-VR-NSP-SU`, etc.).
   - Clear blocks rendered with subtle track borders; occupied blocks highlighted with active train badge.
2. **Signal Aspect Indicators**:
   - Crisp, semantic signal heads at block entry points:
     - 🔴 `RED`
     - 🟡 `YELLOW`
     - 🟡🟡 `DOUBLE_YELLOW`
     - 🟢 `GREEN`
3. **Train Movement Position**:
   - Trains plotted on their authoritative `currentBlockId` or `assignedPlatformNumber`.
   - Direction indicator (UP $\downarrow$ Southbound, DOWN $\uparrow$ Northbound).
   - "HALTED" icon badge if train is stopped at a red signal.

### Train Detail Panel ("Why is this train stopped?")
Clicking any train on the map or fleet table opens an inspection drawer:
* **Train Identification**: `SIM-VB-20901 (Vande Bharat)`
* **Authoritative Location**: `Block BLK-DIC-BVI-APP-FU (Dahisar -> Borivali Fast UP)`
* **Current Status**: `WAITING` (Stopped)
* **Next Signal**: `SIG-BVI-H5 (Aspect: RED)`
* **Explanation**: `Platform 5 is closed for maintenance. Dynamic reallocation pending.`

### Signal Detail Panel
Clicking any signal on the map shows:
* **Signal ID**: `SIG-BVI-H5`
* **Aspect**: `RED` (Stop)
* **Protecting Block**: `BLK-BVI-P5 (Borivali Platform 5)`
* **Block State**: `CLOSED`
* **Downstream Section**: `Platform 5 buffer section`
* **Aspect Reason**: `Platform closed for maintenance simulation.`

---

## 9. API Extensions

### `GET /api/simulation/corridor-state` (Central Authoritative State)
Returns the complete, unified snapshot:
```json
{
  "simulationTime": "2026-09-18T10:15:00",
  "running": true,
  "speedMultiplier": 1,
  "trains": [
    {
      "trainNumber": "SIM-VB-20901",
      "status": "WAITING",
      "currentBlockId": "BLK-DIC-BVI-APP-FU",
      "nextSignalId": "SIG-BVI-H5",
      "signalAspect": "RED",
      "waitingReason": "Platform 5 occupied by SIM-SF-12953",
      "delayMinutes": 5
    }
  ],
  "trackBlocks": [
    {
      "blockId": "BLK-DIC-BVI-APP-FU",
      "isOccupied": true,
      "occupyingTrainNumber": "SIM-VB-20901",
      "isClosed": false
    }
  ],
  "signals": [
    {
      "signalId": "SIG-BVI-H5",
      "currentAspect": "RED",
      "protectingBlockId": "BLK-BVI-P5",
      "aspectReason": "Platform 5 occupied by SIM-SF-12953"
    }
  ],
  "activeConflicts": []
}
```

---

## 10. Implementation Plan & Phases

| Phase | Description | Status | Scope & Guardrails |
| :--- | :--- | :---: | :--- |
| **Phase A** | Architecture inspection & documentation | `COMPLETED` | Complete analysis, design `/project-docs/signalling.md`, update existing project docs. |
| **Phase B** | `TrackBlock` Model & Corridor Graph derivation | `COMPLETED` | Pure Java `TrackBlock` domain and CorridorGraph block generator. |
| **Phase C** | `Signal` Model & `SignalStateEngine` | `COMPLETED` | 4-Aspect evaluation logic, pure unit test suite (6 tests). |
| **Phase D** | Train Movement & Signal Enforcement | `COMPLETED` | Connect `SimulationService` clock ticks to block progression and RED stop enforcement. |
| **Phase E** | Platform & Delay Cascade Integration | `COMPLETED` | Interlock platform closures, occupancies, and delay reallocations with signal aspects and zero stale occupancy (10 tests). |
| **Phase F** | Frontend Live Map Integration | `PLANNED` | Upgrade `CorridorMap.tsx` with block lanes, signal lights, and train inspection drawer. |
| **Phase G** | What-If Operations Studio Expansion | `COMPLETED` | Add isolated scenario simulation, diff assessment, and What-If perturbation analysis. |
| **Phase H** | Full Verification & Testing | `PLANNED` | JUnit 5 invariant tests, production build (`npm run build`), updated documentation. |
