package com.railway.simulator.service;

import com.railway.simulator.config.StoppingPatternConfig;
import com.railway.simulator.domain.*;
import com.railway.simulator.repository.PlatformRepository;
import com.railway.simulator.repository.StationRepository;
import com.railway.simulator.repository.TrainRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.function.BiPredicate;

/**
 * Seeds the simulation corridor with 24 authentic Western Railway stations,
 * Configurable simulated platform configuration for the 24-station corridor, and clearly labelled simulated train data.
 *
 * All platform counts, line mappings, and schedules are Configurable Simulation Data.
 */
@Service
public class DataSeederService implements CommandLineRunner {

    private final StationRepository stationRepository;
    private final PlatformRepository platformRepository;
    private final TrainRepository trainRepository;
    private final AllocationService allocationService;
    private final com.railway.simulator.algorithm.CorridorGraph corridorGraph = new com.railway.simulator.algorithm.CorridorGraph();

    public DataSeederService(StationRepository stationRepository,
                             PlatformRepository platformRepository,
                             TrainRepository trainRepository,
                             AllocationService allocationService) {
        this.stationRepository = stationRepository;
        this.platformRepository = platformRepository;
        this.trainRepository = trainRepository;
        this.allocationService = allocationService;
    }

    @Override
    @Transactional
    public void run(String... args) {
        if (stationRepository.count() > 0) {
            return; // Already initialized
        }

        seedCorridorStationsAndPlatforms();
        seedSimulatedTrains();
        // Run initial platform allocations for corridor hub and key intermediate stations
        allocationService.runStationAllocation("BVI");
        allocationService.runStationAllocation("AND");
        allocationService.runStationAllocation("BA");
        allocationService.runStationAllocation("DDR");
        allocationService.runStationAllocation("MMCT");
        allocationService.runStationAllocation("VR");
        allocationService.runStationAllocation("BSR");
        allocationService.runStationAllocation("BYR");
        allocationService.runStationAllocation("MLD");
        allocationService.runStationAllocation("GMN");
    }

    private void seedCorridorStationsAndPlatforms() {
        // [Code, Name, PlatformCount, LocalSupport, ExpressSupport, FastLine, SlowLine, Terminal, Seq, Lat, Lng]
        Object[][] stationsData = {
            {"VR", "Virar", 6, true, true, true, true, true, 1, 19.4538, 72.8113},
            {"NSP", "Nalasopara", 4, true, false, true, true, false, 2, 19.4182, 72.8188},
            {"BSR", "Vasai Road", 5, true, true, true, true, false, 3, 19.3804, 72.8335},
            {"NIG", "Naigaon", 2, true, false, false, true, false, 4, 19.3518, 72.8486},
            {"BYR", "Bhayandar", 6, true, true, true, true, false, 5, 19.3134, 72.8530},
            {"MIRA", "Mira Road", 4, true, false, true, true, false, 6, 19.2810, 72.8566},
            {"DIC", "Dahisar", 4, true, false, false, true, false, 7, 19.2562, 72.8596},
            {"BVI", "Borivali", 8, true, true, true, true, true, 8, 19.2294, 72.8573},
            {"KND", "Kandivali", 4, true, false, false, true, false, 9, 19.2045, 72.8523},
            {"MLD", "Malad", 4, true, false, true, true, false, 10, 19.1868, 72.8486},
            {"GMN", "Goregaon", 4, true, false, true, true, false, 11, 19.1643, 72.8488},
            {"JOS", "Jogeshwari", 2, true, false, false, true, false, 12, 19.1352, 72.8493},
            {"AND", "Andheri", 8, true, true, true, true, true, 13, 19.1197, 72.8468},
            {"VLP", "Vile Parle", 2, true, false, false, true, false, 14, 19.0998, 72.8441},
            {"STC", "Santacruz", 2, true, false, false, true, false, 15, 19.0827, 72.8417},
            {"KHR", "Khar Road", 2, true, false, false, true, false, 16, 19.0699, 72.8398},
            {"BA", "Bandra", 6, true, true, true, true, true, 17, 19.0553, 72.8406},
            {"MM", "Mahim", 2, true, false, false, true, false, 18, 19.0409, 72.8436},
            {"MRU", "Matunga Road", 2, true, false, false, true, false, 19, 19.0278, 72.8437},
            {"DDR", "Dadar", 7, true, true, true, true, true, 20, 19.0185, 72.8427},
            {"PR", "Prabhadevi", 2, true, false, false, true, false, 21, 19.0118, 72.8358},
            {"LP", "Lower Parel", 2, true, false, false, true, false, 22, 18.9957, 72.8310},
            {"MX", "Mahalaxmi", 2, true, false, false, true, false, 23, 18.9828, 72.8242},
            {"MMCT", "Mumbai Central", 5, true, true, true, true, true, 24, 18.9696, 72.8194}
        };

        for (Object[] row : stationsData) {
            Station station = new Station(
                (String) row[0],
                (String) row[1],
                (Integer) row[2],
                (Boolean) row[3],
                (Boolean) row[4],
                (Boolean) row[5],
                (Boolean) row[6],
                (Boolean) row[7],
                (Integer) row[8],
                (Double) row[9],
                (Double) row[10]
            );
            Station savedStation = stationRepository.save(station);

            int pCount = (Integer) row[2];
            for (int p = 1; p <= pCount; p++) {
                LineType lineType = com.railway.simulator.config.PlatformLineConfig.getPlatformLineType(savedStation.getStationCode(), p);
                String direction = com.railway.simulator.config.PlatformLineConfig.getDirection(lineType);
                Set<TrainType> supportedTypes = new HashSet<>();
                supportedTypes.add(TrainType.LOCAL);
                supportedTypes.add(TrainType.EXPRESS_MAIL);
                supportedTypes.add(TrainType.SUPERFAST);
                supportedTypes.add(TrainType.PREMIUM);
                supportedTypes.add(TrainType.SPECIAL);
                supportedTypes.add(TrainType.FREIGHT);

                Platform platform = new Platform(savedStation, p, lineType, supportedTypes);
                platform.setDirection(direction);
                platformRepository.save(platform);
            }
        }
    }

    private void seedSimulatedTrains() {
        LocalDateTime base = LocalDateTime.of(2026, 9, 18, 10, 0);

        // Corridor-wide distributed simulated train schedule
        Object[][] trainData = {
            // [Number, Name, Type, Source, Destination, Priority, ArrDelta, DepDelta, TargetStation, InitialBlock, Progress, Speed, Status, WaitingReason]
            {"SIM-LOC-901", "Simulation Fast Local (VR-CCG)", TrainType.LOCAL, "VR", "CCG", 5, 2, 6, "BVI", "BLK-DIC-BVI-FU", 45.0, 65.0, TrainStatus.APPROACHING, null},
            {"SIM-LOC-902", "Simulation Slow Local (VR-CCG)", TrainType.LOCAL, "VR", "CCG", 5, 5, 10, "BVI", "BLK-DIC-BVI-SU", 50.0, 50.0, TrainStatus.APPROACHING, null},
            {"SIM-VB-20901", "Simulation Vande Bharat Express", TrainType.PREMIUM, "MMCT", "GNC", 2, 8, 14, "BVI", "BLK-AND-JOS-FD", 30.0, 95.0, TrainStatus.APPROACHING, null},
            {"SIM-RAJ-12951", "Simulation Rajdhani Service", TrainType.PREMIUM, "MMCT", "NDLS", 2, 12, 18, "BVI", "BLK-KHR-STC-FD", 60.0, 50.0, TrainStatus.APPROACHING, null},
            {"SIM-SF-12953", "Simulation August Kranti Express", TrainType.SUPERFAST, "MMCT", "NZM", 3, 15, 22, "DDR", "BLK-DDR-P6", 0.0, 0.0, TrainStatus.AT_PLATFORM, null},
            {"SIM-LOC-903", "Simulation Virar Fast Local", TrainType.LOCAL, "VR", "CCG", 5, 19, 24, "AND", "BLK-VR-NSP-FU", 20.0, 70.0, TrainStatus.APPROACHING, null},
            {"SIM-EXP-19015", "Simulation Saurashtra Express", TrainType.EXPRESS_MAIL, "VR", "MMCT", 4, 25, 33, "BA", "BLK-KND-MLD-FU", 40.0, 60.0, TrainStatus.APPROACHING, null},
            {"SIM-LOC-904", "Simulation Borivali Slow Local", TrainType.LOCAL, "BVI", "CCG", 5, 0, 8, "BVI", "BLK-BVI-P2", 0.0, 0.0, TrainStatus.AT_PLATFORM, null},
            {"SIM-SF-22927", "Simulation Lokshakti Superfast", TrainType.SUPERFAST, "BDTS", "ADI", 3, 0, 10, "BA", "BLK-BA-P6", 0.0, 0.0, TrainStatus.AT_PLATFORM, null},
            {"SIM-FRT-8001", "Simulation Container Freight", TrainType.FREIGHT, "VR", "JNPT", 6, 40, 52, "VR", "BLK-NSP-BSR-SU", 80.0, 45.0, TrainStatus.APPROACHING, null},
            {"SIM-EXP-12925", "Simulation Paschim Express", TrainType.EXPRESS_MAIL, "MMCT", "ASR", 4, 0, 12, "AND", "BLK-AND-P6", 0.0, 0.0, TrainStatus.AT_PLATFORM, null},
            {"SIM-SPL-09001", "Simulation Festival Special", TrainType.SPECIAL, "VR", "MMCT", 1, 50, 58, "BVI", "BLK-MIRA-DIC-SU", 98.0, 0.0, TrainStatus.WAITING, "Waiting at signal SIG-DIC-BVI-SU: Protected block BLK-DIC-BVI-SU is OCCUPIED by train SIM-LOC-902"},
            {"SIM-LOC-905", "Simulation Dahanu Local", TrainType.LOCAL, "VR", "CCG", 5, 54, 60, "MMCT", "BLK-LP-MX-SU", 35.0, 55.0, TrainStatus.APPROACHING, null},
            {"SIM-LOC-906", "Simulation Churchgate Fast Local", TrainType.LOCAL, "VR", "CCG", 5, 62, 68, "DDR", "BLK-MRU-DDR-FU", 65.0, 50.0, TrainStatus.APPROACHING, null},
            {"SIM-EMG-01", "Simulation Emergency Relief Train", TrainType.SPECIAL, "VR", "MMCT", 1, 0, 60, "MMCT", "BLK-MMCT-P5", 0.0, 0.0, TrainStatus.AT_PLATFORM, null},
            {"SIM-EXP-12955", "Simulation Flying Ranee", TrainType.EXPRESS_MAIL, "ST", "MMCT", 4, 14, 20, "BSR", "BLK-NSP-BSR-FU", 60.0, 75.0, TrainStatus.APPROACHING, null},
            {"SIM-LOC-908", "Simulation Bhayandar Shuttle", TrainType.LOCAL, "BYR", "CCG", 5, 0, 10, "BYR", "BLK-BYR-P1", 0.0, 0.0, TrainStatus.AT_PLATFORM, null},
            {"SIM-LOC-909", "Simulation Malad Fast Local", TrainType.LOCAL, "VR", "CCG", 5, 8, 14, "MLD", "BLK-MLD-GMN-FU", 35.0, 65.0, TrainStatus.APPROACHING, null},
            {"SIM-LOC-910", "Simulation Goregaon Slow Local", TrainType.LOCAL, "VR", "CCG", 5, 0, 8, "GMN", "BLK-GMN-P1", 0.0, 0.0, TrainStatus.AT_PLATFORM, null},
            {"SIM-EXP-22903", "Simulation Bandra-Bhuj AC Superfast", TrainType.SUPERFAST, "BDTS", "BHUJ", 3, 22, 32, "KHR", "BLK-BA-KHR-FD", 45.0, 55.0, TrainStatus.APPROACHING, null},
            {"SIM-LOC-912", "Simulation Dadar Express Link", TrainType.LOCAL, "VR", "CCG", 5, 0, 12, "DDR", "BLK-DDR-P1", 0.0, 0.0, TrainStatus.AT_PLATFORM, null},
            {"SIM-SF-12951B", "Simulation Tejas Express", TrainType.PREMIUM, "MMCT", "ADI", 2, 0, 25, "MMCT", "BLK-MMCT-P4", 0.0, 0.0, TrainStatus.AT_PLATFORM, null},
            // Balanced DOWN Direction Traffic: 3 Slow Locals and 2 Fast Locals
            {"SIM-LOC-921", "Simulation Virar Slow Local", TrainType.LOCAL, "MMCT", "VR", 5, 0, 8, "MMCT", "BLK-MMCT-P2", 0.0, 0.0, TrainStatus.AT_PLATFORM, null},
            {"SIM-LOC-922", "Simulation Borivali Slow Local (DN)", TrainType.LOCAL, "CCG", "VR", 5, 15, 20, "MLD", "BLK-GMN-MLD-SD", 40.0, 50.0, TrainStatus.APPROACHING, null},
            {"SIM-LOC-923", "Simulation Dahanu Slow Local (DN)", TrainType.LOCAL, "CCG", "VR", 5, 25, 30, "BYR", "BLK-MIRA-BYR-SD", 50.0, 50.0, TrainStatus.APPROACHING, null},
            {"SIM-LOC-924", "Simulation Bhayandar Fast Local (DN)", TrainType.LOCAL, "CCG", "VR", 5, 0, 10, "BYR", "BLK-BYR-P6", 0.0, 0.0, TrainStatus.AT_PLATFORM, null},
            {"SIM-LOC-925", "Simulation Vasai Fast Local (DN)", TrainType.LOCAL, "CCG", "VR", 5, 8, 14, "VR", "BLK-BSR-NSP-FD", 40.0, 70.0, TrainStatus.APPROACHING, null}
        };

        List<Platform> allPlatforms = platformRepository.findAll();
        BiPredicate<String, LineType> platformChecker = (stn, lt) -> allPlatforms.stream().anyMatch(p ->
                p.getStationCode().equalsIgnoreCase(stn) &&
                (p.getLineType() == LineType.COMMON || p.getLineType() == lt ||
                 (lt.name().contains("FAST") && (p.getLineType() == LineType.FAST_UP || p.getLineType() == LineType.FAST_DOWN || p.getLineType() == LineType.COMMON)))
        );

        for (Object[] row : trainData) {
            String num = (String) row[0];
            String name = (String) row[1];
            TrainType type = (TrainType) row[2];
            String src = (String) row[3];
            String dest = (String) row[4];
            int prio = (Integer) row[5];
            int arrDelta = (Integer) row[6];
            int depDelta = (Integer) row[7];
            String target = (String) row[8];
            String initialBlock = (String) row[9];
            Double progress = (Double) row[10];
            Double speed = (Double) row[11];
            TrainStatus status = (TrainStatus) row[12];
            String waitingReason = (String) row[13];

            Train train = new Train(
                num, name, type, src, dest, prio,
                base.plusMinutes(arrDelta),
                base.plusMinutes(depDelta),
                src, target
            );
            train.setCurrentBlockId(initialBlock);
            train.setProgressPercent(progress);
            train.setSpeedKmH(speed);
            train.setStatus(status);
            train.setWaitingReason(waitingReason);
            if (status == TrainStatus.WAITING) {
                train.setSignalAspect(SignalAspect.RED);
            } else if (status == TrainStatus.AT_PLATFORM) {
                train.setSignalAspect(SignalAspect.GREEN);
            }

            boolean isUpDest = "CCG".equalsIgnoreCase(dest) || "MMCT".equalsIgnoreCase(dest)
                    || "JNPT".equalsIgnoreCase(dest) || "BDTS".equalsIgnoreCase(dest);
            String direction = isUpDest ? "UP" : "DOWN";
            train.setDirection(direction);
            TrainCategory cat = train.getTrainCategory();
            train.setTrainCategory(cat);

            List<String> defaultStops = StoppingPatternConfig.generateDefaultStopStations(cat, src, dest, direction);
            List<String> validStops = StoppingPatternConfig.validateAndFilterStops(defaultStops, cat, direction, num, platformChecker);
            train.setStopStations(validStops);

            com.railway.simulator.algorithm.RouteProgress routeProgress = corridorGraph.computeRouteProgress(train);
            train.setLastStationCode(routeProgress.getLastStationCode());
            train.setUpcomingStationCode(routeProgress.getUpcomingStationCode());
            train.setNextStopStationCode(routeProgress.getNextStopStationCode());
            if (train.getStatus() != TrainStatus.AT_PLATFORM && (initialBlock == null || !initialBlock.matches(".*-P\\d+"))) {
                train.setTargetStationCode(routeProgress.getNextStopStationCode());
            }

            if (initialBlock != null && initialBlock.matches(".*-P\\d+")) {
                int pIdx = initialBlock.lastIndexOf("-P");
                String stn = initialBlock.substring(4, pIdx);
                int pNum = Integer.parseInt(initialBlock.substring(pIdx + 2));
                train.setAssignedPlatformNumber(pNum);
                platformRepository.findByStationCodeAndPlatformNumber(stn, pNum).ifPresent(p -> {
                    train.setAssignedPlatformId(p.getPlatformId());
                    if (status == TrainStatus.AT_PLATFORM) {
                        p.setIsOccupied(true);
                        p.setCurrentTrainId(train.getTrainId());
                        p.setCurrentTrainNumber(train.getTrainNumber());
                        platformRepository.save(p);
                    }
                });
            }

            trainRepository.save(train);
        }
    }
}

