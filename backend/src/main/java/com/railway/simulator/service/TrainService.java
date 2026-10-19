package com.railway.simulator.service;

import com.railway.simulator.domain.*;
import com.railway.simulator.repository.SimulationEventRepository;
import com.railway.simulator.repository.TrainRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@Transactional(readOnly = true)
public class TrainService {

    private final TrainRepository trainRepository;
    private final SimulationEventRepository eventRepository;

    public TrainService(TrainRepository trainRepository,
                        SimulationEventRepository eventRepository) {
        this.trainRepository = trainRepository;
        this.eventRepository = eventRepository;
    }

    public List<Train> getAllTrains() {
        return trainRepository.findAll();
    }

    public List<Train> getActiveTrains() {
        return trainRepository.findByIsActiveTrue();
    }

    public Optional<Train> getTrainById(Long id) {
        return trainRepository.findById(id);
    }

    public Optional<Train> getTrainByNumber(String trainNumber) {
        return trainRepository.findByTrainNumber(trainNumber);
    }

    public List<Train> getTrainsByStation(String stationCode) {
        return trainRepository.findByTargetStationCode(stationCode.toUpperCase());
    }

    public List<Train> getTrainsByType(TrainType type) {
        return trainRepository.findByTrainType(type);
    }

    public List<Train> getTrainsByStatus(TrainStatus status) {
        return trainRepository.findByStatus(status);
    }

    @Transactional
    public Train createTrain(Train train) {
        train.setCreatedAt(LocalDateTime.now());
        train.setUpdatedAt(LocalDateTime.now());
        Train saved = trainRepository.save(train);

        eventRepository.save(new SimulationEvent(
                "TRAIN_SCHEDULED",
                Severity.INFO,
                String.format("Train %s (%s) registered into schedule. Route: %s -> %s",
                        train.getTrainNumber(), train.getTrainName(), train.getSourceStation(), train.getDestinationStation()),
                train.getTrainNumber(),
                null,
                train.getTargetStationCode()
        ));

        return saved;
    }

    @Transactional
    public Train updateTrain(Long id, Train updatedData) {
        Train existing = trainRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Train not found: " + id));

        existing.setTrainName(updatedData.getTrainName());
        existing.setTrainType(updatedData.getTrainType());
        existing.setSimulationPriority(updatedData.getSimulationPriority());
        existing.setStatus(updatedData.getStatus());
        existing.setTargetStationCode(updatedData.getTargetStationCode());
        existing.setUpdatedAt(LocalDateTime.now());

        return trainRepository.save(existing);
    }

    @Transactional
    public Train updateDelay(Long id, int delayMinutes, String reason) {
        Train train = trainRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Train not found: " + id));

        train.setDelayMinutes(delayMinutes);
        train.setActualArrival(train.getScheduledArrival().plusMinutes(delayMinutes));
        train.setActualDeparture(train.getScheduledDeparture().plusMinutes(delayMinutes));
        if (delayMinutes > 0 && train.getStatus() == TrainStatus.SCHEDULED) {
            train.setStatus(TrainStatus.DELAYED);
        }
        train.setUpdatedAt(LocalDateTime.now());
        Train saved = trainRepository.save(train);

        eventRepository.save(new SimulationEvent(
                "TRAIN_DELAYED",
                delayMinutes > 15 ? Severity.CRITICAL : Severity.WARNING,
                String.format("Train %s delayed by %d mins: %s", train.getTrainNumber(), delayMinutes, reason != null ? reason : "Operational hold"),
                train.getTrainNumber(),
                train.getAssignedPlatformNumber(),
                train.getTargetStationCode()
        ));

        return saved;
    }

    @Transactional
    public Train cancelTrain(Long id, String reason) {
        Train train = trainRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Train not found: " + id));

        train.setStatus(TrainStatus.CANCELLED);
        train.setIsActive(false);
        train.setUpdatedAt(LocalDateTime.now());
        Train saved = trainRepository.save(train);

        eventRepository.save(new SimulationEvent(
                "TRAIN_CANCELLED",
                Severity.CRITICAL,
                String.format("Train %s cancelled: %s", train.getTrainNumber(), reason != null ? reason : "Operational cancellation"),
                train.getTrainNumber(),
                train.getAssignedPlatformNumber(),
                train.getTargetStationCode()
        ));

        return saved;
    }

    @Transactional
    public void deleteTrain(Long id) {
        trainRepository.deleteById(id);
    }
}
