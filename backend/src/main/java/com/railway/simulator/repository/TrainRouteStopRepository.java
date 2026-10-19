package com.railway.simulator.repository;

import com.railway.simulator.domain.TrainRouteStop;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TrainRouteStopRepository extends JpaRepository<TrainRouteStop, Long> {
    List<TrainRouteStop> findByTrainIdOrderByStopSequenceAsc(Long trainId);
    List<TrainRouteStop> findByStationCode(String stationCode);
}
