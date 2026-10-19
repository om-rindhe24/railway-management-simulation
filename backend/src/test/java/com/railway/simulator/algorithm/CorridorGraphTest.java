package com.railway.simulator.algorithm;

import com.railway.simulator.domain.LineType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class CorridorGraphTest {

    private CorridorGraph graph;

    @BeforeEach
    void setUp() {
        graph = new CorridorGraph();
    }

    @Test
    @DisplayName("Corridor should contain all 24 stations from Virar to Mumbai Central")
    void testCorridorContains24Stations() {
        assertEquals(24, graph.getAllStations().size());
        assertNotNull(graph.getStation("VR"), "Virar station must exist");
        assertNotNull(graph.getStation("BVI"), "Borivali station must exist");
        assertNotNull(graph.getStation("AND"), "Andheri station must exist");
        assertNotNull(graph.getStation("DDR"), "Dadar station must exist");
        assertNotNull(graph.getStation("MMCT"), "Mumbai Central station must exist");

        // Verify start and end terminal markers
        assertTrue(graph.getStation("VR").isTerminal());
        assertTrue(graph.getStation("MMCT").isTerminal());
    }

    @Test
    @DisplayName("Sequential station route should be topologically connected along corridor")
    void testValidRouteConnectivity() {
        List<String> validSouthboundSuburban = List.of("VR", "NSP", "BSR", "NIG", "BYR");
        assertTrue(graph.isValidRoute(validSouthboundSuburban, LineType.SLOW_UP),
                "Slow line southbound route must be connected");

        List<String> invalidDiscontinuousRoute = List.of("VR", "BVI", "MMCT");
        assertFalse(graph.isValidRoute(invalidDiscontinuousRoute, LineType.SLOW_UP),
                "Discontinuous jump route without intermediate edges should be invalid for direct track traversal");
    }

    @Test
    @DisplayName("Transit time between stations should be calculated proportionally")
    void testTransitTimeCalculation() {
        int transitTimeSlow = graph.calculateTransitMinutes("VR", "BVI", LineType.SLOW_UP);
        int transitTimeFast = graph.calculateTransitMinutes("VR", "BVI", LineType.FAST_UP);

        assertTrue(transitTimeSlow > 0);
        assertTrue(transitTimeFast > 0);
        assertTrue(transitTimeFast <= transitTimeSlow, "Fast line transit time should be faster than slow line");
    }

    @Test
    @DisplayName("Walk nextBlockId from end to end for each of the 4 lines and never hit a null early")
    void testWalkNextBlockFromVRToMMCT() {
        Map<String, com.railway.simulator.domain.TrackBlock> blocks = graph.createCorridorTrackBlocks();

        // 1. Slow UP line (VR -> MMCT)
        com.railway.simulator.domain.TrackBlock slowCurrent = blocks.get("BLK-VR-NSP-SU");
        assertNotNull(slowCurrent, "Starting block BLK-VR-NSP-SU must exist");
        int slowSteps = 1;
        while (slowCurrent.getNextBlockId() != null) {
            assertEquals(LineType.SLOW_UP, slowCurrent.getLineType(), "Must stay on SLOW_UP: " + slowCurrent.getBlockId());
            slowCurrent = blocks.get(slowCurrent.getNextBlockId());
            assertNotNull(slowCurrent);
            slowSteps++;
        }
        assertEquals(23, slowSteps, "Slow line chain must have 23 blocks from VR to MMCT");
        assertEquals("MMCT", slowCurrent.getToStationCode(), "Slow line chain must terminate at MMCT");
        assertNull(slowCurrent.getNextBlockId(), "Terminal block at MMCT must have null nextBlockId");

        // 2. Fast UP line (VR -> MMCT)
        com.railway.simulator.domain.TrackBlock fastCurrent = blocks.get("BLK-VR-NSP-FU");
        assertNotNull(fastCurrent, "Starting block BLK-VR-NSP-FU must exist");
        int fastSteps = 1;
        while (fastCurrent.getNextBlockId() != null) {
            assertEquals(LineType.FAST_UP, fastCurrent.getLineType(), "Must stay on FAST_UP: " + fastCurrent.getBlockId());
            fastCurrent = blocks.get(fastCurrent.getNextBlockId());
            assertNotNull(fastCurrent);
            fastSteps++;
        }
        assertEquals(23, fastSteps, "Fast UP chain must have 23 blocks from VR to MMCT");
        assertEquals("MMCT", fastCurrent.getToStationCode(), "Fast UP chain must terminate at MMCT");
        assertNull(fastCurrent.getNextBlockId(), "Terminal block at MMCT must have null nextBlockId");

        // 3. Fast DOWN line (MMCT -> VR)
        com.railway.simulator.domain.TrackBlock fastDnCurrent = blocks.get("BLK-MMCT-MX-FD");
        assertNotNull(fastDnCurrent, "Starting block BLK-MMCT-MX-FD must exist");
        int fastDnSteps = 1;
        while (fastDnCurrent.getNextBlockId() != null) {
            assertEquals(LineType.FAST_DOWN, fastDnCurrent.getLineType(), "Must stay on FAST_DOWN: " + fastDnCurrent.getBlockId());
            fastDnCurrent = blocks.get(fastDnCurrent.getNextBlockId());
            assertNotNull(fastDnCurrent);
            fastDnSteps++;
        }
        assertEquals(23, fastDnSteps, "Fast DOWN chain must have 23 blocks from MMCT to VR");
        assertEquals("VR", fastDnCurrent.getToStationCode(), "Fast DOWN chain must terminate at VR");
        assertNull(fastDnCurrent.getNextBlockId(), "Terminal block at VR must have null nextBlockId");

        // 4. Slow DOWN line (MMCT -> VR)
        com.railway.simulator.domain.TrackBlock slowDnCurrent = blocks.get("BLK-MMCT-MX-SD");
        assertNotNull(slowDnCurrent, "Starting block BLK-MMCT-MX-SD must exist");
        int slowDnSteps = 1;
        while (slowDnCurrent.getNextBlockId() != null) {
            assertEquals(LineType.SLOW_DOWN, slowDnCurrent.getLineType(), "Must stay on SLOW_DOWN: " + slowDnCurrent.getBlockId());
            slowDnCurrent = blocks.get(slowDnCurrent.getNextBlockId());
            assertNotNull(slowDnCurrent);
            slowDnSteps++;
        }
        assertEquals(23, slowDnSteps, "Slow DOWN chain must have 23 blocks from MMCT to VR");
        assertEquals("VR", slowDnCurrent.getToStationCode(), "Slow DOWN chain must terminate at VR");
        assertNull(slowDnCurrent.getNextBlockId(), "Terminal block at VR must have null nextBlockId");
    }
}
