package com.railway.simulator.algorithm;

import com.railway.simulator.domain.Train;
import com.railway.simulator.domain.TrainType;

import java.time.LocalDateTime;

/**
 * Configurable Simulation Priority Policy with six configurable priority tiers.
 *
 * NOTE: This is a simulation model and decision policy for scheduling algorithms.
 * The example ordering may be used internally for demonstration, but must NEVER be
 * described as official Indian Railways priority.
 *
 * Default Simulation Policy:
 * Tier 1: Emergency / Special Service
 * Tier 2: Premium (Rajdhani/Shatabdi style simulated service)
 * Tier 3: Superfast Express
 * Tier 4: Express / Mail
 * Tier 5: Suburban Local
 * Tier 6: Freight / Goods
 */
public class PriorityPolicy {

    /**
     * Computes the numerical priority tier for scheduling.
     * Lower number = Higher priority in Min-Heap.
     */
    public static int getPriorityTier(TrainType type, Integer explicitPriority) {
        if (explicitPriority != null && explicitPriority >= 1 && explicitPriority <= 6) {
            return explicitPriority;
        }
        return switch (type) {
            case SPECIAL -> 1;
            case PREMIUM -> 2;
            case SUPERFAST -> 3;
            case EXPRESS_MAIL -> 4;
            case LOCAL -> 5;
            case FREIGHT -> 6;
        };
    }

    /**
     * Determines whether train A has strictly higher simulation priority than train B.
     */
    public static boolean hasHigherPriority(Train a, Train b) {
        int priorityA = getPriorityTier(a.getTrainType(), a.getSimulationPriority());
        int priorityB = getPriorityTier(b.getTrainType(), b.getSimulationPriority());

        if (priorityA != priorityB) {
            return priorityA < priorityB; // Lower tier number = higher priority
        }

        // Tie-breaker 1: Earlier effective arrival time
        LocalDateTime arrivalA = a.getActualArrival();
        LocalDateTime arrivalB = b.getActualArrival();
        if (!arrivalA.isEqual(arrivalB)) {
            return arrivalA.isBefore(arrivalB);
        }

        // Tie-breaker 2: Higher accumulated delay (fairness to minimize excessive individual passenger delay)
        return a.getDelayMinutes() > b.getDelayMinutes();
    }
}
