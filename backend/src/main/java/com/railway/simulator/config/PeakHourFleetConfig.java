package com.railway.simulator.config;

import com.railway.simulator.domain.TrainCategory;
import com.railway.simulator.domain.TrainType;

import java.util.ArrayList;
import java.util.List;

/**
 * Defines the Peak Hour additional fleet (28 realistic trains).
 * Staggered across unoccupied blocks, adhering to direction, line type, and headway rules.
 */
public class PeakHourFleetConfig {

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

        // UP SLOW LOCALS (Stops at all stations, VR -> CCG)
        specs.add(new PeakTrainSpec("SIM-PK-931", "Peak Slow Up 931", TrainType.LOCAL, TrainCategory.SLOW_LOCAL, "UP", "VR", "CCG", "BLK-NIG-BYR-SU", 40.0, 50.0));
        specs.add(new PeakTrainSpec("SIM-PK-932", "Peak Slow Up 932", TrainType.LOCAL, TrainCategory.SLOW_LOCAL, "UP", "VR", "CCG", "BLK-DIC-BVI-SU", 40.0, 50.0));
        specs.add(new PeakTrainSpec("SIM-PK-933", "Peak Slow Up 933", TrainType.LOCAL, TrainCategory.SLOW_LOCAL, "UP", "VR", "CCG", "BLK-KND-MLD-SU", 40.0, 50.0));
        specs.add(new PeakTrainSpec("SIM-PK-934", "Peak Slow Up 934", TrainType.LOCAL, TrainCategory.SLOW_LOCAL, "UP", "VR", "CCG", "BLK-GMN-JOS-SU", 40.0, 50.0));
        specs.add(new PeakTrainSpec("SIM-PK-935", "Peak Slow Up 935", TrainType.LOCAL, TrainCategory.SLOW_LOCAL, "UP", "VR", "CCG", "BLK-AND-VLP-SU", 40.0, 50.0));
        specs.add(new PeakTrainSpec("SIM-PK-936", "Peak Slow Up 936", TrainType.LOCAL, TrainCategory.SLOW_LOCAL, "UP", "VR", "CCG", "BLK-STC-KHR-SU", 40.0, 50.0));
        specs.add(new PeakTrainSpec("SIM-PK-937", "Peak Slow Up 937", TrainType.LOCAL, TrainCategory.SLOW_LOCAL, "UP", "VR", "CCG", "BLK-BA-MM-SU", 40.0, 50.0));
        specs.add(new PeakTrainSpec("SIM-PK-938", "Peak Slow Up 938", TrainType.LOCAL, TrainCategory.SLOW_LOCAL, "UP", "VR", "CCG", "BLK-DDR-PR-SU", 40.0, 50.0));

        // UP FAST LOCALS & EXPRESS (Fast tracks, VR -> CCG/MMCT)
        specs.add(new PeakTrainSpec("SIM-PK-939", "Peak Fast Up 939", TrainType.LOCAL, TrainCategory.FAST_LOCAL, "UP", "VR", "CCG", "BLK-BSR-NIG-FU", 40.0, 65.0));
        specs.add(new PeakTrainSpec("SIM-PK-940", "Peak Fast Up 940", TrainType.LOCAL, TrainCategory.FAST_LOCAL, "UP", "VR", "CCG", "BLK-BYR-MIRA-FU", 40.0, 65.0));
        specs.add(new PeakTrainSpec("SIM-PK-941", "Peak Fast Up 941", TrainType.LOCAL, TrainCategory.FAST_LOCAL, "UP", "VR", "CCG", "BLK-BVI-KND-FU", 40.0, 65.0));
        specs.add(new PeakTrainSpec("SIM-PK-942", "Peak Fast Up 942", TrainType.LOCAL, TrainCategory.FAST_LOCAL, "UP", "VR", "CCG", "BLK-GMN-JOS-FU", 40.0, 65.0));
        specs.add(new PeakTrainSpec("SIM-PK-943", "Peak Fast Up 943", TrainType.LOCAL, TrainCategory.FAST_LOCAL, "UP", "VR", "CCG", "BLK-AND-VLP-FU", 40.0, 65.0));
        specs.add(new PeakTrainSpec("SIM-PK-944", "Peak Fast Up 944", TrainType.LOCAL, TrainCategory.FAST_LOCAL, "UP", "VR", "CCG", "BLK-STC-KHR-FU", 40.0, 65.0));
        specs.add(new PeakTrainSpec("SIM-PK-945", "Peak Superfast Up 945", TrainType.SUPERFAST, TrainCategory.SUPERFAST, "UP", "VR", "MMCT", "BLK-BA-MM-FU", 40.0, 75.0));

        // DOWN SLOW LOCALS (Stops at all stations, CCG -> VR)
        specs.add(new PeakTrainSpec("SIM-PK-946", "Peak Slow Down 946", TrainType.LOCAL, TrainCategory.SLOW_LOCAL, "DOWN", "CCG", "VR", "BLK-MX-LP-SD", 40.0, 50.0));
        specs.add(new PeakTrainSpec("SIM-PK-947", "Peak Slow Down 947", TrainType.LOCAL, TrainCategory.SLOW_LOCAL, "DOWN", "CCG", "VR", "BLK-PR-DDR-SD", 40.0, 50.0));
        specs.add(new PeakTrainSpec("SIM-PK-948", "Peak Slow Down 948", TrainType.LOCAL, TrainCategory.SLOW_LOCAL, "DOWN", "CCG", "VR", "BLK-MRU-MM-SD", 40.0, 50.0));
        specs.add(new PeakTrainSpec("SIM-PK-949", "Peak Slow Down 949", TrainType.LOCAL, TrainCategory.SLOW_LOCAL, "DOWN", "CCG", "VR", "BLK-KHR-STC-SD", 40.0, 50.0));
        specs.add(new PeakTrainSpec("SIM-PK-950", "Peak Slow Down 950", TrainType.LOCAL, TrainCategory.SLOW_LOCAL, "DOWN", "CCG", "VR", "BLK-VLP-AND-SD", 40.0, 50.0));
        specs.add(new PeakTrainSpec("SIM-PK-951", "Peak Slow Down 951", TrainType.LOCAL, TrainCategory.SLOW_LOCAL, "DOWN", "CCG", "VR", "BLK-JOS-GMN-SD", 40.0, 50.0));
        specs.add(new PeakTrainSpec("SIM-PK-952", "Peak Slow Down 952", TrainType.LOCAL, TrainCategory.SLOW_LOCAL, "DOWN", "CCG", "VR", "BLK-MLD-KND-SD", 40.0, 50.0));
        specs.add(new PeakTrainSpec("SIM-PK-953", "Peak Slow Down 953", TrainType.LOCAL, TrainCategory.SLOW_LOCAL, "DOWN", "CCG", "VR", "BLK-BVI-DIC-SD", 40.0, 50.0));

        // DOWN FAST LOCALS & EXPRESS (Fast tracks, CCG/MMCT -> VR)
        specs.add(new PeakTrainSpec("SIM-PK-954", "Peak Fast Down 954", TrainType.LOCAL, TrainCategory.FAST_LOCAL, "DOWN", "CCG", "VR", "BLK-MX-LP-FD", 40.0, 65.0));
        specs.add(new PeakTrainSpec("SIM-PK-955", "Peak Fast Down 955", TrainType.LOCAL, TrainCategory.FAST_LOCAL, "DOWN", "CCG", "VR", "BLK-MRU-MM-FD", 40.0, 65.0));
        specs.add(new PeakTrainSpec("SIM-PK-956", "Peak Fast Down 956", TrainType.LOCAL, TrainCategory.FAST_LOCAL, "DOWN", "CCG", "VR", "BLK-STC-VLP-FD", 40.0, 65.0));
        specs.add(new PeakTrainSpec("SIM-PK-957", "Peak Fast Down 957", TrainType.LOCAL, TrainCategory.FAST_LOCAL, "DOWN", "CCG", "VR", "BLK-JOS-GMN-FD", 40.0, 65.0));
        specs.add(new PeakTrainSpec("SIM-PK-958", "Peak Express Down 958", TrainType.EXPRESS_MAIL, TrainCategory.EXPRESS, "DOWN", "MMCT", "VR", "BLK-MLD-KND-FD", 40.0, 70.0));

        return specs;
    }
}
