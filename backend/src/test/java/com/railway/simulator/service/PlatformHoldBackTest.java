package com.railway.simulator.service;

import com.railway.simulator.config.SignalSpeedConfig;
import com.railway.simulator.domain.*;
import com.railway.simulator.repository.PlatformRepository;
import com.railway.simulator.repository.TrainRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
public class PlatformHoldBackTest {

    @Autowired
    private SimulationService simulationService;

    @Autowired
    private PlatformRepository platformRepository;

    @Autowired
    private TrainRepository trainRepository;

    @BeforeEach
    void setUp() {
        simulationService.resetSimulation();
    }

    @Test
    @DisplayName("Hold-back rule: express held before stop line outside station when all compatible platforms full, enters once freed")
    void testHoldBackWhenPlatformsFullAndProceedsWhenFreed() {
        // Station: Borivali (BVI). Compatible UP platforms for FAST/EXPRESS trains are P5 and P7 (FAST_UP).
        Platform p5 = platformRepository.findByStationCodeAndPlatformNumber("BVI", 5).orElseThrow();
        Platform p7 = platformRepository.findByStationCodeAndPlatformNumber("BVI", 7).orElseThrow();

        // Deactivate baseline train SIM-LOC-901 so it does not conflict on BLK-DIC-BVI-FU
        trainRepository.findByTrainNumber("SIM-LOC-901").ifPresent(t -> {
            t.setIsActive(false);
            trainRepository.save(t);
        });

        LocalDateTime simTime = simulationService.getSimulationTime();

        // 1. Fill every compatible UP platform at BVI with real berthed trains
        Train occ5 = new Train(
                "STAT-OCC-P5", "Occupier P5", TrainType.LOCAL,
                "VR", "CCG", 5, simTime, simTime.plusMinutes(20), "BVI", "BVI"
        );
        occ5.setStatus(TrainStatus.AT_PLATFORM);
        occ5.setCurrentBlockId("BLK-BVI-P5");
        occ5.setProgressPercent(50.0);
        occ5.setSpeedKmH(0.0);
        occ5.setAssignedPlatformNumber(5);
        occ5.setAssignedPlatformId(p5.getPlatformId());
        occ5.setIsActive(true);
        occ5 = trainRepository.save(occ5);

        p5.setIsOccupied(true);
        p5.setCurrentTrainId(occ5.getTrainId());
        p5.setCurrentTrainNumber(occ5.getTrainNumber());
        p5.setOccupancyStart(simTime);
        p5.setOccupancyEnd(simTime.plusMinutes(20));
        platformRepository.save(p5);

        TrackBlock tb5 = simulationService.getTrackBlocks().get("BLK-BVI-P5");
        if (tb5 != null) {
            tb5.occupy(occ5.getTrainId(), occ5.getTrainNumber());
        }

        Train occ7 = new Train(
                "STAT-OCC-P7", "Occupier P7", TrainType.LOCAL,
                "VR", "CCG", 5, simTime, simTime.plusMinutes(20), "BVI", "BVI"
        );
        occ7.setStatus(TrainStatus.AT_PLATFORM);
        occ7.setCurrentBlockId("BLK-BVI-P7");
        occ7.setProgressPercent(50.0);
        occ7.setSpeedKmH(0.0);
        occ7.setAssignedPlatformNumber(7);
        occ7.setAssignedPlatformId(p7.getPlatformId());
        occ7.setIsActive(true);
        occ7 = trainRepository.save(occ7);

        p7.setIsOccupied(true);
        p7.setCurrentTrainId(occ7.getTrainId());
        p7.setCurrentTrainNumber(occ7.getTrainNumber());
        p7.setOccupancyStart(simTime);
        p7.setOccupancyEnd(simTime.plusMinutes(20));
        platformRepository.save(p7);

        TrackBlock tb7 = simulationService.getTrackBlocks().get("BLK-BVI-P7");
        if (tb7 != null) {
            tb7.occupy(occ7.getTrainId(), occ7.getTrainNumber());
        }

        // Close slow UP platforms P1 and P2 so no slow line fallback can occur
        Platform p1 = platformRepository.findByStationCodeAndPlatformNumber("BVI", 1).orElseThrow();
        Platform p2 = platformRepository.findByStationCodeAndPlatformNumber("BVI", 2).orElseThrow();
        p1.setIsClosed(true);
        platformRepository.save(p1);
        p2.setIsClosed(true);
        platformRepository.save(p2);

        // 2. Send an Express train behind on BLK-DIC-BVI-FU approaching BVI
        Train express = new Train(
                "SIM-EXP-HOLDBACK",
                "HoldBack Express",
                TrainType.EXPRESS_MAIL,
                "VR",
                "MMCT",
                TrainType.EXPRESS_MAIL.getDefaultSimulationPriority(),
                simTime,
                simTime.plusMinutes(15),
                "BVI",
                "BVI"
        );
        express.setTrainCategory(TrainCategory.EXPRESS);
        express.setDirection("UP");
        express.setCurrentBlockId("BLK-DIC-BVI-FU");
        express.setProgressPercent(85.0);
        express.setSpeedKmH(70.0);
        express.setStatus(TrainStatus.APPROACHING);
        express.setAssignedPlatformNumber(5);
        express.setAssignedPlatformId(p5.getPlatformId());
        express.setStopStations(List.of("VR", "BVI", "AND", "DDR", "MMCT"));
        express.setIsActive(true);
        express = trainRepository.save(express);

        TrackBlock dicBvi = simulationService.getTrackBlocks().get("BLK-DIC-BVI-FU");
        if (dicBvi != null) {
            dicBvi.occupy(express.getTrainId(), express.getTrainNumber());
        }

        try {
            // 3. Advance simulation 1s at a time until train is held at the stop line
            for (int sec = 1; sec <= 60; sec++) {
                simulationService.advanceTrainStates(1L);
                Train t = trainRepository.findById(express.getTrainId()).orElseThrow();
                System.out.printf("SEC %d: blk=%s st=%s spd=%.1f prog=%.2f tgt=%s nextSig=%s sigAsp=%s%n",
                        sec, t.getCurrentBlockId(), t.getStatus(), t.getSpeedKmH(), t.getProgressPercent(), t.getTargetStationCode(), t.getNextSignalId(), t.getSignalAspect());
                if (t.getStatus() == TrainStatus.WAITING) {
                    break;
                }
            }

            Train heldTrain = trainRepository.findById(express.getTrainId()).orElseThrow();

            // Assert: it stops before the stop line (<= 98%), speed == 0, status is WAITING (HELD)
            assertTrue(heldTrain.getProgressPercent() <= SignalSpeedConfig.STOP_PROGRESS_PERCENT + 1e-4,
                    "Train must stop before the stop line (<= 98%): actual=" + heldTrain.getProgressPercent());
            assertEquals(0.0, heldTrain.getSpeedKmH(), 1e-4, "Train must come to a complete stop (0.0 km/h)");
            assertEquals(TrainStatus.WAITING, heldTrain.getStatus(), "Train must wait as WAITING/HELD outside the station");
            assertEquals(SignalAspect.RED, heldTrain.getSignalAspect(), "Signal aspect must be RED");

            // Assert: it stays OUT of the station block and does NOT skip Borivali
            assertEquals("BLK-DIC-BVI-FU", heldTrain.getCurrentBlockId(),
                    "Train must remain in approach block outside station, staying OUT of station platform block");
            assertEquals("BVI", heldTrain.getTargetStationCode(), "Train must not skip station because platforms are full");

            // 4. A platform frees: vacate P5
            trainRepository.delete(occ5);
            p5 = platformRepository.findByStationCodeAndPlatformNumber("BVI", 5).orElseThrow();
            p5.setIsOccupied(false);
            p5.setCurrentTrainId(null);
            p5.setCurrentTrainNumber(null);
            platformRepository.save(p5);
            if (tb5 != null) {
                tb5.vacate();
            }

            // 5. Advance simulation further: train must reserve P5, signal clear, enter and berth
            boolean enteredPlatform = false;
            boolean berthed = false;
            for (int sec = 1; sec <= 45; sec++) {
                simulationService.advanceTrainStates(1L);
                Train t = trainRepository.findById(express.getTrainId()).orElseThrow();
                if ("BLK-BVI-P5".equals(t.getCurrentBlockId())) {
                    enteredPlatform = true;
                }
                if (t.getStatus() == TrainStatus.AT_PLATFORM && "BLK-BVI-P5".equals(t.getCurrentBlockId())) {
                    berthed = true;
                    assertEquals(0.0, t.getSpeedKmH(), "Berthed train must be stopped at 0.0 km/h");
                    break;
                }
            }

            assertTrue(enteredPlatform, "Train must enter platform block BLK-BVI-P5 once freed");
            assertTrue(berthed, "Train must berth at platform P5");

        } finally {
            trainRepository.delete(express);
            trainRepository.delete(occ7);
            if (dicBvi != null && express.getTrainId().equals(dicBvi.getOccupyingTrainId())) {
                dicBvi.vacate();
            }
            if (tb7 != null && occ7.getTrainId().equals(tb7.getOccupyingTrainId())) {
                tb7.vacate();
            }
        }
    }
}
