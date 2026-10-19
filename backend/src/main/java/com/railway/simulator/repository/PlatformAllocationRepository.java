package com.railway.simulator.repository;

import com.railway.simulator.domain.AllocationStatus;
import com.railway.simulator.domain.PlatformAllocation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface PlatformAllocationRepository extends JpaRepository<PlatformAllocation, Long> {
    List<PlatformAllocation> findByStationCodeAndStatus(String stationCode, AllocationStatus status);
    List<PlatformAllocation> findByPlatformIdAndStatus(Long platformId, AllocationStatus status);
    Optional<PlatformAllocation> findByTrainIdAndStatus(Long trainId, AllocationStatus status);
    List<PlatformAllocation> findByStatus(AllocationStatus status);

    @Query("SELECT a FROM PlatformAllocation a WHERE a.platformId = :platformId AND a.status = 'ACTIVE' AND a.startTime < :endTime AND a.endTime > :startTime")
    List<PlatformAllocation> findOverlappingAllocations(@Param("platformId") Long platformId,
                                                        @Param("startTime") LocalDateTime startTime,
                                                        @Param("endTime") LocalDateTime endTime);
}
