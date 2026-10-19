import { TrainSnapshot, Train } from '../types';

/**
 * Format train location string per specification:
 * - AT_PLATFORM: show "At <station>"
 * - In an inter-station block: show "Between <last> and <upcoming>"
 */
export function formatTrainLocation(
  train: TrainSnapshot | Train | null | undefined
): string {
  if (!train) return 'En Route';

  const isPlatform =
    train.status === 'AT_PLATFORM' ||
    (train.currentBlockId ? /.*-P\d+/.test(train.currentBlockId) : false);

  if (isPlatform) {
    const stn =
      train.lastStationCode ||
      train.currentStationCode ||
      (train.currentBlockId ? train.currentBlockId.split('-')[1] : '');
    return `At ${stn}`;
  }

  if (train.lastStationCode && train.upcomingStationCode) {
    return `Between ${train.lastStationCode} and ${train.upcomingStationCode}`;
  }

  if (train.lastStationCode) {
    return `At ${train.lastStationCode}`;
  }

  return train.currentStationCode ? `At ${train.currentStationCode}` : 'En Route';
}

/**
 * Format train next stop string per specification:
 * - Next Stop: nextStopStationCode, or "Non-stop" if null
 */
export function formatNextStop(
  train: TrainSnapshot | Train | null | undefined
): string {
  if (!train || !train.nextStopStationCode) {
    return 'Non-stop';
  }
  return train.nextStopStationCode;
}
