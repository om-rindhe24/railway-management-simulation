import {
  Station, Platform, Train, PlatformAllocation, AllocationHistory,
  ConflictReport, AllocationDecision, ReallocationResult, SimulationState, AlgorithmInfo,
  CorridorStateDto
} from '../types';

const API_BASE = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080/api';
const API_KEY = import.meta.env.VITE_API_KEY || 'railway-dev-key-2026';

async function fetchJson<T>(endpoint: string, options?: RequestInit): Promise<T> {
  const url = `${API_BASE}${endpoint}`;
  const response = await fetch(url, {
    headers: {
      'Content-Type': 'application/json',
      'X-API-KEY': API_KEY,
      ...options?.headers,
    },
    ...options,
  });

  if (!response.ok) {
    let errorMsg = `HTTP Error ${response.status}`;
    try {
      const errBody = await response.json();
      if (errBody && errBody.message) {
        errorMsg = errBody.message;
      }
    } catch {
      // ignore
    }
    throw new Error(errorMsg);
  }

  const json = await response.json();
  return json.data;
}

export const api = {
  // Stations
  getStations: () => fetchJson<Station[]>('/stations'),
  getStation: (code: string) => fetchJson<Station>(`/stations/${code}`),

  // Platforms
  getPlatforms: (stationCode?: string) =>
    fetchJson<Platform[]>(stationCode ? `/platforms?stationCode=${stationCode}` : '/platforms'),
  closePlatform: (platformId: number, reason: string) =>
    fetchJson<ReallocationResult>(`/platforms/${platformId}/close`, {
      method: 'POST',
      body: JSON.stringify({ reason }),
    }),
  openPlatform: (platformId: number) =>
    fetchJson<Platform>(`/platforms/${platformId}/open`, { method: 'POST' }),

  // Trains
  getTrains: (params?: { station?: string; type?: string; status?: string }) => {
    const q = new URLSearchParams();
    if (params?.station) q.append('station', params.station);
    if (params?.type) q.append('type', params.type);
    if (params?.status) q.append('status', params.status);
    const queryString = q.toString() ? `?${q.toString()}` : '';
    return fetchJson<Train[]>(`/trains${queryString}`);
  },
  injectDelay: (trainId: number, delayMinutes: number, reason: string) =>
    fetchJson<ReallocationResult>(`/trains/${trainId}/delay`, {
      method: 'PUT',
      body: JSON.stringify({ delayMinutes, reason }),
    }),
  createTrain: (train: Partial<Train>) =>
    fetchJson<Train>('/trains', {
      method: 'POST',
      body: JSON.stringify(train),
    }),
  cancelTrain: (trainId: number, reason?: string) =>
    fetchJson<Train>(`/trains/${trainId}?reason=${encodeURIComponent(reason || 'Manual cancellation')}`, {
      method: 'DELETE',
    }),

  // Allocations & Reallocation
  getActiveAllocations: (stationCode?: string) =>
    fetchJson<PlatformAllocation[]>(stationCode ? `/allocations?stationCode=${stationCode}` : '/allocations'),
  runAllocation: (stationCode: string) =>
    fetchJson<AllocationDecision[]>(`/allocations/run?stationCode=${stationCode}`, { method: 'POST' }),
  getConflicts: (stationCode: string) =>
    fetchJson<ConflictReport[]>(`/allocations/conflicts?stationCode=${stationCode}`),
  getAllocationHistory: (stationCode?: string) =>
    fetchJson<AllocationHistory[]>(stationCode ? `/history?stationCode=${stationCode}` : '/history'),

  // Simulation
  getSimulationState: () => fetchJson<SimulationState>('/simulation/state'),
  getCorridorState: () => fetchJson<CorridorStateDto>('/simulation/corridor-state'),
  startSimulation: () => fetchJson<string>('/simulation/start', { method: 'POST' }),
  pauseSimulation: () => fetchJson<string>('/simulation/pause', { method: 'POST' }),
  resetSimulation: () => fetchJson<string>('/simulation/reset', { method: 'POST' }),
  setSpeed: (speed: number) =>
    fetchJson<number>('/simulation/speed', {
      method: 'POST',
      body: JSON.stringify({ speed }),
    }),
  runDemoScenario: () => fetchJson<string>('/simulation/demo', { method: 'POST' }),

  // Analytics
  getAnalytics: () => fetchJson<Record<string, any>>('/analytics'),

  // Algorithms
  getAlgorithmMetadata: () =>
    fetchJson<{ algorithms: AlgorithmInfo[] }>('/algorithms/metadata').then(d => d.algorithms),
};
