package com.railway.simulator.service;

import com.railway.simulator.algorithm.CorridorGraph;
import com.railway.simulator.controller.dto.TrainSnapshotDto;
import com.railway.simulator.domain.BlockType;
import com.railway.simulator.domain.TrackBlock;
import com.railway.simulator.domain.TrainStatus;
import com.railway.simulator.domain.Train;
import com.railway.simulator.repository.TrainRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class TrainRouteProgressConsistencyTest {

    @Autowired
    private SimulationService simulationService;

    @Autowired
    private AllocationService allocationService;

    @Autowired
    private TrainRepository trainRepository;

    private CorridorGraph corridorGraph;

    @BeforeEach
    void setup() {
        simulationService.resetSimulation();
        allocationService.runStationAllocation("BVI");
        allocationService.runStationAllocation("AND");
        simulationService.syncSignallingState();
        corridorGraph = simulationService.getCorridorGraph();
    }

    @Test
    @DisplayName("Explicit Next Stop assertions at baseline t=0 for SIM-LOC-903, SIM-FRT-8001, and SIM-LOC-909")
    void testBaselineExplicitTrainEvidence() {
        var state = simulationService.getCorridorState();
        Map<String, TrainSnapshotDto> trainMap = state.getTrains().stream()
                .collect(java.util.stream.Collectors.toMap(TrainSnapshotDto::getTrainNumber, t -> t));

        // 1. SIM-LOC-903 (FAST_LOCAL, UP) on BLK-VR-NSP-FU: must show BSR (not AND)
        TrainSnapshotDto t903 = trainMap.get("SIM-LOC-903");
        assertNotNull(t903, "SIM-LOC-903 must exist");
        assertEquals("BLK-VR-NSP-FU", t903.getCurrentBlockId());
        assertEquals("VR", t903.getLastStationCode());
        assertEquals("NSP", t903.getUpcomingStationCode());
        assertEquals("BSR", t903.getNextStopStationCode(), "SIM-LOC-903 next stop must be BSR, not skipped station AND");

        // 2. SIM-FRT-8001 (FREIGHT, UP) on BLK-NSP-BSR-SU: nextStop must be null (Non-stop), never passed station VR or non-corridor JNPT
        TrainSnapshotDto t8001 = trainMap.get("SIM-FRT-8001");
        assertNotNull(t8001, "SIM-FRT-8001 must exist");
        assertEquals("BLK-NSP-BSR-SU", t8001.getCurrentBlockId());
        assertEquals("NSP", t8001.getLastStationCode());
        assertEquals("BSR", t8001.getUpcomingStationCode());
        assertNotEquals("VR", t8001.getNextStopStationCode(), "SIM-FRT-8001 next stop must never be already passed station VR");
        assertNull(t8001.getNextStopStationCode(), "SIM-FRT-8001 next stop must be null (Non-stop), never expose non-corridor code JNPT");

        // 3. SIM-LOC-909 (FAST_LOCAL, UP) on BLK-MLD-GMN-FU: must not be MLD (already passed), but AND
        TrainSnapshotDto t909 = trainMap.get("SIM-LOC-909");
        assertNotNull(t909, "SIM-LOC-909 must exist");
        assertEquals("BLK-MLD-GMN-FU", t909.getCurrentBlockId());
        assertEquals("MLD", t909.getLastStationCode());
        assertEquals("GMN", t909.getUpcomingStationCode());
        assertNotEquals("MLD", t909.getNextStopStationCode(), "SIM-LOC-909 next stop must not be already passed MLD");
        assertEquals("AND", t909.getNextStopStationCode(), "SIM-LOC-909 next stop must be next fast stop AND");
    }

    @Test
    @DisplayName("Verify nextStop, lastStation, upcomingStation for all seeded trains at t=0 and every 60s for 30 minutes")
    void testRouteProgressForAllTrainsOver30Minutes() {
        for (int minute = 0; minute <= 30; minute++) {
            List<Train> allTrains = trainRepository.findAll();
            assertEquals(27, allTrains.size(), "There must be 27 seeded trains in database at minute " + minute);

            var state = simulationService.getCorridorState();
            Map<String, TrainSnapshotDto> snapshotMap = state.getTrains().stream()
                    .collect(java.util.stream.Collectors.toMap(TrainSnapshotDto::getTrainNumber, t -> t));

            for (Train train : allTrains) {
                if (train.getStatus() == TrainStatus.DEPARTED || train.getStatus() == TrainStatus.CANCELLED || Boolean.FALSE.equals(train.getIsActive())) {
                    continue;
                }
                TrainSnapshotDto dto = snapshotMap.get(train.getTrainNumber());
                assertNotNull(dto, "Active train " + train.getTrainNumber() + " must have a snapshot in corridor state at minute " + minute);

                String blockId = dto.getCurrentBlockId();
                assertNotNull(blockId, "Active train " + dto.getTrainNumber() + " must have a currentBlockId");

                // Check block matching for lastStation and upcomingStation
                boolean isPlatform = dto.getStatus() == TrainStatus.AT_PLATFORM || (blockId != null && blockId.matches(".*-P\\d+"));
                if (isPlatform) {
                    String[] parts = blockId.split("-");
                    String platStation = parts[1];
                    assertEquals(platStation, dto.getLastStationCode(),
                            "Train " + dto.getTrainNumber() + " at platform block " + blockId + " lastStation must match " + platStation);
                    assertNull(dto.getUpcomingStationCode(),
                            "Train " + dto.getTrainNumber() + " at platform block must have null upcomingStation");
                } else {
                    String[] parts = blockId.split("-");
                    assertEquals(parts[1], dto.getLastStationCode(),
                            "Train " + dto.getTrainNumber() + " inter-station block " + blockId + " lastStation must match block from");
                    assertEquals(parts[2], dto.getUpcomingStationCode(),
                            "Train " + dto.getTrainNumber() + " inter-station block " + blockId + " upcomingStation must match block to");
                }

                // Check nextStopStationCode
                String nextStop = dto.getNextStopStationCode();
                if (nextStop != null) {
                    boolean isUp = "UP".equalsIgnoreCase(dto.getDirection());
                    CorridorGraph.StationNode lastNode = corridorGraph.getStation(dto.getLastStationCode());
                    int curSeq = lastNode != null ? lastNode.getSequenceOrder() : (isUp ? 0 : 25);

                    List<String> stops = dto.getStopStations();
                    if (stops != null && !stops.isEmpty()) {
                        // nextStop must be in the stop pattern, or final destination once stops are passed
                        assertTrue(stops.contains(nextStop) || nextStop.equals(dto.getDestinationStation()),
                                "Train " + dto.getTrainNumber() + " nextStop " + nextStop + " must be in stop pattern or be destination");

                        CorridorGraph.StationNode nextNode = corridorGraph.getStation(nextStop);
                        if (nextNode != null) {
                            int nextSeq = nextNode.getSequenceOrder();
                            if (isUp) {
                                assertTrue(nextSeq >= curSeq,
                                        "UP train " + dto.getTrainNumber() + " nextStop " + nextStop + " (" + nextSeq + ") must be ahead of lastStation " + dto.getLastStationCode() + " (" + curSeq + ") at minute " + minute);
                            } else {
                                assertTrue(nextSeq <= curSeq,
                                        "DOWN train " + dto.getTrainNumber() + " nextStop " + nextStop + " (" + nextSeq + ") must be ahead of lastStation " + dto.getLastStationCode() + " (" + curSeq + ") at minute " + minute);
                            }
                        }
                    } else {
                        // Freight or Special without stops in corridor: nextStop must be null (Non-stop)
                        assertNull(nextStop,
                                "Freight/Special train " + dto.getTrainNumber() + " nextStop must be null (Non-stop)");
                    }
                }
            }


            if (minute < 30) {
                simulationService.advanceTrainStates(60L);
            }
        }
    }
}
