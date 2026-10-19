package com.railway.simulator.repository;

import com.railway.simulator.domain.AllocationHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AllocationHistoryRepository extends JpaRepository<AllocationHistory, Long> {
    List<AllocationHistory> findAllByOrderByTimestampDesc();
    List<AllocationHistory> findByStationCodeOrderByTimestampDesc(String stationCode);
    List<AllocationHistory> findByTrainNumberOrderByTimestampDesc(String trainNumber);
}
