# Project Specification: Dynamic Railway Platform Allocation & Train Scheduling Simulator

## 1. Executive Overview
The **Dynamic Railway Platform Allocation & Train Scheduling Simulator** is an advanced decision-support and algorithmic simulation engine designed to model suburban and express train scheduling along the Mumbai Western Railway corridor (Virar to Mumbai Central).

The system solves the NP-hard dynamic platform assignment and conflict resolution problem under real-time perturbations (train delays, emergency train insertions, platform closures, and corridor disruptions) using rigorous Data Structures and Algorithms (DSA).

## 2. Key Objectives
* **Realistic Corridor Simulation**: Model the 24-station corridor from Virar to Mumbai Central, differentiating Slow and Fast lines.
* **Algorithmic Correctness**:
  * Min-Heap Priority Queue for upcoming train arrivals and configurable simulation priority ordering.
  * Graph adjacency-list corridor representation with distance, travel time, and route validation.
  * Interval Conflict Detection (`[arrival - buffer, departure + buffer]`).
  * Explainable multi-factor greedy platform scoring engine.
  * Ripple-effect dynamic reallocation engine resolving conflicts algorithmically.
* **Placement & Portfolio Excellence**:
  * Decoupled pure Java algorithm engine accompanied by thorough unit test suites.
  * Spring Boot 3 REST API backend with clean domain layering and dual-profile database support (H2 default + MySQL 8.0).
  * Control-room React + TypeScript UI featuring interactive Gantt timeline, corridor network visualizer, What-If scenario studio, and Algorithm Lab for technical viva explanations.
  * 1-Click "Run Demo Scenario" for evaluation presentations.

## 3. Real-World Accuracy & Simulation Disclaimer
> [!IMPORTANT]
> **SIMULATION ASSUMPTIONS & REAL-WORLD DATA NOTICE**:
> 1. **Verified Real-World Information**: Station names along the 24-station corridor from Virar to Mumbai Central are authentic geographical stations on Western Railway. Any real train names, train numbers, and routes included are verified; when unverified, trains are clearly designated as **"Simulation Data"** / **"Simulated Configuration"** without inventing fictitious official timetables.
> 2. **Configurable Simulation Platform Configuration**: Platform counts across stations represent a **"Configurable simulated platform configuration for the 24-station corridor"** and are not presented as official Indian Railways infrastructure counts.
> 3. **Configurable Simulation Priority Policy**: Priority tiers represent a **"Configurable Simulation Priority Policy with six configurable priority tiers"** for algorithmic demonstration, never presented as official Indian Railways operating priority rules. Priority management uses **"configurable simulation priority ordering"**.
> 4. **Algorithmic Core**: Uses **Interval Conflict Detection** (incorporating configurable $[T_{\text{arr}} - \Delta, T_{\text{dep}} + \Delta]$ buffers).
> 5. **Database Security**: MySQL credentials are never hardcoded; externalized via `DB_URL`, `DB_USERNAME`, and `DB_PASSWORD` environment variables, while embedded H2 remains the default zero-friction database.
> 6. **Non-Affiliation**: This system is an academic simulation and algorithmic decision-support tool, not an official railway signaling or dispatch system.
> 7. **Geographic Visualization**: Station coordinates (latitude and longitude) represent verified real-world station positions. Track lines, signal locations, block boundaries, and train progress are simulated operational visualizations, not live GPS telemetry or surveyed engineering schematics.
