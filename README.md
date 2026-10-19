# Dynamic Railway Platform Allocation & Train Scheduling Simulator

A high-performance, algorithm-driven railway platform allocation and train scheduling simulator modeled on the Mumbai suburban Western Railway corridor from **Virar to Mumbai Central** (24 stations).

This project demonstrates the practical application of core Computer Science Data Structures & Algorithms (**Min-Heap Priority Queues**, **Corridor Adjacency Graphs**, **Interval Overlap Conflict Detection**, **Greedy Platform Scoring**, and **Dynamic Cascading Reallocation**) within a production-grade **Spring Boot 3** and **React + TypeScript** control-room application.

---

## 1. Problem Statement

Railway platform allocation is a complex scheduling challenge. In busy suburban corridors handling hundreds of train movements daily, unexpected operational perturbations—such as signal delays, platform maintenance closures, or emergency relief train insertions—trigger cascading schedule conflicts. 

The core algorithmic challenge is:
> **Dynamically generate feasible, conflict-free platform assignments while prioritizing emergency and premium services, optimizing line alignment, and minimizing passenger disruption across platforms.**

---

## 2. Core Algorithmic Architecture & DSA Foundation

The system separates algorithmic decision-making into an independent, pure Java engine (`com.railway.simulator.algorithm`) accompanied by a comprehensive JUnit 5 test suite verifying structural invariants:

### 1. Min-Heap Priority Queue (`TrainPriorityQueue`)
* **Purpose**: Manages upcoming train arrivals and configurable simulation priority ordering.
* **Comparator Invariant**: Composite ordering on:
  $$\text{Key} = (\text{Simulation Priority Tier}, \text{Effective Arrival Time}, \text{Train Number})$$
* **Complexity**: Insertion $O(\log N)$ | Removal $O(\log N)$ | Peek $O(1)$ | Space $O(N)$.
* **Configurable Simulation Priority Policy with six configurable priority tiers**:
  *(Note: The ordering below is used internally for demonstration and must NEVER be described as official Indian Railways priority)*
  * Tier 1: Emergency / Special Service
  * Tier 2: Premium (Rajdhani/Shatabdi simulated service)
  * Tier 3: Superfast Express
  * Tier 4: Express / Mail
  * Tier 5: Suburban Local
  * Tier 6: Freight / Goods

### 2. Corridor Network Graph (`CorridorGraph`)
* **Purpose**: Models the 24-station Western Railway corridor from Virar to Mumbai Central, separating Fast Line and Slow Line track connectivity.
* **Data Structure**: Adjacency list graph with weighted directed edges representing physical tracks.
* **Complexity**: Station lookup $O(1)$ | Route validation $O(V + E)$ | Shortest path $O(V + E \log V)$.

### 3. Interval Overlap Conflict Detector (`IntervalConflictDetector`)
* **Purpose**: Determines whether two trains collide on the same platform within safety buffers.
* **Interval Model**: $[T_{\text{arr}} - \Delta_{\text{buffer}}, T_{\text{dep}} + \Delta_{\text{buffer}}]$ (default $\Delta = 3\text{ minutes}$).
* **Condition**: Two intervals $[s_1, e_1]$ and $[s_2, e_2]$ conflict on platform $P$ if and only if:
  $$\max(s_1, s_2) < \min(e_1, e_2)$$
* **Complexity**: Pairwise check $O(1)$ | Platform check $O(M)$ | Global sweep $O(T \log T)$.

### 4. Explainable Multi-Factor Platform Scoring Engine (`PlatformScoringEngine`)
* **Purpose**: Evaluates candidate platforms for a train without arbitrary magic numbers.
* **Mathematical Factors**:
  * **Hard Gates**: Platform must be open, compatible with train type, and conflict-free.
  * **Line Match**: $+50$ pts for dedicated line alignment (Fast platform for express, Slow platform for local).
  * **Idle Spacing Buffer**: $+25 \times \min(1.0, \frac{\text{gap}}{20\text{ min}})$ to prevent bottleneck bunching.
  * **Passenger Disruption Minimization**: $+40$ pts for published timetable platform; $-8 \times |\text{Candidate} - \text{Scheduled}|$ walking distance penalty.
  * **Priority Bonus**: $+3 \times (7 - \text{Tier})$.

### 5. Dynamic Cascading Reallocation Engine (`DynamicReallocationEngine`)
* **Purpose**: Triggered when a platform is closed or a train is delayed. Evicts affected trains, re-queues them into the Min-Heap by priority, and algorithmically reassigns them to the best available conflict-free platforms while logging a full audit trail.

---

## 3. Technology Stack

* **Backend**: Java 21+, Spring Boot 3.3.4, Spring Data JPA, Spring Web, Bean Validation, JUnit 5.
* **Database**: Dual-profile persistence:
  * **H2 Embedded (Default)**: In-memory mode for zero-friction evaluation and automated test runs.
  * **MySQL 8.0 (Production Profile: `mysql`)**: Configured via externalized environment variables (`DB_URL`, `DB_USERNAME`, `DB_PASSWORD`).
* **Frontend**: React 19, TypeScript, Vite, Vanilla CSS Design System, Lucide Icons.
* **Architecture**: Layered Hexagonal-inspired Architecture with pure POJO algorithmic core.

---

## 4. Key Features & Dashboard Views

1. **Operations Dashboard Overview**: High-level KPIs (Active Trains, Available Platforms, Delayed Trains, Utilization %, Conflicts Prevented), live approaching train feed, and quick trigger actions.
2. **Platform Occupancy View**: Real-time grid of all station platforms with occupancy badges and a **1-click Platform Closure Simulator** that immediately runs dynamic reallocation.
3. **Occupancy Timeline (Gantt Interval View)**: Interactive timeline displaying platform lanes vs. time intervals, color-coded train bars, safety buffers, and a live simulation clock needle.
4. **Corridor Schematic Network Map**: 24-station topological visualizer from Virar to Mumbai Central showing parallel Fast/Slow tracks and train locations.
5. **What-If Operations Studio**: Perturbation testing lab allowing delay injections and platform shutdowns with real-time impact delta analysis.
6. **DSA Algorithm Lab & Viva Panel**: Dedicated technical defense view showcasing Priority Queues, Corridor Graphs, Interval Conflict Detection, and Platform Scoring with complexities and interview talking points.
7. **Train & Fleet Management**: Search, filter by category/status, add new train modal, and inline delay adjustments.
8. **Allocation & Reallocation Audit Log**: Immutable timestamped history recording previous platform, new platform, algorithmic reasoning, and delay deltas.
9. **1-Click "Run Demo Scenario"**: Automated guided walkthrough designed specifically for evaluation presentations.

---

## 5. Getting Started & Execution

### Prerequisites
* Java JDK 21 or higher (JDK 24 tested)
* Apache Maven 3.9+
* Node.js v20+ and npm

### 1. Run Backend (Spring Boot)
```bash
cd backend
mvn spring-boot:run
```
* Backend starts at: `http://localhost:8080`
* H2 Database Web Console: `http://localhost:8080/h2-console` (JDBC URL: `jdbc:h2:mem:railway_db`, User: `sa`, Password: blank)

*(Optional: To run with MySQL 8.0, pass `-Dspring.profiles.active=mysql` along with `DB_USERNAME` and `DB_PASSWORD` environment variables).*

### 2. Run Frontend (React + TypeScript)
```bash
cd frontend
npm install
npm run dev
```
* Frontend starts at: `http://localhost:5173`

### 3. Run Automated Tests
```bash
cd backend
mvn test
```
Executes all 17 unit tests covering Priority Queues, Corridor Graphs, Interval Conflict Detectors, Platform Scoring, and Dynamic Reallocation.

---

## 6. Simulation Assumptions & Disclaimers

1. **Station Names**: Station names along the Virar to Mumbai Central line correspond to authentic geographical stations on Western Railway.
2. **Simulation Parameters**: Platform counts, track layouts, line allocations, train schedules, travel times, and priority tiers are **Configurable Simulation Data / Configurable Simulation Assumptions**.
3. **Non-Affiliation**: This software is an academic simulation and algorithmic decision-support project. It does NOT claim to represent official Indian Railways operating rules, live signalling protocols, or actual railway dispatching decisions.

---

## 7. License
Academic and portfolio demonstration project.
