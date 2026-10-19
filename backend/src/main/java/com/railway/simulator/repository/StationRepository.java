package com.railway.simulator.repository;

import com.railway.simulator.domain.Station;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface StationRepository extends JpaRepository<Station, Long> {
    Optional<Station> findByStationCode(String stationCode);
    List<Station> findAllByOrderBySequenceOrderAsc();
}
