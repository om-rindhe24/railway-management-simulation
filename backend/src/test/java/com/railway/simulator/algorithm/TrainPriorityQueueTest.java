package com.railway.simulator.algorithm;

import com.railway.simulator.domain.Train;
import com.railway.simulator.domain.TrainType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TrainPriorityQueueTest {

    private TrainPriorityQueue queue;
    private LocalDateTime baseTime;

    @BeforeEach
    void setUp() {
        queue = new TrainPriorityQueue();
        baseTime = LocalDateTime.of(2026, 9, 18, 10, 0);
    }

    @Test
    @DisplayName("Verify Min-Heap dequeues higher simulation priority before lower priority")
    void testMinHeapOrderingByPriority() {
        Train localTrain = new Train("LOC-901", "Simulation Local 01", TrainType.LOCAL,
                "VR", "CCG", 5, baseTime.plusMinutes(5), baseTime.plusMinutes(10), "BVI", "BVI");

        Train expressTrain = new Train("EXP-201", "Simulation Express 01", TrainType.EXPRESS_MAIL,
                "VR", "MMCT", 4, baseTime.plusMinutes(15), baseTime.plusMinutes(20), "BVI", "BVI");

        Train emergencyTrain = new Train("EMG-001", "Simulation Emergency Relief", TrainType.SPECIAL,
                "VR", "MMCT", 1, baseTime.plusMinutes(30), baseTime.plusMinutes(35), "BVI", "BVI");

        queue.offer(localTrain);
        queue.offer(expressTrain);
        queue.offer(emergencyTrain);

        assertEquals(3, queue.size());

        // Tier 1 (Emergency) must be dequeued first despite having later arrival time
        Train first = queue.poll();
        assertNotNull(first);
        assertEquals(TrainType.SPECIAL, first.getTrainType());
        assertEquals("EMG-001", first.getTrainNumber());

        // Tier 4 (Express) must be dequeued second
        Train second = queue.poll();
        assertNotNull(second);
        assertEquals(TrainType.EXPRESS_MAIL, second.getTrainType());

        // Tier 5 (Local) must be dequeued last
        Train third = queue.poll();
        assertNotNull(third);
        assertEquals(TrainType.LOCAL, third.getTrainType());

        assertTrue(queue.isEmpty());
    }

    @Test
    @DisplayName("Verify tie-breaker: same priority dequeues by earlier arrival time")
    void testTieBreakingByArrivalTime() {
        Train localEarly = new Train("LOC-101", "Early Local", TrainType.LOCAL,
                "VR", "CCG", 5, baseTime.plusMinutes(5), baseTime.plusMinutes(10), "BVI", "BVI");

        Train localLate = new Train("LOC-102", "Late Local", TrainType.LOCAL,
                "VR", "CCG", 5, baseTime.plusMinutes(25), baseTime.plusMinutes(30), "BVI", "BVI");

        queue.offer(localLate);
        queue.offer(localEarly);

        Train first = queue.poll();
        assertEquals("LOC-101", first.getTrainNumber(), "Earlier scheduled arrival must dequeue first on tie");

        Train second = queue.poll();
        assertEquals("LOC-102", second.getTrainNumber());
    }

    @Test
    @DisplayName("Verify snapshot list preservation without mutating heap")
    void testToOrderedListDoesNotMutateHeap() {
        queue.offer(new Train("T-1", "Train 1", TrainType.LOCAL, "VR", "CCG", 5, baseTime, baseTime.plusMinutes(5), "BVI", "BVI"));
        queue.offer(new Train("T-2", "Train 2", TrainType.PREMIUM, "VR", "CCG", 2, baseTime, baseTime.plusMinutes(5), "BVI", "BVI"));

        List<Train> ordered = queue.toOrderedList();
        assertEquals(2, ordered.size());
        assertEquals("T-2", ordered.get(0).getTrainNumber(), "Premium should be first in ordered snapshot");
        assertEquals(2, queue.size(), "Heap size must remain unchanged");
    }
}
