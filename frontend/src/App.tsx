import React, { useState, useEffect, useCallback } from 'react';
import { api } from './services/api';
import {
  Station,
  Platform,
  Train,
  PlatformAllocation,
  AllocationHistory,
  ConflictReport,
  SimulationState,
  ReallocationResult,
  CorridorStateDto,
  TrainSnapshot,
} from './types';
import { TopBar } from './components/TopBar';
import { Sidebar } from './components/Sidebar';
import { Dashboard } from './pages/Dashboard';
import { PlatformView } from './pages/PlatformView';
import { OccupancyTimeline } from './pages/OccupancyTimeline';
import { CorridorMap } from './pages/CorridorMap';
import { LiveTrainSpot } from './pages/LiveTrainSpot';
import { TrainSignalDrawer } from './components/TrainSignalDrawer';
import { WhatIfSimulator } from './pages/WhatIfSimulator';
import { TrainManagement } from './pages/TrainManagement';
import { AllocationHistoryPage } from './pages/AllocationHistoryPage';
import { AnalyticsPage } from './pages/AnalyticsPage';
import { DemoWalkthroughModal } from './components/DemoWalkthroughModal';
import { ScrollVideoHero } from './components/landing/ScrollVideoHero';

export const App: React.FC = () => {
  const isIntroSkippedByUrl = typeof window !== 'undefined' && new URLSearchParams(window.location.search).get('intro') === '0';
  const hasSeenIntroInSession = () => {
    try {
      return sessionStorage.getItem('railflow_intro_seen') === 'true';
    } catch {
      return false;
    }
  };

  const [showLanding, setShowLanding] = useState<boolean>(() => {
    if (isIntroSkippedByUrl) return false;
    const initialHash = typeof window !== 'undefined' ? window.location.hash.replace('#', '') : '';
    if (initialHash === 'landing') return true;
    return !hasSeenIntroInSession();
  });

  const [activeTab, setActiveTab] = useState<string>(() => {
    const initialHash = typeof window !== 'undefined' ? window.location.hash.replace('#', '') : '';
    return (initialHash && initialHash !== 'landing') ? initialHash : 'dashboard';
  });
  const [selectedStation, setSelectedStation] = useState<string>('BVI');
  const [isLightMode, setIsLightMode] = useState<boolean>(() => {
    return localStorage.getItem('railway_theme') === 'light';
  });
  const [isDemoModalOpen, setIsDemoModalOpen] = useState<boolean>(false);

  const handleEnterApp = useCallback(() => {
    try {
      sessionStorage.setItem('railflow_intro_seen', 'true');
    } catch {}
    setShowLanding(false);
    setActiveTab('dashboard');
    if (window.location.hash === '#landing') {
      window.location.hash = 'dashboard';
    }
  }, []);

  const handleTabChange = useCallback((tab: string) => {
    if (tab === 'landing') {
      setShowLanding(true);
      window.location.hash = 'landing';
    } else {
      setShowLanding(false);
      setActiveTab(tab);
      window.location.hash = tab;
    }
  }, []);

  useEffect(() => {
    const handleHash = () => {
      const hash = window.location.hash.replace('#', '');
      if (hash === 'landing') {
        setShowLanding(true);
      } else if (hash) {
        setShowLanding(false);
        setActiveTab(hash);
      }
    };
    window.addEventListener('hashchange', handleHash);
    return () => window.removeEventListener('hashchange', handleHash);
  }, []);

  // Core domain states
  const [simulationState, setSimulationState] = useState<SimulationState>({
    simulationTime: '2026-09-18T10:00:00',
    running: false,
    speedMultiplier: 1,
  });
  const [corridorState, setCorridorState] = useState<CorridorStateDto | null>(null);
  const [inspectingTrain, setInspectingTrain] = useState<TrainSnapshot | null>(null);
  const [stations, setStations] = useState<Station[]>([]);
  const [platforms, setPlatforms] = useState<Platform[]>([]);
  const [trains, setTrains] = useState<Train[]>([]);
  const [allocations, setAllocations] = useState<PlatformAllocation[]>([]);
  const [conflicts, setConflicts] = useState<ConflictReport[]>([]);
  const [history, setHistory] = useState<AllocationHistory[]>([]);
  const [kpis, setKpis] = useState<Record<string, any>>({});

  // Theme switcher
  const handleToggleTheme = () => {
    const nextTheme = !isLightMode;
    setIsLightMode(nextTheme);
    localStorage.setItem('railway_theme', nextTheme ? 'light' : 'dark');
  };

  // Fetch initial stations list
  useEffect(() => {
    api.getStations()
      .then((data) => setStations(data))
      .catch((err) => console.error('Failed to load stations:', err));
  }, []);

  // Main data synchronization function
  const refreshStationData = useCallback(async () => {
    try {
      const [platData, trainData, allocData, confData, histData, kpiData, simState, corrState] = await Promise.all([
        api.getPlatforms(selectedStation),
        api.getTrains(),
        api.getActiveAllocations(selectedStation),
        api.getConflicts(selectedStation),
        api.getAllocationHistory(selectedStation),
        api.getAnalytics(),
        api.getSimulationState(),
        api.getCorridorState(),
      ]);

      setPlatforms(platData);
      setTrains(trainData);
      setAllocations(allocData);
      setConflicts(confData);
      setHistory(histData);
      setKpis(kpiData);
      setSimulationState(simState);
      setCorridorState(corrState);

      // Keep inspecting train synchronized with authoritative snapshot by trainId
      if (corrState) {
        setInspectingTrain((prev) => {
          if (!prev) return null;
          const updated = corrState.trains.find((t) => t.trainId === prev.trainId);
          return updated || null;
        });
      }
    } catch (err) {
      console.warn('Backend sync error (service may be booting):', err);
    }
  }, [selectedStation]);

  // Initial and periodic refresh (1.5s authoritative refresh)
  useEffect(() => {
    refreshStationData();
    const interval = setInterval(refreshStationData, 1500);
    return () => clearInterval(interval);
  }, [refreshStationData]);

  // Simulation Controls
  const handleStart = async () => {
    await api.startSimulation();
    await refreshStationData();
  };

  const handlePause = async () => {
    await api.pauseSimulation();
    await refreshStationData();
  };

  const handleReset = async () => {
    await api.resetSimulation();
    await refreshStationData();
  };

  const handleSetSpeed = async (speed: number) => {
    await api.setSpeed(speed);
    await refreshStationData();
  };

  // Operational Actions
  const handleRunAllocation = async () => {
    await api.runAllocation(selectedStation);
    await refreshStationData();
  };

  const handleClosePlatform = async (platformId: number, reason: string): Promise<ReallocationResult> => {
    const result = await api.closePlatform(platformId, reason);
    await refreshStationData();
    return result;
  };

  const handleOpenPlatform = async (platformId: number) => {
    await api.openPlatform(platformId);
    await refreshStationData();
  };

  const handleInjectDelay = async (trainId: number, delayMinutes: number, reason: string): Promise<ReallocationResult> => {
    const result = await api.injectDelay(trainId, delayMinutes, reason);
    await refreshStationData();
    return result;
  };

  const handleSampleDelay = async () => {
    const stationTrains = trains.filter((t) => t.targetStationCode === selectedStation);
    const target = stationTrains.find((t) => t.trainType === 'SUPERFAST' || t.trainType === 'EXPRESS_MAIL') || stationTrains[0];
    if (target) {
      await handleInjectDelay(target.trainId, 15, 'Sample operational hold between Dahisar and Borivali');
    }
  };

  const handleCancelTrain = async (trainId: number, reason: string) => {
    await api.cancelTrain(trainId, reason);
    await refreshStationData();
  };

  const handleCreateTrain = async (train: Partial<Train>) => {
    await api.createTrain(train);
    await refreshStationData();
  };

  if (showLanding) {
    return (
      <div className={`app-root ${isLightMode ? 'light-theme' : ''}`}>
        <ScrollVideoHero
          onEnterApp={handleEnterApp}
          stationsCount={stations.length}
          platformsCount={platforms.length}
          trainsCount={trains.length}
        />
      </div>
    );
  }

  return (
    <div className={`app-layout ${isLightMode ? 'light-theme' : ''}`}>
      {/* Sidebar Navigation */}
      <Sidebar activeTab={showLanding ? 'landing' : activeTab} onTabChange={handleTabChange} />

      {/* Main View Area */}
      <div className={`main-content ${activeTab === 'train-spot' ? 'train-spot-view' : ''}`}>
        <TopBar
          simulationState={simulationState}
          selectedStation={selectedStation}
          onSelectStation={setSelectedStation}
          onStart={handleStart}
          onPause={handlePause}
          onReset={handleReset}
          onSetSpeed={handleSetSpeed}
          onRunDemo={() => setIsDemoModalOpen(true)}
          isLightMode={isLightMode}
          onToggleTheme={handleToggleTheme}
        />

        {activeTab === 'dashboard' && (
          <Dashboard
            selectedStation={selectedStation}
            trains={trains}
            platforms={platforms}
            allocations={allocations}
            conflicts={conflicts}
            kpis={kpis}
            onRunAllocation={handleRunAllocation}
            onNavigateTab={setActiveTab}
            onInjectSampleDelay={handleSampleDelay}
          />
        )}

        {activeTab === 'train-spot' && (
          <LiveTrainSpot
            corridorState={corridorState}
            stations={stations}
            onSelectTrain={(train) => setInspectingTrain(train)}
            onSelectStation={setSelectedStation}
            selectedStation={selectedStation}
            inspectingTrain={inspectingTrain}
          />
        )}

        {activeTab === 'platforms' && (
          <PlatformView
            selectedStation={selectedStation}
            platforms={platforms}
            trains={trains}
            onClosePlatform={handleClosePlatform}
            onOpenPlatform={handleOpenPlatform}
          />
        )}

        {activeTab === 'timeline' && (
          <OccupancyTimeline
            selectedStation={selectedStation}
            platforms={platforms}
            trains={trains}
            allocations={allocations}
            simulationTime={simulationState.simulationTime}
          />
        )}

        {activeTab === 'map' && (
          <CorridorMap
            stations={stations}
            trains={trains}
            selectedStation={selectedStation}
            onSelectStation={setSelectedStation}
            corridorState={corridorState}
            onSelectTrain={(train) => setInspectingTrain(train)}
          />
        )}

        {activeTab === 'simulator' && (
          <WhatIfSimulator
            selectedStation={selectedStation}
            platforms={platforms}
            trains={trains}
            onInjectDelay={handleInjectDelay}
            onClosePlatform={handleClosePlatform}
            onResetSimulation={handleReset}
          />
        )}

        {activeTab === 'trains' && (
          <TrainManagement
            trains={trains}
            onInjectDelay={handleInjectDelay}
            onCancelTrain={handleCancelTrain}
            onCreateTrain={handleCreateTrain}
          />
        )}

        {activeTab === 'history' && (
          <AllocationHistoryPage history={history} selectedStation={selectedStation} />
        )}

        {activeTab === 'analytics' && <AnalyticsPage kpis={kpis} onRefresh={refreshStationData} />}
      </div>

      {/* Shared Unified Train & Signalling Drawer */}
      <TrainSignalDrawer
        train={inspectingTrain}
        signals={corridorState?.signals || []}
        trackBlocks={corridorState?.trackBlocks || []}
        onClose={() => setInspectingTrain(null)}
      />

      {/* 1-Click Demo Walkthrough Presentation Modal */}
      <DemoWalkthroughModal
        isOpen={isDemoModalOpen}
        onClose={() => setIsDemoModalOpen(false)}
        onRunInitialAllocation={handleRunAllocation}
        onInjectDelay={handleSampleDelay}
        onReset={handleReset}
        onNavigateTab={setActiveTab}
      />
    </div>
  );
};

export default App;
