package com.railway.simulator.config;

import com.railway.simulator.domain.LineType;
import com.railway.simulator.domain.SignalAspect;
import com.railway.simulator.domain.TrackBlock;
import com.railway.simulator.domain.Train;
import com.railway.simulator.domain.TrainType;

/**
 * Authoritative speed limits and physical dynamics configuration.
 * Editable constants define aspect-based speed caps, train-type operational ceilings,
 * and standard acceleration/braking rates for smooth continuous motion.
 */
public class SignalSpeedConfig {

    // Aspect speed limits (km/h)
    public static final double SPEED_RED = 0.0;
    public static final double SPEED_YELLOW = 15.0;            // max 15 km/h (range 10-15)
    public static final double SPEED_DOUBLE_YELLOW = 50.0;     // max 50 km/h (range 30-50)
    public static final double MAX_CORRIDOR_SPEED = 110.0;     // never above 110 km/h

    // Train maximum operating speeds (simulation assumption in km/h)
    public static final double SPEED_SLOW_LOCAL = 80.0;
    public static final double SPEED_FAST_LOCAL = 100.0;
    public static final double SPEED_EXPRESS_MAIL = 110.0;
    public static final double SPEED_SUPERFAST = 110.0;
    public static final double SPEED_PREMIUM = 110.0;
    public static final double SPEED_FREIGHT = 75.0;
    public static final double SPEED_SPECIAL = 80.0;

    // Physical acceleration & deceleration rates (m/s^2)
    public static final double ACCELERATION_M_S2 = 0.5;        // ~0.5 m/s^2
    public static final double BRAKING_M_S2 = 0.8;             // ~0.8 m/s^2

    // Rates in km/h per second (1 m/s = 3.6 km/h)
    public static final double ACCEL_KMH_PER_SEC = ACCELERATION_M_S2 * 3.6; // 1.8 km/h/s
    public static final double BRAKE_KMH_PER_SEC = BRAKING_M_S2 * 3.6;      // 2.88 km/h/s

    // Target stop position within block when facing RED ahead signal
    public static final double STOP_PROGRESS_PERCENT = 98.0;

    /**
     * Determines whether a train is categorized as a fast local service.
     */
    public static boolean isFastLocal(Train train, TrackBlock currentBlock) {
        if (train == null) {
            return false;
        }
        if (train.getTrainName() != null && train.getTrainName().toLowerCase().contains("fast")) {
            return true;
        }
        if (currentBlock != null) {
            LineType lt = currentBlock.getLineType();
            if (lt == LineType.FAST_UP || lt == LineType.FAST_DOWN) {
                return true;
            }
        }
        return false;
    }

    /**
     * Returns the maximum operating speed ceiling for a train based on its type and fast/slow classification.
     */
    public static double getMaxSpeed(TrainType type, boolean isFastLocal) {
        if (type == null) {
            return SPEED_SLOW_LOCAL;
        }
        double max;
        switch (type) {
            case LOCAL:
                max = isFastLocal ? SPEED_FAST_LOCAL : SPEED_SLOW_LOCAL;
                break;
            case FREIGHT:
                max = SPEED_FREIGHT;
                break;
            case SPECIAL:
                max = SPEED_SPECIAL;
                break;
            case EXPRESS_MAIL:
                max = SPEED_EXPRESS_MAIL;
                break;
            case SUPERFAST:
                max = SPEED_SUPERFAST;
                break;
            case PREMIUM:
                max = SPEED_PREMIUM;
                break;
            default:
                max = SPEED_SLOW_LOCAL;
        }
        return Math.min(max, MAX_CORRIDOR_SPEED);
    }

    /**
     * Overloaded helper taking Train and current TrackBlock.
     */
    public static double getMaxSpeed(Train train, TrackBlock currentBlock) {
        if (train == null) {
            return SPEED_SLOW_LOCAL;
        }
        return getMaxSpeed(train.getTrainType(), isFastLocal(train, currentBlock));
    }

    /**
     * Returns the target speed limit dictated by the signal aspect and the train's maximum speed ceiling.
     */
    public static double getAspectSpeedLimit(SignalAspect aspect, double trainMaxSpeed) {
        if (aspect == null) {
            return Math.min(trainMaxSpeed, MAX_CORRIDOR_SPEED);
        }
        switch (aspect) {
            case RED:
                return SPEED_RED;
            case YELLOW:
                return Math.min(SPEED_YELLOW, trainMaxSpeed);
            case DOUBLE_YELLOW:
                return Math.min(SPEED_DOUBLE_YELLOW, trainMaxSpeed);
            case GREEN:
            default:
                return Math.min(trainMaxSpeed, MAX_CORRIDOR_SPEED);
        }
    }
}
