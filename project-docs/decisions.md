# Architecture Decision Records (ADRs)

## ADR-001: Separation of Real-World Stations vs Simulation Assumptions
* **Date**: 2026-09-18
* **Status**: APPROVED
* **Problem**: How to model the Mumbai Western Railway corridor realistically without making unsupported claims about official Indian Railways operations, signaling rules, or platform allocations.
* **Chosen Approach**: Retain authentic geographical station names (24 stations from Virar to Mumbai Central) and verified train details when available. Clearly label all platform structures as a **"Configurable simulated platform configuration for the 24-station corridor"**, use clearly labelled **"Simulation Data"** / **"Simulated Configuration"** for unverified train schedules, and define a **"Configurable Simulation Priority Policy with six configurable priority tiers"** using **"configurable simulation priority ordering"**.
* **Reason**: Balances real-world recognition and educational relevance for placement interviews with strict academic integrity and technical accuracy.
* **Alternatives Considered**: Completely fictional station names (lacks corridor recognition); claiming official IR timetable or priority data (inaccurate and unverified).

---

## ADR-002: Dual Database Strategy (H2 Default + MySQL 8.0 Profile)
* **Date**: 2026-09-18
* **Status**: APPROVED
* **Problem**: MySQL requires database credentials. Credentials must never be committed in source code, and evaluators need zero-friction execution.
* **Chosen Approach**: Configure Spring Boot with H2 in-memory mode by default (`application.properties`) for zero-friction evaluation, and provide a full MySQL production profile (`application-mysql.properties`) strictly reading `DB_URL`, `DB_USERNAME`, and `DB_PASSWORD` from environment variables.
* **Reason**: Ensures zero-friction startup for testing and interview demonstrations while maintaining secure, production-grade MySQL support.
* **Alternatives Considered**: Committing local root passwords (security violation); requiring MySQL only (causes setup failures if credentials differ); H2 only (loses enterprise MySQL resume value).

---

## ADR-003: Pure Java Algorithmic Engine Independence
* **Date**: 2026-09-18
* **Status**: APPROVED
* **Problem**: Tight coupling between Spring JPA entities and DSA algorithms makes unit testing slow and degrades architectural clarity.
* **Chosen Approach**: Implement `TrainPriorityQueue`, `CorridorGraph`, `IntervalConflictDetector`, `PlatformScoringEngine`, and `DynamicReallocationEngine` as pure POJO Java classes within `com.railway.simulator.algorithm`.
* **Reason**: Enables blazing fast unit test execution without Spring context, cleanly isolates DSA logic for interview discussion, and promotes high cohesion.
* **Alternatives Considered**: Placing logic directly inside Spring Data JPA repositories or `@Transactional` service methods.

---

## ADR-004: Interval Conflict Detection Terminology & Data Structure
* **Date**: 2026-09-18
* **Status**: APPROVED
* **Problem**: Inaccurate algorithmic terminology (e.g. claiming an Interval Tree when sweep-line/sorted intervals are used).
* **Chosen Approach**: Use exact terminology: **Interval Overlap Conflict Detection** with sweep-line interval checking and binary search on sorted platform intervals.
* **Reason**: Maintains technical precision and avoids misinformation during placement technical interviews.

---

## ADR-006: Geographic Interactive Simulation Map with Open-Source Tiles
* **Date**: 2026-10-02
* **Status**: APPROVED
* **Problem**: How to provide an authentic, high-impact geographic map of the 24-station Mumbai corridor without requiring paid Mapbox/Google Maps API keys, while strictly maintaining single authoritative backend state synchronization.
* **Chosen Approach**: Use Leaflet with OpenStreetMap / CartoDB Dark Matter / Positron tiles, coupled with verified geographic coordinates seeded in the backend `Station` entity and `CorridorGraph`. Compute moving train positions via linear interpolation between inter-station block endpoints based on authoritative `progressPercent`. Provide graceful non-blocking offline fallbacks if tiles fail to load.
* **Reason**: Delivers an interactive geographic control-room experience with zero operational subscription costs, zero API key dependencies, and 100% adherence to single authoritative backend state.
* **Alternatives Considered**: Google Maps / Mapbox (requires paid API keys, risks quota limits during evaluation); purely schematic SVG ladders (lacks true spatial geographic context).

