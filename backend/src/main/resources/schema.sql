-- Dynamic Railway Platform Allocation & Train Scheduling Simulator
-- Relational Database DDL Schema (MySQL 8.0 & H2 Compatible)

CREATE TABLE IF NOT EXISTS stations (
    station_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    station_code VARCHAR(10) NOT NULL UNIQUE,
    station_name VARCHAR(100) NOT NULL,
    number_of_platforms INT NOT NULL,
    local_train_support BOOLEAN NOT NULL DEFAULT TRUE,
    express_train_support BOOLEAN NOT NULL DEFAULT FALSE,
    fast_line_support BOOLEAN NOT NULL DEFAULT TRUE,
    slow_line_support BOOLEAN NOT NULL DEFAULT TRUE,
    terminal_flag BOOLEAN NOT NULL DEFAULT FALSE,
    sequence_order INT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    latitude DOUBLE,
    longitude DOUBLE
);

CREATE TABLE IF NOT EXISTS platforms (
    platform_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    station_id BIGINT NOT NULL,
    station_code_snapshot VARCHAR(10) NOT NULL,
    platform_number INT NOT NULL,
    line_type VARCHAR(20) NOT NULL,
    is_occupied BOOLEAN NOT NULL DEFAULT FALSE,
    is_closed BOOLEAN NOT NULL DEFAULT FALSE,
    closure_reason VARCHAR(255),
    current_train_id BIGINT,
    current_train_number VARCHAR(20),
    occupancy_start TIMESTAMP NULL,
    occupancy_end TIMESTAMP NULL,
    CONSTRAINT fk_platform_station FOREIGN KEY (station_id) REFERENCES stations(station_id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS platform_supported_train_types (
    platform_id BIGINT NOT NULL,
    train_type VARCHAR(30) NOT NULL,
    CONSTRAINT fk_pstt_platform FOREIGN KEY (platform_id) REFERENCES platforms(platform_id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS trains (
    train_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    train_number VARCHAR(20) NOT NULL UNIQUE,
    train_name VARCHAR(100) NOT NULL,
    train_type VARCHAR(30) NOT NULL,
    source_station VARCHAR(50) NOT NULL,
    destination_station VARCHAR(50) NOT NULL,
    simulation_priority INT NOT NULL,
    scheduled_arrival TIMESTAMP NOT NULL,
    scheduled_departure TIMESTAMP NOT NULL,
    actual_arrival TIMESTAMP NOT NULL,
    actual_departure TIMESTAMP NOT NULL,
    delay_minutes INT NOT NULL DEFAULT 0,
    status VARCHAR(30) NOT NULL,
    current_station_code VARCHAR(10) NOT NULL,
    next_station_code VARCHAR(10),
    target_station_code VARCHAR(10),
    assigned_platform_id BIGINT,
    assigned_platform_number INT,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    is_simulated BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS train_route_stops (
    stop_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    train_id BIGINT NOT NULL,
    stop_sequence INT NOT NULL,
    station_code VARCHAR(10) NOT NULL,
    scheduled_arrival_time TIME,
    scheduled_departure_time TIME,
    halt_duration_minutes INT NOT NULL DEFAULT 2,
    is_passed BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE TABLE IF NOT EXISTS platform_allocations (
    allocation_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    train_id BIGINT NOT NULL,
    train_number VARCHAR(20) NOT NULL,
    train_name VARCHAR(100) NOT NULL,
    platform_id BIGINT NOT NULL,
    platform_number INT NOT NULL,
    station_id BIGINT NOT NULL,
    station_code VARCHAR(10) NOT NULL,
    start_time TIMESTAMP NOT NULL,
    end_time TIMESTAMP NOT NULL,
    allocated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    selection_reason TEXT,
    scoring_breakdown TEXT,
    delay_minutes INT NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS allocation_history (
    history_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    train_number VARCHAR(20) NOT NULL,
    train_name VARCHAR(100) NOT NULL,
    station_code VARCHAR(10) NOT NULL,
    from_platform_number INT,
    to_platform_number INT NOT NULL,
    change_type VARCHAR(30) NOT NULL,
    timestamp TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    reason TEXT NOT NULL,
    delay_impact_minutes INT NOT NULL DEFAULT 0,
    conflicts_avoided INT NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS simulation_events (
    event_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    event_type VARCHAR(50) NOT NULL,
    severity VARCHAR(20) NOT NULL,
    timestamp TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    message TEXT NOT NULL,
    affected_train_number VARCHAR(20),
    affected_platform_number INT,
    affected_station_code VARCHAR(10)
);

CREATE INDEX idx_stations_seq ON stations(sequence_order);
CREATE INDEX idx_platforms_st ON platforms(station_id);
CREATE INDEX idx_trains_st ON trains(target_station_code, status);
CREATE INDEX idx_alloc_plat_time ON platform_allocations(platform_id, start_time, end_time);
CREATE INDEX idx_history_time ON allocation_history(timestamp DESC);
