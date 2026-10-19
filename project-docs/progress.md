# Progress History

## 2026-09-18 — Core DSA Algorithmic Engine & Spring Boot Backend
* **Task**: Implement pure Java algorithmic engine, JPA domain models, services, seeder, and REST controllers.
* **What Changed**:
  - Authored `PriorityPolicy`, `TrainPriorityQueue` (Min-Heap), `CorridorGraph` (24 stations), `IntervalConflictDetector` (safety buffer overlap), `PlatformScoringEngine` (explainable scoring), `PlatformAllocationEngine` (greedy interval assignment), and `DynamicReallocationEngine` (perturbation cascade).
  - Wrote 17 JUnit 5 tests covering all DSA invariants.
  - Implemented 7 JPA entities, 7 repositories, and 6 business services.
  - Developed full REST API surface (`/api/stations`, `/api/platforms`, `/api/trains`, `/api/allocations`, `/api/simulation`, `/api/analytics`, `/api/algorithms`).
* **Tests Performed**: `mvn clean test package` executed with 17 tests passed, 0 failures, 0 errors. Backend successfully compiled and started on port 8080.
* **Result**: Backend verified and fully operational.

## 2026-09-18 — React TypeScript Frontend & Full System Integration
* **Task**: Build modern control-room dashboard and connect with backend.
* **What Changed**:
  - Scaffolding with Vite + React 19 + TypeScript.
  - Implemented custom Vanilla CSS design system with control-room dark palette and light theme toggle.
  - Built `TopBar` (digital clock, speed toggles, demo CTA), `Sidebar`, `Dashboard`, `PlatformView` (1-click closure), `OccupancyTimeline` (Gantt intervals), `CorridorMap` (24 stations), `WhatIfSimulator` (perturbation studio), `AlgorithmLab` (viva defense panel), `TrainManagement`, `AllocationHistoryPage`, `AnalyticsPage`, and `DemoWalkthroughModal`.
  - Built frontend bundle with `npm run build` (0 errors).
  - Verified backend and frontend running simultaneously (`http://localhost:8080` and `http://localhost:5173`).
* **Result**: Complete placement-ready full-stack system delivered and verified.

## 2026-09-18 — Educational Simulation Signalling Backend Foundation (Phase B to D)
* **Task**: Implement pure Java `TrackBlock` model, `Signal` model, `SignalAspect` (4 aspects), `SignalStateEngine`, and signal-enforced train movement.
* **What Changed**:
  - Created `TrackBlock.java` and `BlockType.java` representing discrete corridor and platform track sections.
  - Added `createCorridorTrackBlocks()` and `createCorridorSignals()` to `CorridorGraph.java`, deriving blocks directly from graph topology.
  - Created `Signal.java` and `SignalAspect.java` (`RED`, `YELLOW`, `DOUBLE_YELLOW`, `GREEN`).
  - Implemented pure Java deterministic `SignalStateEngine.java` with 4-aspect evaluation rules.
  - Extended `Train.java` with `currentBlockId`, `nextSignalId`, `signalAspect`, and `waitingReason`.
  - Integrated signalling state evaluation and RED signal halting into `SimulationService.java`.
  - Authored `SignalStateEngineTest.java` with 6 JUnit 5 tests.
* **Tests Performed**:
  - `mvn clean test` executed with 23 tests passed, 0 failures, 0 errors.
  - `npm run build` executed in frontend with 0 TypeScript/Vite errors.
* **Result**: Backend signalling core completed and verified without regressions.

## 2026-09-18 — Platform Interlocking & Delay Cascade Integration (Phase E)
* **Task**: Interconnect platform availability, track block states, signal evaluations, train movement constraints, and dynamic reallocation.
* **What Changed**:
  - Added `syncSignallingState()` in `SimulationService` providing bidirectional synchronization across database entities, in-memory track blocks, and signals.
  - Enforced platform interlocking in `advanceTrainStates()`: approaching/arriving trains halt at RED home signals before entering platform blocks; trains resume when reallocated or when signals clear.
  - Connected `AllocationService.handlePlatformClosure()` and `handleTrainDelay()` to trigger `syncSignallingState()` before and after reallocation, clearing stale block occupancies.
  - Connected `PlatformService.closePlatform()` and `openPlatform()` to trigger `syncSignallingState()`.
  - Authored `PlatformInterlockingSignallingTest.java` with 10 integration tests covering all Phase E requirements.
* **Tests Performed**:
  - `mvn test` executed with 33 tests passed, 0 failures, 0 errors.
  - `mvn package -DskipTests` updated `railway-simulator-backend-1.0.0.jar`.
  - `npm.cmd run build` executed with 0 errors (891 ms).
* **Result**: Complete backend platform interlocking and delay cascade completed and verified.

## 2026-09-18 — Central Corridor State API + Live Signalling Map (Phase F)
* **Task**: Deliver unified central corridor state API (`/api/simulation/corridor-state`), frontend TypeScript types, upgrade `CorridorMap.tsx` with 4-aspect signal heads and discrete track blocks, build `TrainSignalDrawer.tsx`, and enhance `AlgorithmLab.tsx`.
* **What Changed**:
  - Implemented `GET /api/simulation/corridor-state` returning authoritative unified DTO (`TrainSnapshotDto`, `TrackBlockSnapshotDto`, `SignalSnapshotDto`, `PlatformSnapshotDto`, `ConflictReport`).
  - Added TypeScript interfaces in `types/index.ts` and `api.getCorridorState()` in `services/api.ts`.
  - Created `frontend/src/components/TrainSignalDrawer.tsx` displaying train inspector and prominent "WHY IS THIS TRAIN STOPPED?" explanation with backend `waitingReason`.
  - Upgraded `frontend/src/pages/CorridorMap.tsx` to a control-room schematic visualization featuring parallel Fast Line (cyan) and Slow Line (green) track corridors, discrete track blocks, 4-aspect signal heads (🔴, 🟡, 🟡🟡, 🟢), train markers on active blocks, 1.5s live polling, live network status bar, and accessible legend.
  - Added 6th algorithm `Simulation Signalling State Engine (SignalStateEngine)` to `AlgorithmController.java` and interactive 5-stage pipeline diagram (`Track Block` ➔ `Occupancy` ➔ `SignalStateEngine` ➔ `Signal Aspect` ➔ `Train Movement`) to `AlgorithmLab.tsx`.
* **Tests Performed**:
  - `mvn clean test` passed with 33 of 33 tests green.
  - `npm.cmd run build` passed with 0 TypeScript/Vite errors (built in 977ms).
  - Verified `GET /api/simulation/corridor-state` returns HTTP 200 with 49.8KB authoritative snapshot in 30ms.
* **Result**: Phase F fully implemented, operational, and verified.

## 2026-09-18 — Final Phase: Visualization Audit, Live Train Spot, Movement & Cross-View Synchronization
* **Task**: Comprehensive audit of visual representation, train movement, cross-view state consistency, dynamic timeline windowing, and creation of dedicated "Live Train Spot" page.
* **Root Causes Identified**:
  - `Train` model lacked position/speed telemetry (`progressPercent`, `speedKmH`), causing static representation.
  - Seeded initial block IDs (`BLK-DIC-BVI-FU`, `BLK-AND-JOS-FD`) did not match `CorridorGraph` track topology where Dahisar and Jogeshwari only feature Slow tracks (`SU`/`SD`).
  - Independent polling across views led to potential state drift.
  - Timeline had a static window and lacked clear empty-state messaging for quiet stations.
* **What Changed**:
  - Extended `Train.java` and `TrainSnapshotDto.java` with `progressPercent` and `speedKmH`.
  - Added continuous block-bound movement kinematics in `SimulationService.java` (`deltaPercent = (speed / 3600) * dt / blockKm * 100`) with immediate signal halting at RED and platform arrival transitions.
  - Connected `AllocationService` and `PlatformService` to keep `train.currentBlockId` synchronized with platform blocks for `AT_PLATFORM` trains and automatically resume reallocated trains.
  - Created new dedicated page `frontend/src/pages/LiveTrainSpot.tsx` (`/train-spot`) showing all 24 stations Virar to Mumbai Central, Fast & Slow line tracks, discrete blocks, 4-aspect signal heads, and real-time train movement.
  - Upgraded `CorridorMap.tsx` to a map-centric view with collapsible platform berths while preserving search, filters, and diagnostics.
  - Centralized corridor state polling in `App.tsx` (`GET /api/simulation/corridor-state`), sharing a single authoritative snapshot and unified `TrainSignalDrawer` across all views.
  - Fixed `OccupancyTimeline.tsx` with a dynamic 90-minute window around `simulationTime`, merged active platform berths, and added clear empty-state notifications.
  - Created `SimulationCorridorConsistencyTest.java` validating all 12 consistency invariants across 9 automated test cases.
* **Tests Performed**:
  - `mvn test` passed with 42 of 42 tests green (10 in `PlatformInterlockingSignallingTest`, 9 in `SimulationCorridorConsistencyTest`, and 23 DSA tests).
  - `npm.cmd run build` passed with 0 TypeScript/Vite errors (built in 1.42s).
  - Automated REST verification: verified running simulation advances `progressPercent`, pausing stops movement, platform closure evaluates signal to RED and reallocates approaching train, delay injection propagates across snapshot.
* **Result**: Complete Final Phase delivered, verified, and operational with single authoritative backend state.

## 2026-10-02 — Interactive Geographic Live Train Spot & Corridor Coordinates (Phase 10)
* **Task**: Upgrade Live Train Spot to an interactive real geographic map of the 24-station Western Railway corridor (Virar to Mumbai Central) using Leaflet and CartoDB Dark Matter / Positron tiles, with backend station coordinate integration.
* **What Changed**:
  - Extended `Station` domain entity with `latitude` and `longitude` columns, constructors, and getters/setters.
  - Seeded authentic verified coordinates for all 24 corridor stations in `DataSeederService.java` and `schema.sql`.
  - Added coordinate fields to `StationNode` in `CorridorGraph.java`.
  - Added `latitude` and `longitude` fields to TypeScript `Station` interface in `types/index.ts`.
  - Completely replaced schematic ladder in `LiveTrainSpot.tsx` with high-performance Leaflet interactive map.
  - Rendered Fast Line (`#38bdf8`) and Slow Line (`#10b981`) track polylines with lateral visual offsets.
  - Positioned all 24 interactive station markers with rich click popups and direct station selection.
  - Implemented continuous linear interpolation for trains in transit based on `progressPercent / 100` between inter-station block endpoints, with lateral track offsets, directional arrows (`UP`/`DOWN`), and category color badges.
  - Rendered 4-aspect signal heads (`RED`, `YELLOW`, `DOUBLE_YELLOW`, `GREEN`) with aspect glow and click diagnostic popup.
  - Integrated with shared `TrainSignalDrawer` on train click.
  - Added Sector viewports (Full Corridor, North, Central, South), Line filters (All, Fast, Slow), Status filters (All, Moving, Waiting, Delayed, At Platform), Layer toggles (Stations, Trains, Signals, Blocks), and Instant Search.
  - Added non-blocking offline map tile fallback notice.
* **Tests Performed**:
  - `mvn test` in `backend/`: 42 of 42 unit and integration tests passed.
  - `npm.cmd run build` in `frontend/`: 0 errors, built in 600ms.
  - Verified `GET /api/stations` returns 24 stations with populated `latitude` and `longitude`.
  - Verified `GET /api/simulation/corridor-state` returns live trains with `progressPercent`, blocks, and signals.
* **Result**: Geographic Live Train Spot operational, verified, and connected to single authoritative simulation state.

## 2026-10-10 — Final Corridor Integration, Spatial Schematic & Schedule Distribution (Phase 11)
* **Task**: Fix frontend TypeScript build errors, resolve traffic concentration at Borivali, generate platform blocks/signals for all 24 corridor stations, transform Corridor Map into a continuous physical railway schematic, apply Spatial UI with subtle glassmorphism, and verify end-to-end consistency.
* **Root Causes Identified**:
  - `LiveTrainSpot.tsx` compared `train.status` with `'WAITING AT STATION'`, which is not part of `TrainStatus`, causing Vite/TypeScript build failure.
  - Initial train seed had 6 out of 15 trains targeting `BVI` and ran initial allocations only for 6 stations, making intermediate corridor stations appear empty.
  - `CorridorGraph.createCorridorTrackBlocks()` only generated platform blocks for 7 stations, causing missing block exceptions for other stations.
  - Corridor Map was structured with large station cards rather than a continuous railway corridor where tracks run as physical rails with trains positioned directly on them.
* **What Changed**:
  - Corrected `train.status` comparison in `LiveTrainSpot.tsx` to match valid `TrainStatus` types.
  - Enhanced `CorridorGraph.java` with dynamic platform track block and signal generation across all 24 stations.
  - Added 7 deterministic simulated train schedules distributed across northern, central, and southern sectors (`BSR`, `BYR`, `MLD`, `GMN`, `BA`, `DDR`, `MMCT`) in `DataSeederService.java` and `baselineMap` in `SimulationService.java`, and seeded initial allocations across all hub and intermediate stations.
  - Rebuilt `CorridorMap.tsx` as a continuous spatial schematic with metallic rails, sleeper beds, discrete block markers, 4-aspect signal heads, and train markers positioned directly ON the trackway rails with smooth CSS linear movement (`transition: left 1.4s linear`).
  - Added Spatial UI and restrained Glassmorphism tokens in `index.css` (`.spatial-canvas`, `.spatial-glass`, `.railway-track-bed`, `.track-train-marker`).
* **Tests Performed**:
  - `mvn test` in `backend/`: all 42 of 42 JUnit unit and integration tests passed with 0 failures, 0 errors.
  - `npm.cmd run build` in `frontend/`: 0 errors, built in 982ms.
  - Verified backend Tomcat server running on `http://localhost:8080` (HTTP 200).
  - Verified Vite dev server running on `http://localhost:5173` (HTTP 200).
  - Verified `GET /api/simulation/corridor-state` returns 84KB authoritative snapshot in ~25ms.
  - Verified simulation start, pause, and reset transitions through REST API.
* **Result**: Complete final integration completed, tested, and operational.

## 2026-10-11 — Real Overpass OpenStreetMap Geometry & Spatial Glassmorphism OCC Restyle (Phase 12)
* **Task**: Fix railway track map geometry to follow the authentic Western Railway corridor (Virar to Mumbai Central), snap stations and signals to real tracks, implement parallel track offsets via Turf.js, apply a dark control-room map theme with circular oriented train markers, and restyle the entire UI with Spatial UI and subtle glassmorphism.
* **Root Causes Identified**:
  - Previous map drew direct straight lines between station coordinates rather than following the physical curves of the real railway tracks.
  - CartoDB tiles displayed API key watermarks or errored under tight network boundaries.
  - Train markers on the map were rectangular cards rather than control-room circular indicators aligned with track bearing.
  - UI views had inconsistent background treatments without a cohesive floating spatial glass aesthetic.
* **What Changed**:
  - Fetched authentic railway geometry from OpenStreetMap Overpass API (`south=18.96 west=72.78 north=19.47 east=72.90`) and stitched into continuous 56.10 km polyline stored offline in `src/data/westernLineTrack.json`.
  - Snapped all 24 corridor stations onto the polyline with exact kilometer positions.
  - Generated parallel tracks (Fast UP, Fast DOWN, Slow UP, Slow DOWN) using Turf.js `lineOffset` (offsets ±7m and ±2.5m).
  - Positioned moving trains dynamically along the polyline using Turf.js `along` based on `progressPercent`, rotating circular train markers with direction arrows to the exact track bearing.
  - Placed 4-aspect signal heads beside the track rails at block boundaries with aspect glow and tooltips.
  - Integrated dark, watermark-free control-room canvas tiles (`ArcGIS World Dark Gray Base` / Carto Dark Matter), only showing warning after 3+ consecutive tile errors.
  - Added decluttering: stations render as compact dots; station labels display at zoom ≥ 12; train cards appear strictly for selected/hovered trains.
  - Built collapsible control-room legend (UP lines green, DN lines orange, signal aspects, fleet types).
  - Implemented Spatial UI across all views with dark navy background (`#0a0f1c` to `#0f172a`), radial glow, floating glass panels (`background: rgba(255,255,255,0.05)`, `backdrop-filter: blur(16px) saturate(140%)`, `border: 1px solid rgba(255,255,255,0.12)`, radius 16px, soft shadow and inner top highlight), cyan accents (`#22d3ee`), Inter typography, and JetBrains Mono for telemetry.
* **Tests Performed**:
  - `npm run build` executed in `frontend/`: 0 TypeScript / Vite errors.
  - Captured headless browser screenshots of overview map and verified live control-room layout.
  - Verified live server active at `http://localhost:5173`.
* **Result**: Authentic real railway geometry, control-room OCC map, and unified glassmorphic UI delivered and operational.
