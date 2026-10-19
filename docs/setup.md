# System Setup & Execution Guide

## 1. Prerequisites
* **Java Development Kit (JDK)**: JDK 21 or higher (JDK 24 detected and verified).
* **Apache Maven**: 3.9+ (Installed).
* **Node.js**: v20+ (v22.19.0 installed).
* **Package Manager**: npm 10+ (use `npm.cmd` on Windows PowerShell).
* **Database (Optional)**: MySQL 8.0 (H2 embedded is pre-configured and runs by default).

---

## 2. Running the Backend (Spring Boot)

### Option A: Default Zero-Friction Mode (H2 In-Memory)
Requires zero setup. Automatically initializes tables and seeds the 24 stations, Configurable simulated platform configuration for the 24-station corridor, and clearly labelled simulation data:
```bash
cd backend
mvn spring-boot:run
```
The backend starts on `http://localhost:8080`.
H2 Console available at: `http://localhost:8080/h2-console` (JDBC URL: `jdbc:h2:mem:railway_db`, User: `sa`, Password: blank).

### Option B: Production Mode with Local MySQL 8.0
If you wish to use your local MySQL server:
1. Ensure MySQL service is running.
2. Provide your credentials via environment variables (never committed directly in source code):
```powershell
$env:SPRING_PROFILES_ACTIVE="mysql"
$env:DB_URL="jdbc:mysql://localhost:3306/railway_simulator?createDatabaseIfNotExist=true&serverTimezone=UTC"
$env:DB_USERNAME="root"
$env:DB_PASSWORD="your_mysql_password"
mvn spring-boot:run
```

---

## 3. Running the Frontend (React + TypeScript)
```powershell
cd frontend
npm.cmd install
npm.cmd run dev
```
The frontend starts on `http://localhost:5173`.
CORS is pre-configured in Spring Boot to accept requests from `http://localhost:5173` and `http://127.0.0.1:5173`.
Includes open-source Leaflet map stack with zero paid API keys required (uses CartoDB Dark Matter / Positron and OpenStreetMap tiles).

---

## 4. Running Automated Tests
```powershell
cd backend
mvn test
```
Executes all unit tests for the DSA engine (Priority Queue, Interval Conflict Detector, Platform Scoring, Reallocation Engine, Corridor Graph).
