# REST API Specification: Railway Platform Simulator

Base URL: `/api`

All JSON responses follow the standard envelope:
```json
{
  "success": true,
  "message": "Operation description",
  "data": { ... },
  "timestamp": "2026-09-18T00:50:00Z"
}
```

---

## 1. Stations & Platforms

### `GET /api/stations`
Returns list of 24 corridor stations with platform metadata and verified geographic coordinates (`latitude`, `longitude`, `sequenceOrder`, `fastLineSupport`, `slowLineSupport`, `terminalFlag`).

### `GET /api/stations/{id}`
Returns details for a single station with platforms and current platform occupancy.

### `GET /api/platforms`
Returns all platforms across all stations. Can filter by `stationId`.

### `POST /api/platforms/{id}/close`
Closes a platform for maintenance or incident simulation.
Triggers immediate conflict detection and reallocation calculation.
**Response**: Reallocation impact summary (affected trains count, reallocated count, delay impact).

### `POST /api/platforms/{id}/open`
Reopens a closed platform.

---

## 2. Trains & Schedules

### `GET /api/trains`
Returns list of all active trains. Query params:
* `type`: LOCAL, EXPRESS_MAIL, SUPERFAST, PREMIUM, SPECIAL, FREIGHT
* `status`: SCHEDULED, APPROACHING, ARRIVING, AT_PLATFORM, DEPARTED, DELAYED, WAITING, CANCELLED
* `station`: Station code (e.g. `BVI`, `AND`, `DDR`, `CCG`)

### `POST /api/trains`
Registers a new train in the simulation.

### `PUT /api/trains/{id}/delay`
Injects an operational delay (in minutes).
**Request Body**:
```json
{
  "delayMinutes": 15,
  "reason": "Signal failure near Vasai Creek"
}
```
**Response**: Dynamic reallocation cascade result with before/after platform diff.

### `DELETE /api/trains/{id}`
Cancels or removes a train from the active schedule.

---

## 3. Allocations & Dynamic Reallocations

### `GET /api/allocations`
Returns active platform allocations. Query params: `stationId`, `platformId`.

### `POST /api/allocations/run`
Triggers greedy platform allocation engine for all unallocated trains.

### `POST /api/reallocation/run`
Evaluates all current train schedules and dynamic delays, resolving overlaps.

---

## 4. Simulation Engine & Scenarios

### `GET /api/simulation/state`
Returns current simulation clock, status (`RUNNING`, `PAUSED`, `STOPPED`), speed multiplier (`1x`, `2x`, `5x`, `10x`), and system statistics.

### `GET /api/simulation/corridor-state`
Authoritative single snapshot for the live railway visualization and control room map.
**Response Body**:
```json
{
  "simulationTime": "2026-09-18T10:15:00",
  "running": true,
  "speedMultiplier": 1,
  "trains": [...],
  "trackBlocks": [...],
  "signals": [...],
  "platforms": [...],
  "activeConflicts": [...]
}
```
* **Trains**: ID, number, name, type, status, block ID, next signal, aspect, waitingReason, delay, platform, direction.
* **TrackBlocks**: block ID, from/to station, line type, block type, occupied, occupying train, closed, next block.
* **Signals**: signal ID, protected block, station, direction, line, aspect (RED, YELLOW, DOUBLE_YELLOW, GREEN), reason, next signal.
* **Platforms**: platform ID, station, number, line, occupied, closed, closure reason, occupancy intervals.

### `POST /api/simulation/start`
Starts or resumes the simulation clock.

### `POST /api/simulation/pause`
Pauses the simulation clock.

### `POST /api/simulation/speed`
Updates the simulation clock multiplier (1, 2, 5, 10).

### `POST /api/simulation/reset`
Resets the simulation to the baseline initial state.

### `POST /api/simulation/scenario`
Injects pre-configured or custom What-If scenario:
* `SCENARIO_PLATFORM_CLOSURE`
* `SCENARIO_TRAIN_DELAY`
* `SCENARIO_EMERGENCY_TRAIN`
* `SCENARIO_PEAK_SURGE`

### `POST /api/simulation/demo`
Executes the automated 1-click demonstration scenario for placement evaluation.

---

## 5. Analytics & History

### `GET /api/analytics`
Returns dashboard KPIs: active trains, platform utilization rate, delay distribution by category, hourly conflicts resolved.

### `GET /api/history`
Returns chronological audit log of platform allocations and reallocations with reasoning and timestamps.

### `GET /api/algorithms/metadata`
*(Unused by frontend UI following Algorithm Lab retirement; retained for backend DSA reference and API verification)*
Returns DSA technical metadata (Priority Queue, Graph, Conflict Detector, Scoring Engine, Time/Space complexities) for technical review and viva presentation.

---

## 6. Educational Simulation Signalling & Corridor State

### `GET /api/simulation/corridor-state`
Returns the complete authoritative snapshot of the corridor:
* `simulationClock`: current simulation time, running status, speed multiplier.
* `trains`: active trains with `currentBlockId`, `progressPercent` (0.0 to 100.0 along active block), `speedKmH` (operational speed), `nextSignalId`, `signalAspect`, `waitingReason`, and delay.
* `trackBlocks`: all corridor track blocks with occupancy status and occupying train.
* `signals`: all corridor signals with current aspects (`RED`, `YELLOW`, `DOUBLE_YELLOW`, `GREEN`) and aspect reasoning.
* `platforms`: real-time platform occupancy and closures.
* `activeConflicts`: active interval conflicts detected.

### `GET /api/signals`
Returns all simulation signals with their protecting blocks, current aspects, and explanatory reasons.

### `GET /api/track-blocks`
Returns all track blocks derived from the 24-station corridor topology with occupancy and status.
