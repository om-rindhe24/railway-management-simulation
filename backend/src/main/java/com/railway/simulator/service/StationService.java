package com.railway.simulator.service;

import com.railway.simulator.domain.Station;
import com.railway.simulator.repository.StationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@Transactional(readOnly = true)
public class StationService {

    private final StationRepository stationRepository;

    public StationService(StationRepository stationRepository) {
        this.stationRepository = stationRepository;
    }

    public List<Station> getAllStations() {
        return stationRepository.findAllByOrderBySequenceOrderAsc();
    }

    public Optional<Station> getStationByCode(String stationCode) {
        return stationRepository.findByStationCode(stationCode.toUpperCase());
    }

    public Optional<Station> getStationById(Long stationId) {
        return stationRepository.findById(stationId);
    }

    @Transactional
    public Station saveStation(Station station) {
        return stationRepository.save(station);
    }
}
