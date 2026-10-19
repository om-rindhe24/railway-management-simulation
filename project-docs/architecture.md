# System Architecture: Dynamic Railway Platform Allocation

## 1. Architectural Style
The system follows a clean Layered Architecture (Hexagonal-inspired) separating the algorithmic scheduling core from REST controllers, business services, and database persistence.

```
                      +----------------------------------+
                      |    React + TypeScript Frontend   |
                      | (Control-Room UI, Gantt Timeline,|
                      | Corridor Map, What-If Simulator) |
                      +-----------------+----------------+
                                        | HTTP REST (JSON)
                                        v
                      +-----------------+----------------+
                      |     Spring Boot REST API Layer   |
                      |  (Controllers, DTOs, Validation) |
                      +-----------------+----------------+
                                        |
                                        v
                      +-----------------+----------------+
                      |         Service Layer            |
                      | (AllocationService, SimService)  |
                      +--------+----------------+--------+
                               |                |
                Pure Java DSA  v                v Spring Data JPA
          +--------------------+---+      +-----+------------------+
          | Algorithmic Engine     |      | Persistence Layer      |
          | - TrainPriorityQueue   |      | - Repositories         |
          | - CorridorGraph        |      | - Entities             |
          | - IntervalConflictDet  |      | - H2 (Default)         |
          | - PlatformScoringEngine|      | - MySQL (Profile)      |
          | - ReallocationEngine   |      +------------------------+
          +------------------------+
```

## 2. Decoupled Algorithmic Engine (`com.railway.simulator.algorithm`)
The core scheduling and conflict resolution algorithms are implemented as pure, POJO-based Java classes independent of Spring framework annotations. This guarantees:
1. Complete, isolated unit testing without loading Spring ApplicationContext.
2. Fast execution benchmarked in milliseconds.
3. Clean separation between data structures/algorithms and database plumbing.

## 3. Database Strategy: Zero-Friction Dual Mode
* **Default Mode (H2 in-memory/file)**: Out-of-the-box zero configuration execution for placement evaluation, live viva demos, and CI unit tests.
* **Production Profile (`mysql`)**: Connects to MySQL 8.0 instance using environment variables (`DB_URL`, `DB_USERNAME`, `DB_PASSWORD`), with JPA auto-schema generation and SQL seed scripts. Passwords are never committed in source code.
* **Seed Configuration**: Automatically initializes with a configurable simulated platform configuration for the 24-station corridor and clearly labelled simulated train data.

## 4. Frontend Architecture
* Modular React 19 + TypeScript structured into:
  * `components/`: Reusable UI elements (KPI cards, topbar, sidebar, badge, modal).
  * `pages/`: Dedicated functional views (Dashboard, Timeline, CorridorMap, WhatIfSimulator, AlgorithmLab, TrainManagement, AllocationHistory, Analytics).
  * `services/`: Axios API clients with typed request/response contracts.
  * `styles/`: Custom Vanilla CSS design tokens, glassmorphic surfaces, dark control-room palette with light-mode toggle.

## 5. Educational Simulation Signalling & Track-Block Architecture
* **Core Principle**: A single authoritative backend simulation state governs train movement, platform allocation, track-block occupancy, and signal aspects.
* **Component Layers**:
  * **TrackBlock Model**: Discrete track segments derived from `CorridorGraph` track edges, maintaining occupancy and closure flags.
  * **Signal Model**: 4-aspect simulation signals (`RED`, `YELLOW`, `DOUBLE_YELLOW`, `GREEN`) protecting block entry boundaries.
  * **SignalStateEngine**: Pure deterministic algorithmic evaluator computing aspect and human-readable explanation based on block occupancy and platform status.
  * **Unified State Endpoint**: `/api/simulation/corridor-state` exposes the combined simulation clock, trains, blocks, signals, and platform states to ensure absolute consistency across all UI components.
  * See [`/project-docs/signalling.md`](file:///c:/Users/rindh/OneDrive/Documents/Railway%20Management%20System/project-docs/signalling.md) for detailed specifications.

## 6. Geographic Live Train Spot Architecture (Phase 10)
* **Single Authoritative Geographic Model**:
  * Stations carry verified geographic coordinates (`latitude`, `longitude`) in the backend `Station` entity and `CorridorGraph`.
  * The frontend map (`LiveTrainSpot.tsx`) is built with Leaflet and CartoDB Dark Matter / Positron tiles, avoiding any paid map keys.
  * Fast Line (`#38bdf8`) and Slow Line (`#10b981`) track corridors are rendered as distinct simulated polylines with lateral visual separation.
  * Trains in transit calculate continuous positions along block vectors:
    $$\text{Lat}(p) = \text{Lat}_{\text{from}} + (\text{Lat}_{\text{to}} - \text{Lat}_{\text{from}}) \times \frac{\text{progressPercent}}{100}$$
    $$\text{Lng}(p) = \text{Lng}_{\text{from}} + (\text{Lng}_{\text{to}} - \text{Lng}_{\text{from}}) \times \frac{\text{progressPercent}}{100} \pm \Delta_{\text{lineOffset}}$$
  * 4-aspect signal heads and block occupancy overlays synchronize directly from `/api/simulation/corridor-state` without secondary polling.
  * Station selection, train selection, and shared diagnostic drawer (`TrainSignalDrawer`) are cross-synchronized with the rest of the application.

