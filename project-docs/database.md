# Database Schema & Persistence Strategy

## 1. Dual-Database Strategy & Security
* **H2 Embedded In-Memory (Default)**:
  * Automatically boots with standard Spring Boot profile with zero setup.
  * Creates all tables and seeds a **configurable simulated platform configuration for the 24-station corridor** alongside clearly labelled simulated train data.
  * Ensures zero-friction running for evaluators, CI environments, and technical placement interviews.
* **MySQL 8.0 (Production Profile: `mysql`)**:
  * Activated by setting environment variable `SPRING_PROFILES_ACTIVE=mysql`.
  * **Zero Hardcoded Secrets**: Passwords and connection credentials are NEVER configured or committed directly in source code.
  * Required environment variables:
    * `DB_URL` (e.g. `jdbc:mysql://localhost:3306/railway_simulator?createDatabaseIfNotExist=true`)
    * `DB_USERNAME`
    * `DB_PASSWORD`
  * Fallbacks in `application-mysql.properties` read directly from `${DB_URL:...}`, `${DB_USERNAME:...}`, `${DB_PASSWORD:...}`.

---

## 2. Relational Schema & Tables

### `stations`
* `station_id` (BIGINT, PK, Auto-Increment)
* `station_code` (VARCHAR(10), UNIQUE, NOT NULL, e.g. 'VR', 'BVI', 'MMCT')
* `station_name` (VARCHAR(100), NOT NULL)
* `number_of_platforms` (INT, NOT NULL)
* `local_train_support` (BOOLEAN, NOT NULL)
* `express_train_support` (BOOLEAN, NOT NULL)
* `fast_line_support` (BOOLEAN, NOT NULL)
* `slow_line_support` (BOOLEAN, NOT NULL)
* `terminal_flag` (BOOLEAN, NOT NULL)
* `sequence_order` (INT, NOT NULL)
* `status` (VARCHAR(20), NOT NULL, default 'ACTIVE')

### `platforms`
* `platform_id` (BIGINT, PK, Auto-Increment)
* `station_id` (BIGINT, FK -> stations.station_id)
* `platform_number` (INT, NOT NULL)
* `line_type` (VARCHAR(20), NOT NULL, e.g. 'SLOW_UP', 'SLOW_DOWN', 'FAST_UP', 'FAST_DOWN', 'COMMON')
* `is_occupied` (BOOLEAN, NOT NULL, default FALSE)
* `is_closed` (BOOLEAN, NOT NULL, default FALSE)
* `current_train_id` (BIGINT, NULLABLE)
* `occupancy_start` (DATETIME, NULLABLE)
* `occupancy_end` (DATETIME, NULLABLE)

### `trains`
* `train_id` (BIGINT, PK, Auto-Increment)
* `train_number` (VARCHAR(20), UNIQUE, NOT NULL)
* `train_name` (VARCHAR(100), NOT NULL)
* `train_type` (VARCHAR(30), NOT NULL, e.g. 'LOCAL', 'EXPRESS_MAIL', 'SUPERFAST', 'PREMIUM', 'SPECIAL', 'FREIGHT')
* `source_station` (VARCHAR(50), NOT NULL)
* `destination_station` (VARCHAR(50), NOT NULL)
* `simulation_priority` (INT, NOT NULL, 1 to 6)
* `scheduled_arrival` (DATETIME, NOT NULL)
* `scheduled_departure` (DATETIME, NOT NULL)
* `actual_arrival` (DATETIME, NOT NULL)
* `actual_departure` (DATETIME, NOT NULL)
* `delay_minutes` (INT, NOT NULL, default 0)
* `status` (VARCHAR(30), NOT NULL, e.g. 'SCHEDULED', 'APPROACHING', 'ARRIVING', 'AT_PLATFORM', 'DEPARTED', 'DELAYED', 'WAITING', 'CANCELLED', 'REASSIGNED')
* `current_station_code` (VARCHAR(10), NOT NULL)
* `next_station_code` (VARCHAR(10), NULLABLE)
* `assigned_platform_id` (BIGINT, FK -> platforms.platform_id, NULLABLE)
* `is_active` (BOOLEAN, NOT NULL, default TRUE)

### `platform_allocations`
* `allocation_id` (BIGINT, PK, Auto-Increment)
* `train_id` (BIGINT, FK -> trains.train_id)
* `platform_id` (BIGINT, FK -> platforms.platform_id)
* `station_id` (BIGINT, FK -> stations.station_id)
* `start_time` (DATETIME, NOT NULL)
* `end_time` (DATETIME, NOT NULL)
* `allocated_at` (DATETIME, NOT NULL)
* `status` (VARCHAR(20), NOT NULL, 'ACTIVE', 'COMPLETED', 'CANCELLED', 'REALLOCATED')
* `selection_reason` (TEXT, NULLABLE)
* `scoring_breakdown` (TEXT, NULLABLE)

### `allocation_history`
* `history_id` (BIGINT, PK, Auto-Increment)
* `train_number` (VARCHAR(20), NOT NULL)
* `train_name` (VARCHAR(100), NOT NULL)
* `station_code` (VARCHAR(10), NOT NULL)
* `from_platform_number` (INT, NULLABLE)
* `to_platform_number` (INT, NOT NULL)
* `change_type` (VARCHAR(30), NOT NULL, 'INITIAL_ALLOCATION', 'DYNAMIC_REALLOCATION', 'EMERGENCY_OVERRIDE', 'PLATFORM_CLOSURE')
* `timestamp` (DATETIME, NOT NULL)
* `reason` (TEXT, NOT NULL)
* `delay_impact_minutes` (INT, NOT NULL, default 0)
* `conflicts_avoided` (INT, NOT NULL, default 0)

### `simulation_events`
* `event_id` (BIGINT, PK, Auto-Increment)
* `event_type` (VARCHAR(50), NOT NULL)
* `severity` (VARCHAR(20), NOT NULL, 'INFO', 'WARNING', 'CRITICAL', 'SUCCESS')
* `timestamp` (DATETIME, NOT NULL)
* `message` (TEXT, NOT NULL)
* `affected_train_number` (VARCHAR(20), NULLABLE)
* `affected_platform_number` (INT, NULLABLE)

---

## 3. Key Indexes
* `idx_platforms_station` on `platforms(station_id)`
* `idx_allocations_time` on `platform_allocations(platform_id, start_time, end_time)`
* `idx_trains_status` on `trains(status, is_active)`
* `idx_history_timestamp` on `allocation_history(timestamp DESC)`
