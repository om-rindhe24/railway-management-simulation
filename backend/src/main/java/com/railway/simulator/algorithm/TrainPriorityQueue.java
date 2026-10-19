package com.railway.simulator.algorithm;

import com.railway.simulator.domain.Train;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.PriorityQueue;

/**
 * Min-Heap Priority Queue for upcoming train arrivals and configurable simulation priority ordering.
 *
 * Data Structure:
 * Binary Min-Heap backed by java.util.PriorityQueue.
 *
 * Comparator Invariant:
 * 1. Primary Key: Simulation Priority Tier (Ascending: Tier 1 > Tier 2 > ... > Tier 6).
 * 2. Secondary Key: Effective Arrival Time (Actual Arrival, Ascending).
 * 3. Tertiary Key: Unique Train Number / ID.
 *
 * Complexity:
 * - Insert (Offer): O(log N)
 * - Extract-Min (Poll): O(log N)
 * - Peek: O(1)
 * - Space: O(N)
 */
public class TrainPriorityQueue {

    private final PriorityQueue<Train> heap;

    public static final Comparator<Train> TRAIN_SIMULATION_COMPARATOR = (t1, t2) -> {
        int tier1 = PriorityPolicy.getPriorityTier(t1.getTrainType(), t1.getSimulationPriority());
        int tier2 = PriorityPolicy.getPriorityTier(t2.getTrainType(), t2.getSimulationPriority());

        if (tier1 != tier2) {
            return Integer.compare(tier1, tier2);
        }

        int timeComparison = t1.getActualArrival().compareTo(t2.getActualArrival());
        if (timeComparison != 0) {
            return timeComparison;
        }

        return t1.getTrainNumber().compareTo(t2.getTrainNumber());
    };

    public TrainPriorityQueue() {
        this.heap = new PriorityQueue<>(TRAIN_SIMULATION_COMPARATOR);
    }

    public synchronized void offer(Train train) {
        if (train == null) return;
        heap.offer(train);
    }

    public synchronized Train poll() {
        return heap.poll();
    }

    public synchronized Train peek() {
        return heap.peek();
    }

    public synchronized int size() {
        return heap.size();
    }

    public synchronized boolean isEmpty() {
        return heap.isEmpty();
    }

    public synchronized void clear() {
        heap.clear();
    }

    /**
     * Updates an existing train's position in the heap when delay changes.
     * Removes and re-offers train to restore heap invariant: O(N + log N) = O(N).
     */
    public synchronized void update(Train train) {
        if (train == null) return;
        heap.removeIf(t -> t.getTrainNumber().equals(train.getTrainNumber()));
        heap.offer(train);
    }

    /**
     * Returns a snapshot of trains ordered by current priority rank without destroying heap.
     */
    public synchronized List<Train> toOrderedList() {
        PriorityQueue<Train> copy = new PriorityQueue<>(heap);
        List<Train> ordered = new ArrayList<>(copy.size());
        while (!copy.isEmpty()) {
            ordered.add(copy.poll());
        }
        return ordered;
    }
}
