package com.railway.simulator.config;

import com.railway.simulator.domain.TrainCategory;
import com.railway.simulator.domain.TrainType;

import java.util.ArrayList;
import java.util.List;

/**
 * Defines the Peak Hour additional fleet with a realistic mix of services:
 * - 24 Suburban Locals (12 Slow Locals, 12 Fast Locals across UP & DOWN)
 * - 6 Long-Distance Intercity Services (2 Express, 2 Superfast, 2 Premium across UP & DOWN on FAST lines)
 * Total: 30 additional realistic trains, staggered on unoccupied blocks.
 */
public class PeakHourFleetConfig {

    public static final int EXTRA_LOCAL_COUNT = 24;
    public static final int EXTRA_LONG_DISTANCE_COUNT = 6;
    public static final int EXTRA_EXPRESS_COUNT = 2;
    public static final int EXTRA_SUPERFAST_COUNT = 2;
    public static final int EXTRA_PREMIUM_COUNT = 2;
    public static final int TOTAL_PEAK_TRAINS = EXTRA_LOCAL_COUNT + EXTRA_LONG_DISTANCE_COUNT; // 30

    public static class PeakTrainSpec {
        public final String trainNumber;
        public final String trainName;
        public final TrainType trainType;
        public final TrainCategory category;
        public final String direction;
        public final String source;
        public final String destination;
        public final String startBlockId;
        public final double progress;
        public final double speed;

        public PeakTrainSpec(String trainNumber, String trainName, TrainType trainType,
                             TrainCategory category, String direction, String source,
                             String destination, String startBlockId, double progress, double speed) {
            this.trainNumber = trainNumber;
            this.trainName = trainName;
            this.trainType = trainType;
            this.category = category;
            this.direction = direction;
            this.source = source;
            this.destination = destination;
            this.startBlockId = startBlockId;
            this.progress = progress;
            this.speed = speed;
        }
    }

    public static List<PeakTrainSpec> getPeakHourTrainSpecs() {
        List<PeakTrainSpec> specs = new ArrayList<>();

        // ==========================================
        // UP DIRECTION (Virar -> Mumbai Central/CCG)
        // ==========================================

        // 1. UP FAST LINE (FU): 6 Fast Locals + 3 Long-Distance (1 Premium, 1 Express, 1 Superfast) = 9 Trains
        specs.add(new PeakTrainSpec("SIM-PK-931", "Peak Fast Up 931", TrainType.LOCAL, TrainCategory.FAST_LOCAL, "UP", "VR", "CCG", "BLK-BSR-NIG-FU", 40.0, 65.0));
        specs.add(new PeakTrainSpec("SIM-PK-932", "Peak Fast Up 932", TrainType.LOCAL, TrainCategory.FAST_LOCAL, "UP", "VR", "CCG", "BLK-BYR-MIRA-FU", 40.0, 65.0));
        specs.add(new PeakTrainSpec("SIM-PK-933", "Peak Vande Bharat Up 933", TrainType.PREMIUM, TrainCategory.PREMIUM, "UP", "VR", "MMCT", "BLK-BVI-KND-FU", 40.0, 85.0));
        specs.add(new PeakTrainSpec("SIM-PK-934", "Peak Fast Up 934", TrainType.LOCAL, TrainCategory.FAST_LOCAL, "UP", "VR", "CCG", "BLK-GMN-JOS-FU", 40.0, 65.0));
        specs.add(new PeakTrainSpec("SIM-PK-935", "Peak Saurashtra Janta Up 935", TrainType.EXPRESS_MAIL, TrainCategory.EXPRESS, "UP", "VR", "MMCT", "BLK-AND-VLP-FU", 40.0, 70.0));
        specs.add(new PeakTrainSpec("SIM-PK-936", "Peak Fast Up 936", TrainType.LOCAL, TrainCategory.FAST_LOCAL, "UP", "VR", "CCG", "BLK-STC-KHR-FU", 40.0, 65.0));
        specs.add(new PeakTrainSpec("SIM-PK-937", "Peak Gujarat Superfast Up 937", TrainType.SUPERFAST, TrainCategory.SUPERFAST, "UP", "VR", "MMCT", "BLK-BA-MM-FU", 40.0, 75.0));
        specs.add(new PeakTrainSpec("SIM-PK-938", "Peak Fast Up 938", TrainType.LOCAL, TrainCategory.FAST_LOCAL, "UP", "VR", "CCG", "BLK-DDR-PR-FU", 40.0, 65.0));
        specs.add(new PeakTrainSpec("SIM-PK-939", "Peak Fast Up 939", TrainType.LOCAL, TrainCategory.FAST_LOCAL, "UP", "VR", "CCG", "BLK-LP-MX-FU", 40.0, 65.0));

        // 2. UP SLOW LINE (SU): 6 Slow Locals = 6 Trains
        specs.add(new PeakTrainSpec("SIM-PK-940", "Peak Slow Up 940", TrainType.LOCAL, TrainCategory.SLOW_LOCAL, "UP", "VR", "CCG", "BLK-VR-NSP-SU", 40.0, 50.0));
        specs.add(new PeakTrainSpec("SIM-PK-941", "Peak Slow Up 941", TrainType.LOCAL, TrainCategory.SLOW_LOCAL, "UP", "VR", "CCG", "BLK-NIG-BYR-SU", 40.0, 50.0));
        specs.add(new PeakTrainSpec("SIM-PK-942", "Peak Slow Up 942", TrainType.LOCAL, TrainCategory.SLOW_LOCAL, "UP", "VR", "CCG", "BLK-DIC-BVI-SU", 40.0, 50.0));
        specs.add(new PeakTrainSpec("SIM-PK-943", "Peak Slow Up 943", TrainType.LOCAL, TrainCategory.SLOW_LOCAL, "UP", "VR", "CCG", "BLK-KND-MLD-SU", 40.0, 50.0));
        specs.add(new PeakTrainSpec("SIM-PK-944", "Peak Slow Up 944", TrainType.LOCAL, TrainCategory.SLOW_LOCAL, "UP", "VR", "CCG", "BLK-AND-VLP-SU", 40.0, 50.0));
        specs.add(new PeakTrainSpec("SIM-PK-945", "Peak Slow Up 945", TrainType.LOCAL, TrainCategory.SLOW_LOCAL, "UP", "VR", "CCG", "BLK-BA-MM-SU", 40.0, 50.0));

        // ============================================
        // DOWN DIRECTION (Mumbai Central/CCG -> Virar)
        // ============================================

        // 3. DOWN FAST LINE (FD): 6 Fast Locals + 3 Long-Distance (1 Premium, 1 Express, 1 Superfast) = 9 Trains
        specs.add(new PeakTrainSpec("SIM-PK-946", "Peak Fast Down 946", TrainType.LOCAL, TrainCategory.FAST_LOCAL, "DOWN", "CCG", "VR", "BLK-MX-LP-FD", 40.0, 65.0));
        specs.add(new PeakTrainSpec("SIM-PK-947", "Peak Tejas Express Down 947", TrainType.PREMIUM, TrainCategory.PREMIUM, "DOWN", "MMCT", "VR", "BLK-PR-DDR-FD", 40.0, 85.0));
        specs.add(new PeakTrainSpec("SIM-PK-948", "Peak Fast Down 948", TrainType.LOCAL, TrainCategory.FAST_LOCAL, "DOWN", "CCG", "VR", "BLK-MRU-MM-FD", 40.0, 65.0));
        specs.add(new PeakTrainSpec("SIM-PK-949", "Peak Flying Ranee Down 949", TrainType.EXPRESS_MAIL, TrainCategory.EXPRESS, "DOWN", "MMCT", "VR", "BLK-STC-VLP-FD", 40.0, 70.0));
        specs.add(new PeakTrainSpec("SIM-PK-950", "Peak Fast Down 950", TrainType.LOCAL, TrainCategory.FAST_LOCAL, "DOWN", "CCG", "VR", "BLK-JOS-GMN-FD", 40.0, 65.0));
        specs.add(new PeakTrainSpec("SIM-PK-951", "Peak Paschim Superfast Down 951", TrainType.SUPERFAST, TrainCategory.SUPERFAST, "DOWN", "MMCT", "VR", "BLK-MLD-KND-FD", 40.0, 75.0));
        specs.add(new PeakTrainSpec("SIM-PK-952", "Peak Fast Down 952", TrainType.LOCAL, TrainCategory.FAST_LOCAL, "DOWN", "CCG", "VR", "BLK-BVI-DIC-FD", 40.0, 65.0));
        specs.add(new PeakTrainSpec("SIM-PK-953", "Peak Fast Down 953", TrainType.LOCAL, TrainCategory.FAST_LOCAL, "DOWN", "CCG", "VR", "BLK-MIRA-BYR-FD", 40.0, 65.0));
        specs.add(new PeakTrainSpec("SIM-PK-954", "Peak Fast Down 954", TrainType.LOCAL, TrainCategory.FAST_LOCAL, "DOWN", "CCG", "VR", "BLK-BYR-NIG-FD", 40.0, 65.0));

        // 4. DOWN SLOW LINE (SD): 6 Slow Locals = 6 Trains
        specs.add(new PeakTrainSpec("SIM-PK-955", "Peak Slow Down 955", TrainType.LOCAL, TrainCategory.SLOW_LOCAL, "DOWN", "CCG", "VR", "BLK-MX-LP-SD", 40.0, 50.0));
        specs.add(new PeakTrainSpec("SIM-PK-956", "Peak Slow Down 956", TrainType.LOCAL, TrainCategory.SLOW_LOCAL, "DOWN", "CCG", "VR", "BLK-PR-DDR-SD", 40.0, 50.0));
        specs.add(new PeakTrainSpec("SIM-PK-957", "Peak Slow Down 957", TrainType.LOCAL, TrainCategory.SLOW_LOCAL, "DOWN", "CCG", "VR", "BLK-MRU-MM-SD", 40.0, 50.0));
        specs.add(new PeakTrainSpec("SIM-PK-958", "Peak Slow Down 958", TrainType.LOCAL, TrainCategory.SLOW_LOCAL, "DOWN", "CCG", "VR", "BLK-KHR-STC-SD", 40.0, 50.0));
        specs.add(new PeakTrainSpec("SIM-PK-959", "Peak Slow Down 959", TrainType.LOCAL, TrainCategory.SLOW_LOCAL, "DOWN", "CCG", "VR", "BLK-VLP-AND-SD", 40.0, 50.0));
        specs.add(new PeakTrainSpec("SIM-PK-960", "Peak Slow Down 960", TrainType.LOCAL, TrainCategory.SLOW_LOCAL, "DOWN", "CCG", "VR", "BLK-BYR-NIG-SD", 40.0, 50.0));

        return specs;
    }
}
