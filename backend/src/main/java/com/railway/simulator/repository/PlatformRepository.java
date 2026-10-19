package com.railway.simulator.repository;

import com.railway.simulator.domain.Platform;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PlatformRepository extends JpaRepository<Platform, Long> {
    List<Platform> findByStation_StationIdOrderByPlatformNumberAsc(Long stationId);
    List<Platform> findByStationCodeOrderByPlatformNumberAsc(String stationCode);
    Optional<Platform> findByStationCodeAndPlatformNumber(String stationCode, Integer platformNumber);
    List<Platform> findByStationCodeAndIsClosedFalse(String stationCode);
}
