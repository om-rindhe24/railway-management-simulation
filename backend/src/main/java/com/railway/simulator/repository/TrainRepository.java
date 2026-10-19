package com.railway.simulator.repository;

import com.railway.simulator.domain.Train;
import com.railway.simulator.domain.TrainStatus;
import com.railway.simulator.domain.TrainType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface TrainRepository extends JpaRepository<Train, Long> {
    Optional<Train> findByTrainNumber(String trainNumber);
    List<Train> findByStatus(TrainStatus status);
    List<Train> findByTrainType(TrainType trainType);
    List<Train> findByTargetStationCode(String stationCode);
    List<Train> findByCurrentStationCode(String stationCode);
    List<Train> findByIsActiveTrue();
    List<Train> findByActualArrivalBetween(LocalDateTime start, LocalDateTime end);
    List<Train> findByAssignedPlatformId(Long platformId);
}
