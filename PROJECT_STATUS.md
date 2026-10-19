# PROJECT STATUS & AUDIT REPORT

---

## 1. Key Files of Backend and Frontend

### Backend (Spring Boot + Java 17)
- **Domain Entities & Models**:
  - `backend/src/main/java/com/railway/simulator/domain/Train.java`: Train entity, scheduling data, dynamic movement telemetry (`currentBlockId`, `progressPercent`, `speedKmH`, `signalAspect`, `waitingReason`).
  - `backend/src/main/java/com/railway/simulator/domain/TrackBlock.java`: Block network representation (inter-station and platform blocks, occupancy, length, directional line type, chaining pointer `nextBlockId`).
  - `backend/src/main/java/com/railway/simulator/domain/Signal.java`: 4-aspect signal model (`RED`, `YELLOW`, `DOUBLE_YELLOW`, `GREEN`, `protectedBlockId`, `nextSignalId`, `aspectReason`).
  - `backend/src/main/java/com/railway/simulator/domain/Platform.java` & `Station.java`: Physical 24-station corridor infrastructure from Virar (`VR`) to Mumbai Central (`MMCT`).
  - `backend/src/main/java/com/railway/simulator/domain/PlatformAllocation.java`: Record of train-to-platform assignments.
- **Algorithms & Core Logic**:
  - `backend/src/main/java/com/railway/simulator/algorithm/CorridorGraph.java`: Static corridor graph, track edge adjacency list, creates and chains 24-station `TrackBlock` and `Signal` topologies.
  - `backend/src/main/java/com/railway/simulator/algorithm/SignalStateEngine.java`: Deterministic 4-aspect signal evaluation based on block occupancy, platform state, and downstream lookahead.
  - `backend/src/main/java/com/railway/simulator/algorithm/PlatformAllocationEngine.java`: Platform selection and tie-breaking.
  - `backend/src/main/java/com/railway/simulator/algorithm/PlatformScoringEngine.java`: Scoring heuristics (penalties for train type/length mismatch, walking distance, preference rules).
  - `backend/src/main/java/com/railway/simulator/algorithm/DynamicReallocationEngine.java`: Dynamic eviction and reassignment when platforms close or trains delay.
  - `backend/src/main/java/com/railway/simulator/algorithm/IntervalConflictDetector.java`: Time interval overlap conflict detection with safety buffer.
  - `backend/src/main/java/com/railway/simulator/algorithm/TrainPriorityQueue.java`: Min-Heap priority queue ordering trains by priority score and arrival time.
- **Services & Controllers**:
  - `backend/src/main/java/com/railway/simulator/service/SimulationService.java`: Simulation clock heartbeat (`onClockTick`), train state machine (`advanceTrainStates`), signalling sync (`recalculateSignalling`), baseline reset.
  - `backend/src/main/java/com/railway/simulator/service/AllocationService.java`: Platform allocation lifecycle, delay management, platform closure handling.
  - `backend/src/main/java/com/railway/simulator/service/DataSeederService.java`: Baseline data loader (stations, platforms, trains, schedules).
  - `backend/src/main/java/com/railway/simulator/controller/SimulationController.java`: Endpoints for `/api/simulation/corridor-state`, `/start`, `/pause`, `/reset`, `/speed`.
  - `backend/src/main/java/com/railway/simulator/controller/AllocationController.java`: Endpoints for platform allocation and dynamic conflict resolution.

### Frontend (React 19 + TypeScript + Vite + Leaflet)
- **Application Shell & Layout**:
  - `frontend/src/App.tsx`: Top-level router, 1.5s corridor state polling loop, modal management, and inspected train state synchronization.
  - `frontend/src/components/Sidebar.tsx`: Navigation bar.
  - `frontend/src/components/TopBar.tsx`: Header controls (clock, speed, play/pause/reset, demo walkthrough trigger).
  - `frontend/src/components/TrainSignalDrawer.tsx`: Slide-over drawer displaying live train telemetry, signalling reasons, and track block state.
  - `frontend/src/index.css`: Global styling tokens, CSS custom properties, glassmorphism utilities (`.glass-panel`, `.surface-card`, `.spatial-glass`).
- **Core Views / Pages**:
  - `frontend/src/pages/LiveTrainSpot.tsx`: Leaflet geographic operations map across all 24 stations with OSM tiles, corridor polylines, signals, and moving trains.
  - `frontend/src/pages/CorridorMap.tsx`: Linear/schematic trackway diagram displaying track beds, signals, and stations.
  - `frontend/src/pages/Dashboard.tsx`: High-level operational overview, KPIs, and live train arrival feed.
  - `frontend/src/pages/PlatformView.tsx`: Interactive platform status grid with manual platform closure triggers.
  - `frontend/src/pages/WhatIfSimulator.tsx`: Delay injection and platform closure simulator.
  - `frontend/src/pages/AlgorithmLab.tsx`: Interactive algorithm explanation and scoring parameter tuning lab.
- **API & Data Contracts**:
  - `frontend/src/services/api.ts`: Central Axios client.
  - `frontend/src/types/index.ts`: TypeScript contracts (`CorridorStateDto`, `TrainSnapshot`, `Signal`, `TrackBlock`, `Platform`, `Station`).

---

## 2. Calculation of Train Position, Speed, ProgressPercent, and Signal Aspect

### 1. Clock Pulse & Simulation Tick
- **File**: `backend/src/main/java/com/railway/simulator/service/SimulationService.java`
- **Function**: `onClockTick()` (lines 245-255)
  - Scheduled to run every 1000ms.
  - Advances `simulationTime` by `speedMultiplier * 5` seconds per tick.
  - Invokes `advanceTrainStates()`.

### 2. Train Speed Calculation
- **File**: `backend/src/main/java/com/railway/simulator/service/SimulationService.java`
- **Function**: `advanceTrainStates()` (lines 438-461)
  - **Halted/Waiting**: If train is halted at a RED signal or waiting for platform entry, speed is set to `0.0 km/h` (line 439).
  - **Station Dwell**: If train is at platform dwell (`AT_PLATFORM`), speed is set to `0.0 km/h` (line 408).
  - **Moving**: Base speed is `65.0 km/h` for `LOCAL` trains and `85.0 km/h` for Express/Superfast/Freight (line 455).
    - If controlling signal is `YELLOW`: throttled to `35.0 km/h` (line 457).
    - If controlling signal is `DOUBLE_YELLOW`: throttled to `50.0 km/h` (line 459).

### 3. Train ProgressPercent Calculation
- **File**: `backend/src/main/java/com/railway/simulator/service/SimulationService.java`
- **Function**: `advanceTrainStates()` (lines 464-520)
  - Formula:
    - distanceCovered = (speedKmH / 3600.0) * secondsAdvanced
    - deltaPercent = (distanceCovered / blockLengthKm) * 100.0
    - newProgress = currentProgress + deltaPercent
  - Boundary transition when newProgress >= 100.0%:
    - If approaching platform: status becomes `AT_PLATFORM`, block becomes `BLK-<STATION>-P<NUM>`, and `progressPercent` resets to `50.0%` (lines 472-480).
    - If moving to next inter-station block: block becomes `nextBlockId`, and `progressPercent` resets to min(95.0, newProgress - 100.0) (lines 492-498).
    - If end of corridor: status becomes `DEPARTED`, `progressPercent = 100.0%`, speed = 0 (lines 506-512).

### 4. Train Geographic Coordinates on Map
- **File**: `frontend/src/pages/LiveTrainSpot.tsx`
- **Function**: Trains rendering effect (lines 600-662)
  - Retrieves `fromStation` and `toStation` geographic coordinates from `currentBlock`.
  - Computes parallel line offset: Fast line = -0.00035 lng, Slow line = +0.00035 lng.
  - At platform:
    - lat = stationLat, lng = stationLng + lineOffset
  - In motion between stations:
    - progress = clamp(progressPercent, 0, 100) / 100.0
    - lat = fromLat + (toLat - fromLat) * progress
    - lng = fromLng + (toLng - fromLng) * progress + lineOffset

### 5. Signal Aspect Calculation
- **File**: `backend/src/main/java/com/railway/simulator/algorithm/SignalStateEngine.java`
- **Function**: `evaluateSignal()` (lines 24-150) and `evaluateAllSignals()` (lines 155-162)
  - **RED**: If `protectedBlock.isClosed()` (line 44), `protectedBlock.isOccupied()` (line 51), or if protected block is a platform and `platform.isClosed() || platform.isOccupied()` (line 64).
  - **YELLOW**: Immediate block is clear, but lookahead block 1 step ahead (`nextBlock`) is closed or occupied (line 111).
  - **DOUBLE_YELLOW**: Immediate block and next block are clear, but lookahead block 2 steps ahead (`downstreamBlock`) is closed or occupied (line 142).
  - **GREEN**: Immediate, next, and downstream blocks are all clear (line 148).
- **Assigned to Train**: In `SimulationService.java:advanceTrainStates()`, lines 440, 449, 462 set `train.setSignalAspect(...)` based on the controlling signal.

---

## 3. Bug Audit (Train Movement, Platform Allocation, Signals, and Map)

### Bug 1: Premature Station Teleportation in Train Movement
- **File**: `backend/src/main/java/com/railway/simulator/service/SimulationService.java`
- **Lines**: 427-428, 470-477
- **Root Cause**: `isEnteringStation` condition checks `(currentBlock == null || simulationTime.isAfter(arr.minusMinutes(12)) || currentBlock.getFromStationCode().equalsIgnoreCase(targetStation))`. If a train's arrival time is within 12 minutes while it is still several blocks away up the corridor (e.g. still near Virar while heading to Borivali), `isEnteringStation` evaluates to `true`. When the train finishes its current block, line 472 immediately teleports the train into `targetPlatformBlockId`, jumping across all intermediate stations.

### Bug 2: Hardcoded Destination Terminal List Causes Premature Disappearance
- **File**: `backend/src/main/java/com/railway/simulator/service/SimulationService.java`
- **Lines**: 374-380
- **Root Cause**: Line 376 checks `|| "CCG".equals(trainDest) || "NDLS".equals(trainDest) || "ADI".equals(trainDest)`. Any train with a long-distance destination like Churchgate (`CCG`) or New Delhi (`NDLS`) has `isAtFinalDest == true` at every intermediate platform stop (e.g., Borivali, Andheri). When its platform dwell expires, it is immediately marked `DEPARTED` and permanently deleted from active corridor tracking.

### Bug 3: Chaining Only Connects Identical Line Types (Dead-End Crossovers)
- **File**: `backend/src/main/java/com/railway/simulator/algorithm/CorridorGraph.java`
- **Lines**: 284-290
- **Root Cause**: `block.setNextBlockId(nextBlockId)` strictly filters for `nextEdge.getTrackType() == line`. There are no crossover links between Fast and Slow tracks at junction stations. When a Fast train approaches a station served only by Slow platforms (or reaches the end of the Fast line section), `nextBlockId` is null. Line 506 in `SimulationService` treats `nextBlockId == null` as the end of the line, abruptly marking the train `DEPARTED`.

### Bug 4: Asymmetric Doubled Safety Buffer in Conflict Detector
- **File**: `backend/src/main/java/com/railway/simulator/algorithm/IntervalConflictDetector.java`
- **Lines**: 49-58
- **Root Cause**: `s1 = arr1.minusMinutes(3)`, `e1 = dep1.plusMinutes(3)`, `s2 = arr2.minusMinutes(3)`, `e2 = dep2.plusMinutes(3)`. Expanding both intervals by 3 minutes creates a required separation of 6 minutes between successive trains (3 + 3 = 6). A valid gap of 4 or 5 minutes between trains is falsely flagged as a conflict.

### Bug 5: Platform Reallocation Leaves Stale TrackBlock Occupancies
- **File**: `backend/src/main/java/com/railway/simulator/service/AllocationService.java`
- **Lines**: 269-271
- **Root Cause**: When a platform is closed and an evicted train is reassigned to a new platform, `applyReallocationDecisions()` sets `train.setCurrentBlockId(...)` only if `train.getStatus() == AT_PLATFORM`. It does NOT vacate the old platform's `TrackBlock` in `SimulationService.trackBlocks`, leaving the old block locked as occupied by the evicted train.

### Bug 6: Arbitrary Order in Signal State Evaluation
- **File**: `backend/src/main/java/com/railway/simulator/algorithm/SignalStateEngine.java`
- **Lines**: 155-162
- **Root Cause**: `evaluateAllSignals` iterates through signals in insertion order rather than reverse topological order (downstream to upstream). When signal A checks downstream signal B, signal B has not yet updated its aspect for the current tick, causing signal A to evaluate against stale downstream states.

### Bug 7: TypeScript Compilation Error in `LiveTrainSpot.tsx`
- **File**: `frontend/src/pages/LiveTrainSpot.tsx`
- **Line**: 652
- **Root Cause**: `if (train.status === 'AT_PLATFORM' || train.status === 'WAITING AT STATION')`. In TypeScript, `TrainSnapshot['status']` does not include `'WAITING AT STATION'`, causing `TS2367: This comparison appears to be unintentional because the types ... have no overlap` and failing `npm run build`.

### Bug 8: Map Popup Destruction and Selection Dropping on 1.5s Polling
- **File**: `frontend/src/pages/LiveTrainSpot.tsx`
- **Lines**: 576-581, 765-790
- **Root Cause**: Every 1.5 seconds when `corridorState` refreshes, `trainsGroup.clearLayers()` is executed. All existing Leaflet markers are removed from the DOM and recreated. Any open popup is immediately destroyed, closing the clicked train popup before the user can interact with the inspector.

### Bug 9: Train Marker Visual Label Collisions
- **File**: `frontend/src/pages/LiveTrainSpot.tsx`
- **Lines**: 640-725
- **Root Cause**: Multiple trains within the same block or waiting at adjacent signals have identical or near-identical coordinates. Because markers use identical icon dimensions and anchors without vertical offset alternating or label decluttering, they stack directly on top of each other.

---

## 4. Why the New Glass UI is Not Showing

The frosted glass UI (`backdrop-filter`, translucent surfaces, ambient glow) is not visible in the application due to four specific CSS and component overrides:

1. **Opaque Design Tokens in `:root`**:
   - **File**: `frontend/src/index.css` (lines 6-8)
   - `--bg-primary` is `#0a0f1c` (100% solid dark navy).
   - `--bg-secondary` is `#0f172a` (100% solid dark blue-gray).
   - `--bg-surface` is `#1e293b` (100% solid opaque slate).
   - `body` and `.app-layout` have `background-color: var(--bg-primary)`.

2. **Hardcoded Inline Solid Background Overrides**:
   - **Files**: `LiveTrainSpot.tsx` (lines 936, 943, 951, 959, 967, 979, 1256), `CorridorMap.tsx` (lines 309, 340), `Dashboard.tsx`, `PlatformView.tsx`.
   - Components use inline styles:
     `style={{ background: 'var(--bg-secondary)', border: '1px solid var(--border-card)', ... }}`
     `style={{ background: 'var(--bg-surface)' }}`
     `style={{ background: '#0a0f1d' }}`
   - Inline styles have higher CSS specificity than stylesheet classes (`.glass-panel`, `.surface-card`). The solid opaque `#0f172a` and `#1e293b` backgrounds completely overwrite and block the translucent `rgba(255, 255, 255, 0.05)` background.

3. **`backdrop-filter` Ineffective Over Pure Solid Backdrops**:
   - `backdrop-filter: blur(16px)` requires a translucent foreground over visual contrast (gradients, grid lines, or imagery) to be perceptible. Because the underlying `.main-content` background is a very dark, near-black linear gradient, and all overlaying card elements have solid opaque backgrounds, there is nothing behind the cards to blur. The result renders as flat, solid charcoal boxes rather than frosted glass.

4. **Leaflet Canvas & Map Container Opacity**:
   - In `LiveTrainSpot.tsx` line 1256, the map container wrapper explicitly specifies `background: '#0a0f1d'`. The legend at line 1268 uses `background: 'rgba(15, 23, 42, 0.94)'` (94% opacity), making it appear as a solid dark block rather than a glass overlay.
