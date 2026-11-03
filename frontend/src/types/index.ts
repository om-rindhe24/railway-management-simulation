export type TrainType =
  | 'SPECIAL'
  | 'PREMIUM'
  | 'SUPERFAST'
  | 'EXPRESS_MAIL'
  | 'LOCAL'
  | 'FREIGHT';

export type LineType =
  | 'SLOW_UP'
  | 'SLOW_DOWN'
  | 'FAST_UP'
  | 'FAST_DOWN'
  | 'COMMON';

export type TrainStatus =
  | 'SCHEDULED'
  | 'APPROACHING'
  | 'ARRIVING'
  | 'AT_PLATFORM'
  | 'DEPARTED'
  | 'DELAYED'
  | 'WAITING'
  | 'WAITING AT STATION'
  | 'HELD'
  | 'CANCELLED'
  | 'REASSIGNED';

export type AllocationStatus =
  | 'ACTIVE'
  | 'COMPLETED'
  | 'CANCELLED'
  | 'REALLOCATED';

export type ChangeType =
  | 'INITIAL_ALLOCATION'
  | 'DYNAMIC_REALLOCATION'
  | 'EMERGENCY_OVERRIDE'
  | 'PLATFORM_CLOSURE';

export interface Station {
  stationId: number;
  stationCode: string;
  stationName: string;
  numberOfPlatforms: number;
  localTrainSupport: boolean;
  expressTrainSupport: boolean;
  fastLineSupport: boolean;
  slowLineSupport: boolean;
  terminalFlag: boolean;
  sequenceOrder: number;
  status: string;
  latitude?: number;
  longitude?: number;
}

export interface Platform {
  platformId: number;
  stationCode: string;
  platformNumber: number;
  lineType: LineType;
  supportedTrainTypes: TrainType[];
  isOccupied: boolean;
  isClosed: boolean;
  closureReason?: string;
  currentTrainId?: number;
  currentTrainNumber?: string;
  occupancyStart?: string;
  occupancyEnd?: string;
  direction?: string;
  dwellSecondsRemaining?: number;
  nextWaitingTrainNumber?: string;
  homeSignalId?: string;
  homeSignalAspect?: SignalAspect;
  starterSignalId?: string;
  starterSignalAspect?: SignalAspect;
}

export interface Train {
  direction?: string;
  progressPercent?: number;
  trainId: number;
  trainNumber: string;
  trainName: string;
  trainType: TrainType;
  trainCategory?: string;
  sourceStation: string;
  destinationStation: string;
  simulationPriority: number;
  scheduledArrival: string;
  scheduledDeparture: string;
  actualArrival: string;
  actualDeparture: string;
  delayMinutes: number;
  status: TrainStatus;
  currentStationCode: string;
  lastStationCode?: string;
  upcomingStationCode?: string;
  currentStationDisplay?: string;
  currentBlockId?: string;
  nextStationCode?: string;
  targetStationCode: string;
  stationCode?: string;
  nextStopStationCode?: string;
  stopStations?: string[];
  skipStations?: string[];
  assignedPlatformId?: number;
  assignedPlatformNumber?: number;
  isActive: boolean;
  isSimulated: boolean;
}

export interface PlatformAllocation {
  allocationId: number;
  trainId: number;
  trainNumber: string;
  trainName: string;
  platformId: number;
  platformNumber: number;
  stationId: number;
  stationCode: string;
  startTime: string;
  endTime: string;
  allocatedAt: string;
  status: AllocationStatus;
  selectionReason: string;
  scoringBreakdown: string;
  delayMinutes: number;
}

export interface AllocationHistory {
  historyId: number;
  trainNumber: string;
  trainName: string;
  stationCode: string;
  fromPlatformNumber?: number;
  toPlatformNumber: number;
  changeType: ChangeType;
  timestamp: string;
  reason: string;
  delayImpactMinutes: number;
  conflictsAvoided: number;
}

export interface ConflictReport {
  platformId: number;
  platformNumber: number;
  stationCode: string;
  incomingTrainId: number;
  incomingTrainNumber: string;
  existingTrainId: number;
  existingTrainNumber: string;
  overlapStart: string;
  overlapEnd: string;
  overlapDurationMinutes: number;
  description: string;
}

export interface PlatformScoreResult {
  platformId: number;
  platformNumber: number;
  feasible: boolean;
  totalScore: number;
  feasibilityReason: string;
  factorScores: Record<string, number>;
  summaryExplanation: string;
}

export interface AllocationDecision {
  trainId: number;
  trainNumber: string;
  trainName: string;
  selectedPlatformId?: number;
  selectedPlatformNumber?: number;
  allocated: boolean;
  primaryReason: string;
  detailedBreakdown: string;
  candidateScores: PlatformScoreResult[];
  estimatedDelayMinutes: number;
}

export interface ReallocationResult {
  totalAffectedTrains: number;
  reallocatedCount: number;
  waitingCount: number;
  additionalDelayMinutes: number;
  conflictsResolved: number;
  reallocationDecisions: AllocationDecision[];
  summaryMessage: string;
}

export interface WhatIfTrainDiff {
  trainId: number;
  trainNumber: string;
  trainName: string;
  beforePlatformNumber?: number;
  afterPlatformNumber?: number;
  beforeDelayMinutes: number;
  afterDelayMinutes: number;
  beforeStatus: string;
  afterStatus: string;
  decisionReason: string;
  isHeld: boolean;
  isReallocated: boolean;
}

export interface WhatIfPreviewResult {
  isolatedCopy: boolean;
  totalAffectedTrains: number;
  reallocatedCount: number;
  conflictsResolved: number;
  additionalDelayMinutes: number;
  heldCount: number;
  summaryMessage: string;
  reallocationResult: ReallocationResult;
  diffs: WhatIfTrainDiff[];
}

export interface SimulationState {
  simulationTime: string;
  running: boolean;
  speedMultiplier: number;
}

export interface AlgorithmInfo {
  name: string;
  dataStructure: string;
  purpose: string;
  timeComplexity: string;
  spaceComplexity: string;
  whereUsed: string;
  interviewKeyPoints: string[];
}

export type SignalAspect = 'RED' | 'YELLOW' | 'DOUBLE_YELLOW' | 'GREEN';
export type BlockType = 'INTER_STATION' | 'APPROACH' | 'PLATFORM';

export interface TrackBlock {
  blockId: string;
  fromStationCode: string;
  toStationCode: string;
  lineType: LineType;
  blockType: BlockType;
  occupied: boolean;
  occupyingTrainId?: number;
  occupyingTrainNumber?: string;
  closed: boolean;
  direction?: string;
  lengthKm?: number;
  nextBlockId?: string;
}

export interface Signal {
  signalId: string;
  protectedBlockId: string;
  stationCode: string;
  direction: string;
  lineType: LineType;
  aspect: SignalAspect;
  reason: string;
  nextSignalId?: string;
}

export interface TrainSnapshot {
  trainId: number;
  trainNumber: string;
  trainName: string;
  trainType: TrainType;
  trainCategory?: string;
  status: TrainStatus;
  sourceStation?: string;
  destinationStation?: string;
  currentStationCode?: string;
  lastStationCode?: string;
  upcomingStationCode?: string;
  currentStationDisplay?: string;
  targetStationCode?: string;
  stationCode?: string;
  nextStopStationCode?: string;
  stopStations?: string[];
  skipStations?: string[];
  currentBlockId?: string;
  nextSignalId?: string;
  signalAspect?: SignalAspect;
  waitingReason?: string;
  delayMinutes: number;
  assignedPlatform?: number;
  assignedPlatformId?: number;
  direction?: string;
  progressPercent?: number;
  speedKmH?: number;
  actualArrival?: string;
  actualDeparture?: string;
  scheduledArrival?: string;
  scheduledDeparture?: string;
}

export interface PlatformSnapshot {
  platformId: number;
  stationCode: string;
  platformNumber: number;
  lineType: LineType;
  occupied: boolean;
  closed: boolean;
  closureReason?: string;
  currentTrainId?: number;
  currentTrainNumber?: string;
  occupancyStart?: string;
  occupancyEnd?: string;
  direction?: string;
  dwellSecondsRemaining?: number;
  nextWaitingTrainNumber?: string;
  homeSignalId?: string;
  homeSignalAspect?: SignalAspect;
  starterSignalId?: string;
  starterSignalAspect?: SignalAspect;
}

export interface CorridorStateDto {
  simulationTime: string;
  running: boolean;
  speedMultiplier: number;
  trains: TrainSnapshot[];
  trackBlocks: TrackBlock[];
  signals: Signal[];
  platforms: PlatformSnapshot[];
  activeConflicts: ConflictReport[];
}

