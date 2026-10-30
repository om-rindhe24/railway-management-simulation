import { Platform, PlatformSnapshot, Train, TrainSnapshot } from '../types';

export type AnyPlatform = Platform | PlatformSnapshot;
export type AnyTrain = Train | TrainSnapshot;

/**
 * Computes the standard track block ID for a station platform berth.
 * Example: BLK-BYR-P1, BLK-VR-P6
 */
export function getPlatformBlockId(stationCode: string, platformNumber: number): string {
  return `BLK-${stationCode}-P${platformNumber}`;
}

/**
 * Enforces strict corridor direction compatibility:
 * - UP platform accepts only UP trains.
 * - DOWN platform accepts only DOWN trains.
 * - COMMON line accepts both UP and DOWN trains.
 */
export function isPlatformDirectionCompatible(
  platform: { direction?: string; lineType?: string },
  trainDirection?: string
): boolean {
  if (!trainDirection) return true;

  const rawDir =
    platform.direction ||
    (platform.lineType?.includes('UP') ? 'UP' : platform.lineType?.includes('DOWN') ? 'DOWN' : 'COMMON');

  if (!rawDir || rawDir === 'COMMON') return true;

  return rawDir.toUpperCase() === trainDirection.toUpperCase();
}

/**
 * Finds the docked berth occupant for a platform card.
 * Rule: Train status is AT_PLATFORM, currentBlockId equals BLK-<STATION>-P<n>
 * (or assignedPlatformId === platform.platformId), and direction is compatible.
 */
export function getPlatformBerthOccupant<T extends AnyTrain>(
  platform: { platformId: number; platformNumber: number; direction?: string; lineType?: string; currentTrainNumber?: string },
  stationCode: string,
  trains: T[]
): T | null {
  const expectedBlockId = getPlatformBlockId(stationCode, platform.platformNumber);

  // Unambiguous match: status AT_PLATFORM and (currentBlockId === expectedBlockId OR assignedPlatformId === platform.platformId)
  const matched = trains.find((t) => {
    if (t.status !== 'AT_PLATFORM') return false;
    const isBlockMatch = t.currentBlockId === expectedBlockId;
    const isIdMatch = t.assignedPlatformId != null && t.assignedPlatformId === platform.platformId;
    if (!isBlockMatch && !isIdMatch) return false;
    return isPlatformDirectionCompatible(platform, t.direction);
  });

  if (matched) return matched;

  // Fallback to platform.currentTrainNumber strictly verified at this platform's block
  if (platform.currentTrainNumber) {
    const fallbackTrain = trains.find(
      (t) =>
        t.trainNumber === platform.currentTrainNumber &&
        t.status === 'AT_PLATFORM' &&
        (t.currentBlockId === expectedBlockId || (t.assignedPlatformId != null && t.assignedPlatformId === platform.platformId)) &&
        isPlatformDirectionCompatible(platform, t.direction)
    );
    if (fallbackTrain) return fallbackTrain;
  }

  return null;
}

/**
 * Finds incoming/waiting trains assigned to this platform card.
 * Rule:
 * - assignedPlatformId === platform.platformId
 * - status in APPROACHING, WAITING, HELD
 * - target/upcoming/nextStop station is this station
 * - not the docked occupant
 * - direction compatible (UP platform never lists DOWN train and vice versa)
 * - sorted nearest first (WAITING/HELD first, then highest progress percentage)
 * - at most maxCount (default 2)
 */
export function getPlatformWaitingTrains<T extends AnyTrain>(
  platform: { platformId: number; platformNumber: number; direction?: string; lineType?: string },
  stationCode: string,
  trains: T[],
  occupantTrainNumber?: string | null,
  maxCount: number = 2
): T[] {
  const candidates = trains.filter((t) => {
    if (occupantTrainNumber && t.trainNumber === occupantTrainNumber) return false;

    // Status: APPROACHING, WAITING, HELD
    if (t.status !== 'APPROACHING' && t.status !== 'WAITING' && t.status !== 'HELD') {
      return false;
    }

    // Strict assignedPlatformId match
    if (t.assignedPlatformId == null || t.assignedPlatformId !== platform.platformId) {
      return false;
    }

    // Target or upcoming station must be this station
    const isTargetStation =
      (t as any).targetStationCode === stationCode ||
      (t as any).upcomingStationCode === stationCode ||
      (t as any).nextStopStationCode === stationCode;

    if (!isTargetStation) return false;

    // Strict direction preservation: UP platform never lists DOWN train and vice versa
    if (!isPlatformDirectionCompatible(platform, t.direction)) return false;

    return true;
  });

  // Sort nearest first:
  // WAITING/HELD are stopped right outside the station/signals (effective 100% block traversal)
  candidates.sort((a, b) => {
    const isAWaiting = a.status === 'WAITING' || a.status === 'HELD';
    const isBWaiting = b.status === 'WAITING' || b.status === 'HELD';
    if (isAWaiting && !isBWaiting) return -1;
    if (!isAWaiting && isBWaiting) return 1;

    const progA = a.progressPercent ?? 0;
    const progB = b.progressPercent ?? 0;
    return progB - progA;
  });

  return candidates.slice(0, maxCount);
}
