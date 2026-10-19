package com.railway.simulator.algorithm;

import com.railway.simulator.domain.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class SignalStateEngineTest {

    private SignalStateEngine engine;
    private CorridorGraph corridorGraph;
    private Map<String, TrackBlock> blocks;
    private Map<String, Signal> signals;
    private Map<String, Platform> platforms;

    @BeforeEach
    void setUp() {
        engine = new SignalStateEngine();
        corridorGraph = new CorridorGraph();
        blocks = new LinkedHashMap<>();
        signals = new LinkedHashMap<>();
        platforms = new LinkedHashMap<>();
    }

    @Test
    @DisplayName("Block occupancy should set controlling signal to RED with train details")
    void testBlockOccupancySetsSignalRed() {
        TrackBlock block = new TrackBlock("BLK-VR-NSP-SU", "VR", "NSP", LineType.SLOW_UP, BlockType.INTER_STATION, "UP", 2.5);
        block.occupy(101L, "SIM-LOC-901");
        blocks.put(block.getBlockId(), block);

        Signal signal = new Signal("SIG-VR-NSP-SU", "VR", "Virar Departure", "UP", LineType.SLOW_UP, block.getBlockId());
        signals.put(signal.getSignalId(), signal);

        engine.evaluateSignal(signal, blocks, signals, platforms);

        assertEquals(SignalAspect.RED, signal.getCurrentAspect(), "Signal protecting occupied block must be RED");
        assertTrue(signal.getAspectReason().contains("OCCUPIED"), "Reason must mention occupied status");
        assertTrue(signal.getAspectReason().contains("SIM-LOC-901"), "Reason must name the occupying train");
    }

    @Test
    @DisplayName("Route with all downstream blocks clear should set signal to GREEN")
    void testDownstreamClearRouteSetsSignalGreen() {
        TrackBlock b1 = new TrackBlock("BLK-1", "VR", "NSP", LineType.SLOW_UP, BlockType.INTER_STATION, "UP", 2.5);
        TrackBlock b2 = new TrackBlock("BLK-2", "NSP", "BSR", LineType.SLOW_UP, BlockType.INTER_STATION, "UP", 2.5);
        TrackBlock b3 = new TrackBlock("BLK-3", "BSR", "NIG", LineType.SLOW_UP, BlockType.INTER_STATION, "UP", 2.5);

        b1.setNextBlockId("BLK-2");
        b2.setNextBlockId("BLK-3");

        blocks.put(b1.getBlockId(), b1);
        blocks.put(b2.getBlockId(), b2);
        blocks.put(b3.getBlockId(), b3);

        Signal s1 = new Signal("SIG-1", "VR", "Entrance 1", "UP", LineType.SLOW_UP, "BLK-1");
        Signal s2 = new Signal("SIG-2", "NSP", "Entrance 2", "UP", LineType.SLOW_UP, "BLK-2");
        Signal s3 = new Signal("SIG-3", "BSR", "Entrance 3", "UP", LineType.SLOW_UP, "BLK-3");

        s1.setNextSignalId("SIG-2");
        s2.setNextSignalId("SIG-3");

        signals.put(s1.getSignalId(), s1);
        signals.put(s2.getSignalId(), s2);
        signals.put(s3.getSignalId(), s3);

        engine.evaluateAllSignals(signals.values(), blocks, signals, platforms);

        assertEquals(SignalAspect.GREEN, s1.getCurrentAspect(), "Signal with all downstream clear must be GREEN");
        assertTrue(s1.getCurrentAspect().isPermissive(), "GREEN aspect must be permissive");
        assertTrue(s1.getAspectReason().contains("clear"), "Reason must confirm clear route");
    }

    @Test
    @DisplayName("Downstream block occupancy should trigger sequential YELLOW and DOUBLE_YELLOW caution aspects")
    void testSequentialCautionAspectsYellowAndDoubleYellow() {
        TrackBlock b1 = new TrackBlock("BLK-1", "VR", "NSP", LineType.FAST_UP, BlockType.INTER_STATION, "UP", 2.5);
        TrackBlock b2 = new TrackBlock("BLK-2", "NSP", "BSR", LineType.FAST_UP, BlockType.INTER_STATION, "UP", 2.5);
        TrackBlock b3 = new TrackBlock("BLK-3", "BSR", "BYR", LineType.FAST_UP, BlockType.INTER_STATION, "UP", 2.5);

        b1.setNextBlockId("BLK-2");
        b2.setNextBlockId("BLK-3");

        // BLK-3 is occupied by express train
        b3.occupy(202L, "SIM-EXP-101");

        blocks.put(b1.getBlockId(), b1);
        blocks.put(b2.getBlockId(), b2);
        blocks.put(b3.getBlockId(), b3);

        Signal s1 = new Signal("SIG-1", "VR", "Entrance 1", "UP", LineType.FAST_UP, "BLK-1");
        Signal s2 = new Signal("SIG-2", "NSP", "Entrance 2", "UP", LineType.FAST_UP, "BLK-2");
        Signal s3 = new Signal("SIG-3", "BSR", "Entrance 3", "UP", LineType.FAST_UP, "BLK-3");

        s1.setNextSignalId("SIG-2");
        s2.setNextSignalId("SIG-3");

        signals.put(s1.getSignalId(), s1);
        signals.put(s2.getSignalId(), s2);
        signals.put(s3.getSignalId(), s3);

        engine.evaluateAllSignals(signals.values(), blocks, signals, platforms);

        // Immediate signal protecting occupied block 3 must be RED
        assertEquals(SignalAspect.RED, s3.getCurrentAspect(), "Signal 3 must be RED");

        // Signal 2 (one block upstream) must be YELLOW (Caution)
        assertEquals(SignalAspect.YELLOW, s2.getCurrentAspect(), "Signal 2 must be YELLOW");
        assertTrue(s2.getAspectReason().contains("SIG-3"), "Reason must mention upcoming red signal");

        // Signal 1 (two blocks upstream) must be DOUBLE_YELLOW (Attention / Advanced Caution)
        assertEquals(SignalAspect.DOUBLE_YELLOW, s1.getCurrentAspect(), "Signal 1 must be DOUBLE_YELLOW");
        assertTrue(s1.getAspectReason().contains("YELLOW"), "Reason must mention downstream caution");
    }

    @Test
    @DisplayName("Train should halt at RED signal with accurate explainable waiting reason")
    void testTrainHaltsAtRedSignalWithAccurateReason() {
        // Setup platform 5 at Borivali
        TrackBlock pBlock = new TrackBlock("BLK-BVI-P5", "BVI", "BVI", LineType.FAST_UP, BlockType.PLATFORM, "UP", 0.6);
        pBlock.setClosed(true);
        blocks.put(pBlock.getBlockId(), pBlock);

        Signal homeSignal = new Signal("SIG-BVI-H5", "BVI", "Borivali Platform 5 Home", "UP", LineType.FAST_UP, pBlock.getBlockId());
        signals.put(homeSignal.getSignalId(), homeSignal);

        Station bvi = new Station("BVI", "Borivali", 8, true, true, true, true, true, 8);
        Platform p5 = new Platform(bvi, 5, LineType.FAST_UP, Set.of(TrainType.values()));
        p5.setIsClosed(true);
        platforms.put(pBlock.getBlockId(), p5);

        engine.evaluateSignal(homeSignal, blocks, signals, platforms);
        assertEquals(SignalAspect.RED, homeSignal.getCurrentAspect());

        // Create approaching train
        LocalDateTime now = LocalDateTime.of(2026, 9, 18, 10, 10);
        Train train = new Train("SIM-VB-20901", "Simulation Vande Bharat", TrainType.PREMIUM,
                "VR", "MMCT", 2, now.plusMinutes(2), now.plusMinutes(8), "DIC", "BVI");
        train.setAssignedPlatformNumber(5);

        // Simulate train checking controlling signal
        if (homeSignal.getCurrentAspect() == SignalAspect.RED) {
            train.setStatus(TrainStatus.WAITING);
            train.setNextSignalId(homeSignal.getSignalId());
            train.setSignalAspect(SignalAspect.RED);
            train.setWaitingReason(String.format("Waiting at signal %s: %s", homeSignal.getSignalId(), homeSignal.getAspectReason()));
        }

        assertEquals(TrainStatus.WAITING, train.getStatus(), "Train must halt with WAITING status");
        assertEquals(SignalAspect.RED, train.getSignalAspect(), "Train must record RED signal aspect");
        assertNotNull(train.getWaitingReason(), "Waiting reason must be populated");
        assertTrue(train.getWaitingReason().contains("SIG-BVI-H5"), "Reason must cite controlling signal");
        assertTrue(train.getWaitingReason().contains("CLOSED"), "Reason must explain platform closure");
        assertNotEquals("BLK-BVI-P5", train.getCurrentBlockId(), "Train must NOT enter the closed platform block");
    }

    @Test
    @DisplayName("Train should resume from WAITING status when signal becomes permissive")
    void testTrainResumesWhenSignalClears() {
        TrackBlock pBlock = new TrackBlock("BLK-BVI-P7", "BVI", "BVI", LineType.FAST_UP, BlockType.PLATFORM, "UP", 0.6);
        blocks.put(pBlock.getBlockId(), pBlock);

        Signal homeSignal = new Signal("SIG-BVI-H7", "BVI", "Borivali Platform 7 Home", "UP", LineType.FAST_UP, pBlock.getBlockId());
        signals.put(homeSignal.getSignalId(), homeSignal);

        Station bvi = new Station("BVI", "Borivali", 8, true, true, true, true, true, 8);
        Platform p7 = new Platform(bvi, 7, LineType.FAST_UP, Set.of(TrainType.values()));
        p7.setIsClosed(false);
        p7.setIsOccupied(false);
        platforms.put(pBlock.getBlockId(), p7);

        // Signal evaluates to GREEN
        engine.evaluateSignal(homeSignal, blocks, signals, platforms);
        assertEquals(SignalAspect.GREEN, homeSignal.getCurrentAspect());

        // Train previously waiting
        LocalDateTime now = LocalDateTime.of(2026, 9, 18, 10, 10);
        Train train = new Train("SIM-VB-20901", "Simulation Vande Bharat", TrainType.PREMIUM,
                "VR", "MMCT", 2, now, now.plusMinutes(8), "DIC", "BVI");
        train.setStatus(TrainStatus.WAITING);
        train.setSignalAspect(SignalAspect.RED);
        train.setWaitingReason("Previously waiting at signal");

        // Signal is permissive -> train resumes and enters platform
        if (homeSignal.getCurrentAspect().isPermissive()) {
            train.setStatus(TrainStatus.AT_PLATFORM);
            train.setCurrentBlockId(pBlock.getBlockId());
            train.setSignalAspect(homeSignal.getCurrentAspect());
            train.setWaitingReason(null);
            pBlock.occupy(train.getTrainId(), train.getTrainNumber());
        }

        assertEquals(TrainStatus.AT_PLATFORM, train.getStatus(), "Train must transition to AT_PLATFORM");
        assertNull(train.getWaitingReason(), "Waiting reason must be cleared upon resuming");
        assertEquals("BLK-BVI-P7", train.getCurrentBlockId(), "Train must occupy Platform 7 block");
        assertTrue(pBlock.isOccupied(), "Platform block must be marked occupied");
    }

    @Test
    @DisplayName("Signals must be topologically ordered and evaluated downstream to upstream")
    void testDownstreamToUpstreamSignalEvaluationOrder() {
        Signal s1 = new Signal("SIG-1", "VR", "Signal 1", "UP", LineType.FAST_UP, "BLK-1");
        Signal s2 = new Signal("SIG-2", "NSP", "Signal 2", "UP", LineType.FAST_UP, "BLK-2");
        Signal s3 = new Signal("SIG-3", "BSR", "Signal 3", "UP", LineType.FAST_UP, "BLK-3");

        s1.setNextSignalId("SIG-2");
        s2.setNextSignalId("SIG-3");
        s3.setNextSignalId(null);

        Map<String, Signal> sigMap = new LinkedHashMap<>();
        // Intentionally put them in upstream-to-downstream order
        sigMap.put(s1.getSignalId(), s1);
        sigMap.put(s2.getSignalId(), s2);
        sigMap.put(s3.getSignalId(), s3);

        List<Signal> ordered = engine.getDownstreamToUpstreamOrder(sigMap.values(), sigMap);

        assertEquals(3, ordered.size());
        int idx1 = ordered.indexOf(s1);
        int idx2 = ordered.indexOf(s2);
        int idx3 = ordered.indexOf(s3);

        assertTrue(idx3 < idx2, "Downstream signal S3 must be evaluated before upstream S2");
        assertTrue(idx2 < idx1, "Downstream signal S2 must be evaluated before upstream S1");
    }
}
