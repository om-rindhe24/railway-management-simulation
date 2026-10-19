import React, { useState, useEffect, useRef } from 'react';
import { Station, Train, CorridorStateDto, TrainSnapshot, Signal, TrackBlock, PlatformSnapshot } from '../types';
import { api } from '../services/api';
import { TrainSignalDrawer } from '../components/TrainSignalDrawer';
import { formatTrainLocation, formatNextStop } from '../utils/trainFormatters';
import {
  Train as TrainIcon,
  Navigation,
  AlertTriangle,
  Clock,
  Layers,
  Search,
  Zap,
  Activity,
  Maximize2,
  ChevronDown,
  ChevronUp,
  X,
  Shield,
  Gauge,
  CheckCircle2,
} from 'lucide-react';

/**
 * 4-Aspect Colour Light Signal Component
 */
export const SignalHead: React.FC<{ signal?: Signal; onClick?: () => void }> = ({ signal, onClick }) => {
  if (!signal) return null;

  const aspect = signal.aspect || 'GREEN';
  const isRed = aspect === 'RED';
  const isYellow = aspect === 'YELLOW';
  const isDoubleYellow = aspect === 'DOUBLE_YELLOW';
  const isGreen = aspect === 'GREEN';

  const lampY1 = isYellow || isDoubleYellow;
  const lampGreen = isGreen;
  const lampY2 = isDoubleYellow;
  const lampRed = isRed;

  const getAspectColor = () => {
    if (isRed) return '#ef4444';
    if (isYellow || isDoubleYellow) return '#eab308';
    return '#10b981';
  };

  return (
    <div
      style={{
        position: 'relative',
        display: 'inline-flex',
        flexDirection: 'column',
        alignItems: 'center',
        cursor: 'pointer',
      }}
      title={`Signal ${signal.signalId}\nAspect: ${signal.aspect}\nProtected: ${signal.protectedBlockId}\nReason: ${signal.reason}`}
      onClick={onClick}
    >
      <div
        style={{
          background: '#090d16',
          border: `1.5px solid ${getAspectColor()}`,
          borderRadius: '12px',
          padding: '4px 3px',
          display: 'flex',
          flexDirection: 'column',
          gap: '3px',
          boxShadow: `0 0 10px ${isRed ? 'rgba(239,68,68,0.45)' : isGreen ? 'rgba(16,185,129,0.35)' : 'rgba(234,179,8,0.35)'}`,
        }}
      >
        {/* Lamp 1: Yellow 1 */}
        <div
          style={{
            width: '8px',
            height: '8px',
            borderRadius: '50%',
            background: lampY1 ? '#eab308' : '#27272a',
            boxShadow: lampY1 ? '0 0 8px #eab308' : 'none',
          }}
        />
        {/* Lamp 2: Green */}
        <div
          style={{
            width: '8px',
            height: '8px',
            borderRadius: '50%',
            background: lampGreen ? '#10b981' : '#27272a',
            boxShadow: lampGreen ? '0 0 8px #10b981' : 'none',
          }}
        />
        {/* Lamp 3: Yellow 2 (Double Yellow) */}
        <div
          style={{
            width: '8px',
            height: '8px',
            borderRadius: '50%',
            background: lampY2 ? '#eab308' : '#27272a',
            boxShadow: lampY2 ? '0 0 8px #eab308' : 'none',
          }}
        />
        {/* Lamp 4: Red */}
        <div
          style={{
            width: '8px',
            height: '8px',
            borderRadius: '50%',
            background: lampRed ? '#ef4444' : '#27272a',
            boxShadow: lampRed ? '0 0 10px #ef4444' : 'none',
          }}
        />
      </div>
      <div style={{ width: '3px', height: '8px', background: '#475569' }} />
      <div style={{ width: '9px', height: '2px', background: '#64748b' }} />
    </div>
  );
};

interface CorridorMapProps {
  stations: Station[];
  trains: Train[];
  selectedStation: string;
  onSelectStation: (code: string) => void;
  corridorState?: CorridorStateDto | null;
  onSelectTrain?: (train: TrainSnapshot) => void;
}

export const CorridorMap: React.FC<CorridorMapProps> = ({
  stations,
  trains: propTrains,
  selectedStation,
  onSelectStation,
  corridorState: propCorridorState,
  onSelectTrain: propOnSelectTrain,
}) => {
  const [localCorridorState, setLocalCorridorState] = useState<CorridorStateDto | null>(null);
  const [loading, setLoading] = useState<boolean>(!propCorridorState);
  const [inspectingTrain, setInspectingTrain] = useState<TrainSnapshot | null>(null);
  const [selectedSignal, setSelectedSignal] = useState<Signal | null>(null);

  // Filters & Spatial Controls
  const [selectedSector, setSelectedSector] = useState<'ALL' | 'NORTH' | 'CENTRAL' | 'SOUTH'>('ALL');
  const [trackLineFilter, setTrackLineFilter] = useState<'ALL' | 'SLOW_UP' | 'FAST_UP' | 'SLOW_DN' | 'FAST_DN'>('ALL');
  const [trafficFilter, setTrafficFilter] = useState<'ALL' | 'ACTIVE_ONLY' | 'RED_SIGNALS'>('ALL');
  const [searchQuery, setSearchQuery] = useState<string>('');
  const [expandedStations, setExpandedStations] = useState<{ [code: string]: boolean }>({
    [selectedStation]: true,
  });

  const stationRefs = useRef<{ [code: string]: HTMLDivElement | null }>({});

  const toggleStationExpanded = (code: string) => {
    setExpandedStations((prev) => ({ ...prev, [code]: !prev[code] }));
  };

  const corridorState = propCorridorState || localCorridorState;

  // Authoritative polling fallback if parent does not provide
  useEffect(() => {
    if (propCorridorState) {
      setLoading(false);
      return;
    }
    let isMounted = true;
    let interval: ReturnType<typeof setInterval> | null = null;

    const fetchState = async () => {
      try {
        const data = await api.getCorridorState();
        if (isMounted) {
          setLocalCorridorState(data);
          setLoading(false);
        }
      } catch (err) {
        console.warn('Corridor state polling notice:', err);
      }
    };
    fetchState();
    interval = setInterval(fetchState, 1500);
    return () => {
      isMounted = false;
      if (interval) clearInterval(interval);
    };
  }, [propCorridorState]);

  // Keep inspected train synchronized with authoritative snapshot
  useEffect(() => {
    if (inspectingTrain && corridorState) {
      const updated = corridorState.trains.find((t) => t.trainId === inspectingTrain.trainId);
      if (updated) setInspectingTrain(updated);
    }
  }, [corridorState, inspectingTrain?.trainId]);

  const handleTrainClick = (train: TrainSnapshot) => {
    if (propOnSelectTrain) {
      propOnSelectTrain(train);
    } else {
      setInspectingTrain(train);
    }
  };

  // Telemetry Metrics
  const activeTrains = corridorState?.trains || [];
  const trackBlocks = corridorState?.trackBlocks || [];
  const signals = corridorState?.signals || [];
  const platforms = corridorState?.platforms || [];

  const waitingTrains = activeTrains.filter(
    (t) => t.status === 'WAITING' || (t.waitingReason && t.waitingReason.length > 0) || t.signalAspect === 'RED'
  );
  const redSignals = signals.filter((s) => s.aspect === 'RED');
  const occupiedBlocks = trackBlocks.filter((b) => b.occupied);
  const closedPlatforms = platforms.filter((p) => p.closed);

  const getTrainCategoryColor = (trainType: string): string => {
    switch (trainType) {
      case 'SPECIAL': return '#ef4444';
      case 'PREMIUM': return '#f59e0b';
      case 'SUPERFAST': return '#a855f7';
      case 'EXPRESS_MAIL': return '#0284c7';
      case 'LOCAL': return '#10b981';
      case 'FREIGHT': return '#64748b';
      default: return '#38bdf8';
    }
  };

  // Filter stations by Sector & Search
  const filteredStations = stations.filter((station) => {
    const seq = station.sequenceOrder;
    if (selectedSector === 'NORTH' && seq > 7) return false;
    if (selectedSector === 'CENTRAL' && (seq < 8 || seq > 17)) return false;
    if (selectedSector === 'SOUTH' && seq < 18) return false;

    if (searchQuery.trim().length > 0) {
      const q = searchQuery.toLowerCase();
      return (
        station.stationName.toLowerCase().includes(q) ||
        station.stationCode.toLowerCase().includes(q)
      );
    }
    return true;
  });

  const handleSearchChange = (query: string) => {
    setSearchQuery(query);
    if (query.trim().length >= 2) {
      const match = stations.find(
        (s) =>
          s.stationCode.toLowerCase() === query.trim().toLowerCase() ||
          s.stationName.toLowerCase().includes(query.trim().toLowerCase())
      );
      if (match && stationRefs.current[match.stationCode]) {
        stationRefs.current[match.stationCode]?.scrollIntoView({ behavior: 'smooth', block: 'center' });
        onSelectStation(match.stationCode);
      }
    }
  };

  useEffect(() => {
    activeTrains.forEach((t) => {
      if (t.status === 'SCHEDULED' || t.status === 'CANCELLED') return;
      if (!t.currentBlockId) {
        console.warn(`[CorridorMap] Unplaceable active train with null block: Train ID=${t.trainId}, Number=${t.trainNumber}, Status=${t.status}`);
      }
    });
  }, [activeTrains]);

  return (
    <div className="spatial-canvas" style={{ minHeight: '100%', padding: '20px 24px' }}>
      {/* 1. TOP SPATIAL OPERATIONS BAR */}
      <div
        className="spatial-glass"
        style={{
          borderRadius: 'var(--radius-lg)',
          padding: '16px 22px',
          marginBottom: '16px',
          display: 'flex',
          flexWrap: 'wrap',
          alignItems: 'center',
          justifyContent: 'space-between',
          gap: '16px',
        }}
      >
        <div>
          <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
            <span
              style={{
                background: 'rgba(56, 189, 248, 0.15)',
                color: 'var(--color-primary)',
                padding: '4px 10px',
                borderRadius: '6px',
                fontSize: '0.75rem',
                fontFamily: 'var(--font-mono)',
                fontWeight: 800,
                letterSpacing: '0.05em',
              }}
            >
              CONTINUOUS SPATIAL CORRIDOR
            </span>
            <span style={{ fontSize: '0.75rem', color: 'var(--text-muted)' }}>
              Western Railway &bull; Virar (VR) to Mumbai Central (MMCT) &bull; 24 Stations (60 km)
            </span>
          </div>
          <h1 style={{ fontSize: '1.35rem', fontWeight: 800, margin: '4px 0 0', letterSpacing: '-0.02em' }}>
            Railway Corridor Schematic &amp; Real-Time Trackway
          </h1>
        </div>

        {/* Search & Sector Filters */}
        <div style={{ display: 'flex', alignItems: 'center', gap: '10px', flexWrap: 'wrap' }}>
          <div style={{ position: 'relative', display: 'flex', alignItems: 'center' }}>
            <Search size={14} style={{ position: 'absolute', left: '10px', color: 'var(--text-dim)' }} />
            <input
              type="text"
              placeholder="Jump to station..."
              value={searchQuery}
              onChange={(e) => handleSearchChange(e.target.value)}
              style={{
                padding: '6px 12px 6px 30px',
                background: 'var(--glass-bg)',
                border: '1px solid var(--glass-border)',
                backdropFilter: 'var(--glass-blur)',
                WebkitBackdropFilter: 'var(--glass-blur)',
                borderRadius: 'var(--radius-sm)',
                color: 'var(--text-main)',
                fontSize: '0.8rem',
                outline: 'none',
                width: '180px',
              }}
            />
          </div>

          {/* Sector Selector */}
          <div
            style={{
              display: 'flex',
              gap: '3px',
              background: 'var(--glass-bg)',
              backdropFilter: 'var(--glass-blur)',
              WebkitBackdropFilter: 'var(--glass-blur)',
              padding: '3px',
              borderRadius: 'var(--radius-sm)',
              border: '1px solid var(--glass-border)',
            }}
          >
            {[
              { id: 'ALL', label: 'All 24' },
              { id: 'NORTH', label: 'North (VR-DIC)' },
              { id: 'CENTRAL', label: 'Central (BVI-BA)' },
              { id: 'SOUTH', label: 'South (MM-MMCT)' },
            ].map((sec) => (
              <button
                key={sec.id}
                onClick={() => setSelectedSector(sec.id as any)}
                style={{
                  padding: '4px 10px',
                  fontSize: '0.72rem',
                  fontWeight: selectedSector === sec.id ? 800 : 500,
                  background: selectedSector === sec.id ? 'var(--color-primary)' : 'transparent',
                  color: selectedSector === sec.id ? '#070b14' : 'var(--text-muted)',
                  border: 'none',
                  borderRadius: '4px',
                  cursor: 'pointer',
                  transition: 'all 0.15s ease',
                }}
              >
                {sec.label}
              </button>
            ))}
          </div>

          {/* Track Line Filter */}
          <div
            style={{
              display: 'flex',
              gap: '3px',
              background: 'var(--glass-bg)',
              backdropFilter: 'var(--glass-blur)',
              WebkitBackdropFilter: 'var(--glass-blur)',
              padding: '3px',
              borderRadius: 'var(--radius-sm)',
              border: '1px solid var(--glass-border)',
            }}
          >
            {[
              { id: 'ALL', label: 'All' },
              { id: 'SLOW_UP', label: 'Slow UP' },
              { id: 'FAST_UP', label: 'Fast UP' },
              { id: 'SLOW_DN', label: 'Slow DN' },
              { id: 'FAST_DN', label: 'Fast DN' },
            ].map((line) => (
              <button
                key={line.id}
                onClick={() => setTrackLineFilter(line.id as any)}
                style={{
                  padding: '4px 10px',
                  fontSize: '0.72rem',
                  fontWeight: trackLineFilter === line.id ? 800 : 500,
                  background: trackLineFilter === line.id ? 'rgba(56, 189, 248, 0.2)' : 'transparent',
                  color: trackLineFilter === line.id ? 'var(--color-primary)' : 'var(--text-muted)',
                  border: trackLineFilter === line.id ? '1px solid var(--color-primary)' : '1px solid transparent',
                  borderRadius: '4px',
                  cursor: 'pointer',
                  transition: 'all 0.15s ease',
                }}
              >
                {line.label}
              </button>
            ))}
          </div>
        </div>
      </div>

      {/* 2. SPATIAL TELEMETRY STATUS STRIP */}
      <div
        style={{
          display: 'grid',
          gridTemplateColumns: 'repeat(auto-fit, minmax(170px, 1fr))',
          gap: '12px',
          marginBottom: '16px',
        }}
      >
        <div
          className="spatial-glass spatial-pill"
          style={{ padding: '12px 16px', borderRadius: 'var(--radius-md)' }}
        >
          <div style={{ fontSize: '0.68rem', color: 'var(--text-dim)', textTransform: 'uppercase', fontWeight: 800 }}>
            Simulation Clock
          </div>
          <div style={{ fontFamily: 'var(--font-mono)', fontSize: '1.2rem', fontWeight: 800, color: 'var(--color-primary)', marginTop: '2px' }}>
            {corridorState?.simulationTime ? corridorState.simulationTime.substring(0, 8) : '--:--:--'}
          </div>
          <div style={{ fontSize: '0.7rem', color: 'var(--text-muted)', marginTop: '2px' }}>
            Speed: {corridorState?.speedMultiplier || 1}x &bull; Active Authoritative Sync
          </div>
        </div>

        <div
          className="spatial-glass spatial-pill"
          style={{ padding: '12px 16px', borderRadius: 'var(--radius-md)' }}
        >
          <div style={{ fontSize: '0.68rem', color: 'var(--text-dim)', textTransform: 'uppercase', fontWeight: 800 }}>
            Corridor Fleet
          </div>
          <div style={{ fontFamily: 'var(--font-mono)', fontSize: '1.2rem', fontWeight: 800, color: '#f8fafc', marginTop: '2px' }}>
            {activeTrains.length} Trains
          </div>
          <div style={{ fontSize: '0.7rem', color: 'var(--color-success)', marginTop: '2px' }}>
            {activeTrains.filter((t) => (t.speedKmH ?? 0) > 0).length} Moving &bull; {activeTrains.filter((t) => t.status === 'AT_PLATFORM').length} At Platform
          </div>
        </div>

        <div
          className="spatial-glass spatial-pill"
          style={{
            padding: '12px 16px',
            borderRadius: 'var(--radius-md)',
            border: waitingTrains.length > 0 ? '1px solid #ef4444' : undefined,
          }}
        >
          <div style={{ fontSize: '0.68rem', color: 'var(--text-dim)', textTransform: 'uppercase', fontWeight: 800 }}>
            Halted at Red Signal
          </div>
          <div
            style={{
              fontFamily: 'var(--font-mono)',
              fontSize: '1.2rem',
              fontWeight: 800,
              color: waitingTrains.length > 0 ? '#ef4444' : '#f8fafc',
              marginTop: '2px',
            }}
          >
            {waitingTrains.length} Trains
          </div>
          <div style={{ fontSize: '0.7rem', color: waitingTrains.length > 0 ? '#ef4444' : 'var(--text-muted)', marginTop: '2px' }}>
            {waitingTrains.length > 0 ? 'Signal Interlocking Enforced' : 'All Routes Clear'}
          </div>
        </div>

        <div
          className="spatial-glass spatial-pill"
          style={{ padding: '12px 16px', borderRadius: 'var(--radius-md)' }}
        >
          <div style={{ fontSize: '0.68rem', color: 'var(--text-dim)', textTransform: 'uppercase', fontWeight: 800 }}>
            Track Block Occupancy
          </div>
          <div style={{ fontFamily: 'var(--font-mono)', fontSize: '1.2rem', fontWeight: 800, color: '#f8fafc', marginTop: '2px' }}>
            {occupiedBlocks.length} Occupied
          </div>
          <div style={{ fontSize: '0.7rem', color: 'var(--text-muted)', marginTop: '2px' }}>
            Out of {trackBlocks.length} Track Blocks
          </div>
        </div>

        <div
          className="spatial-glass spatial-pill"
          style={{
            padding: '12px 16px',
            borderRadius: 'var(--radius-md)',
            border: closedPlatforms.length > 0 ? '1px solid #f59e0b' : undefined,
          }}
        >
          <div style={{ fontSize: '0.68rem', color: 'var(--text-dim)', textTransform: 'uppercase', fontWeight: 800 }}>
            Closed Platforms
          </div>
          <div
            style={{
              fontFamily: 'var(--font-mono)',
              fontSize: '1.2rem',
              fontWeight: 800,
              color: closedPlatforms.length > 0 ? '#f59e0b' : '#f8fafc',
              marginTop: '2px',
            }}
          >
            {closedPlatforms.length} Closed
          </div>
          <div style={{ fontSize: '0.7rem', color: closedPlatforms.length > 0 ? '#f59e0b' : 'var(--text-muted)', marginTop: '2px' }}>
            {closedPlatforms.length > 0 ? 'Maintenance / Cascade Active' : 'All Berths Operational'}
          </div>
        </div>
      </div>

      {/* 3. CONTROL ROOM LEGEND */}
      <div
        className="spatial-glass"
        style={{
          borderRadius: 'var(--radius-md)',
          padding: '10px 18px',
          display: 'flex',
          flexWrap: 'wrap',
          alignItems: 'center',
          gap: '20px',
          fontSize: '0.75rem',
          marginBottom: '20px',
        }}
      >
        <span style={{ fontWeight: 800, color: 'var(--text-dim)', textTransform: 'uppercase', letterSpacing: '0.05em' }}>
          Corridor Trackway Legend:
        </span>
        <div style={{ display: 'flex', alignItems: 'center', gap: '6px' }}>
          <span style={{ width: '16px', height: '4px', background: '#38bdf8', borderRadius: '2px' }} />
          <span>⚡ Fast Line (UP/DOWN)</span>
        </div>
        <div style={{ display: 'flex', alignItems: 'center', gap: '6px' }}>
          <span style={{ width: '16px', height: '4px', background: '#10b981', borderRadius: '2px' }} />
          <span>🐢 Slow Line (UP/DOWN)</span>
        </div>
        <div style={{ display: 'flex', alignItems: 'center', gap: '6px' }}>
          <span style={{ width: '10px', height: '10px', borderRadius: '50%', background: '#10b981', boxShadow: '0 0 6px #10b981' }} />
          <span>🟢 Green</span>
        </div>
        <div style={{ display: 'flex', alignItems: 'center', gap: '6px' }}>
          <span style={{ width: '10px', height: '10px', borderRadius: '50%', background: '#eab308', boxShadow: '0 0 6px #eab308' }} />
          <span>🟡 Yellow</span>
        </div>
        <div style={{ display: 'flex', alignItems: 'center', gap: '6px' }}>
          <span style={{ width: '10px', height: '10px', borderRadius: '50%', background: '#ef4444', boxShadow: '0 0 8px #ef4444' }} />
          <span>🔴 Red (Halt)</span>
        </div>
        <div style={{ display: 'flex', alignItems: 'center', gap: '6px' }}>
          <span style={{ display: 'inline-block', width: '12px', height: '10px', background: 'rgba(239,68,68,0.25)', border: '1px solid #ef4444' }} />
          <span>■ Occupied Block</span>
        </div>
        <div style={{ display: 'flex', alignItems: 'center', gap: '6px' }}>
          <span style={{ color: '#ef4444', fontWeight: 800 }}>⛔</span>
          <span>Closed Berth</span>
        </div>
        <div style={{ display: 'flex', alignItems: 'center', gap: '6px' }}>
          <TrainIcon size={13} color="var(--color-primary)" />
          <span>🚆 Train (Click to inspect signal diagnostics)</span>
        </div>
      </div>

      {/* Selected Signal Detail Popover Modal if clicked */}
      {selectedSignal && (
        <div
          className="spatial-glass"
          style={{
            borderRadius: 'var(--radius-md)',
            padding: '14px 20px',
            marginBottom: '18px',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'space-between',
            border: '1.5px solid var(--color-primary)',
          }}
        >
          <div style={{ display: 'flex', alignItems: 'center', gap: '16px' }}>
            <SignalHead signal={selectedSignal} />
            <div>
              <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
                <span style={{ fontFamily: 'var(--font-mono)', fontWeight: 800, fontSize: '0.95rem', color: '#f8fafc' }}>
                  {selectedSignal.signalId}
                </span>
                <span
                  style={{
                    fontSize: '0.72rem',
                    fontFamily: 'var(--font-mono)',
                    fontWeight: 800,
                    padding: '2px 8px',
                    borderRadius: '4px',
                    background:
                      selectedSignal.aspect === 'RED'
                        ? 'rgba(239, 68, 68, 0.25)'
                        : selectedSignal.aspect === 'GREEN'
                        ? 'rgba(16, 185, 129, 0.25)'
                        : 'rgba(234, 179, 8, 0.25)',
                    color:
                      selectedSignal.aspect === 'RED'
                        ? '#ef4444'
                        : selectedSignal.aspect === 'GREEN'
                        ? '#10b981'
                        : '#eab308',
                  }}
                >
                  {selectedSignal.aspect}
                </span>
                <span style={{ fontSize: '0.75rem', color: 'var(--text-muted)' }}>
                  Station: <strong>{selectedSignal.stationCode}</strong> &bull; Protected Block:{' '}
                  <code style={{ color: 'var(--color-primary)' }}>{selectedSignal.protectedBlockId}</code>
                </span>
              </div>
              <div style={{ fontSize: '0.82rem', color: 'var(--text-main)', marginTop: '4px' }}>
                <strong>Evaluated Interlocking Reason:</strong> {selectedSignal.reason}
              </div>
            </div>
          </div>

          <button
            onClick={() => setSelectedSignal(null)}
            style={{
              background: 'transparent',
              border: 'none',
              color: 'var(--text-dim)',
              cursor: 'pointer',
              padding: '6px',
            }}
          >
            <X size={18} />
          </button>
        </div>
      )}

      {/* 4. MAIN CONTINUOUS CORRIDOR TRACKWAY */}
      <div
        className="spatial-glass"
        style={{
          borderRadius: 'var(--radius-lg)',
          padding: '28px 24px',
          overflowX: 'auto',
        }}
      >
        <div style={{ minWidth: '940px' }}>
          {filteredStations.map((station, idx) => {
            const isSelected = station.stationCode === selectedStation;
            const nextStation = idx < filteredStations.length - 1 ? filteredStations[idx + 1] : null;

            // Station platforms
            const stationPlatforms = platforms.filter((p) => p.stationCode === station.stationCode);

            // Trains berthed or docked at this station
            const stationDockedTrains = activeTrains.filter(
              (t) =>
                t.status === 'AT_PLATFORM' &&
                (t.targetStationCode === station.stationCode ||
                  t.currentStationCode === station.stationCode ||
                  (t.currentBlockId && t.currentBlockId.startsWith(`BLK-${station.stationCode}-P`)))
            );

            // Inter-station track blocks between this station and next (UP: station -> nextStation, DN: nextStation -> station)
            let fastUpBlock: TrackBlock | undefined;
            let slowUpBlock: TrackBlock | undefined;
            let fastDnBlock: TrackBlock | undefined;
            let slowDnBlock: TrackBlock | undefined;

            let fastUpSignal: Signal | undefined;
            let slowUpSignal: Signal | undefined;
            let fastDnSignal: Signal | undefined;
            let slowDnSignal: Signal | undefined;

            let fastUpTrains: TrainSnapshot[] = [];
            let slowUpTrains: TrainSnapshot[] = [];
            let fastDnTrains: TrainSnapshot[] = [];
            let slowDnTrains: TrainSnapshot[] = [];

            if (nextStation) {
              fastUpBlock = trackBlocks.find(
                (b) =>
                  b.blockId === `BLK-${station.stationCode}-${nextStation.stationCode}-FU` ||
                  (b.fromStationCode === station.stationCode && b.toStationCode === nextStation.stationCode && b.lineType === 'FAST_UP')
              );
              slowUpBlock = trackBlocks.find(
                (b) =>
                  b.blockId === `BLK-${station.stationCode}-${nextStation.stationCode}-SU` ||
                  (b.fromStationCode === station.stationCode && b.toStationCode === nextStation.stationCode && b.lineType === 'SLOW_UP')
              );
              fastDnBlock = trackBlocks.find(
                (b) =>
                  b.blockId === `BLK-${nextStation.stationCode}-${station.stationCode}-FD` ||
                  (b.fromStationCode === nextStation.stationCode && b.toStationCode === station.stationCode && b.lineType === 'FAST_DOWN')
              );
              slowDnBlock = trackBlocks.find(
                (b) =>
                  b.blockId === `BLK-${nextStation.stationCode}-${station.stationCode}-SD` ||
                  (b.fromStationCode === nextStation.stationCode && b.toStationCode === station.stationCode && b.lineType === 'SLOW_DOWN')
              );

              if (fastUpBlock) {
                fastUpSignal = signals.find((s) => s.protectedBlockId === fastUpBlock?.blockId);
                fastUpTrains = activeTrains.filter((t) => t.currentBlockId === fastUpBlock?.blockId && t.status !== 'AT_PLATFORM');
              }
              if (slowUpBlock) {
                slowUpSignal = signals.find((s) => s.protectedBlockId === slowUpBlock?.blockId);
                slowUpTrains = activeTrains.filter((t) => t.currentBlockId === slowUpBlock?.blockId && t.status !== 'AT_PLATFORM');
              }
              if (fastDnBlock) {
                fastDnSignal = signals.find((s) => s.protectedBlockId === fastDnBlock?.blockId);
                fastDnTrains = activeTrains.filter((t) => t.currentBlockId === fastDnBlock?.blockId && t.status !== 'AT_PLATFORM');
              }
              if (slowDnBlock) {
                slowDnSignal = signals.find((s) => s.protectedBlockId === slowDnBlock?.blockId);
                slowDnTrains = activeTrains.filter((t) => t.currentBlockId === slowDnBlock?.blockId && t.status !== 'AT_PLATFORM');
              }
            }

            return (
              <div
                key={station.stationCode}
                ref={(el) => {
                  stationRefs.current[station.stationCode] = el;
                }}
                style={{ position: 'relative' }}
              >
                {/* STATION NODE WAYPOINT */}
                <div
                  style={{
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'space-between',
                    background: isSelected ? 'rgba(56, 189, 248, 0.12)' : 'rgba(15, 23, 42, 0.85)',
                    border: `1.5px solid ${isSelected ? 'var(--color-primary)' : 'rgba(56, 189, 248, 0.2)'}`,
                    borderRadius: 'var(--radius-md)',
                    padding: '12px 18px',
                    transition: 'all 0.2s ease',
                    boxShadow: isSelected ? '0 0 15px rgba(56, 189, 248, 0.25)' : 'none',
                    cursor: 'pointer',
                  }}
                  onClick={() => onSelectStation(station.stationCode)}
                >
                  {/* Left: Station Identity */}
                  <div style={{ display: 'flex', alignItems: 'center', gap: '14px' }}>
                    <div
                      style={{
                        width: '34px',
                        height: '34px',
                        borderRadius: '50%',
                        background: isSelected ? 'var(--color-primary)' : '#1e293b',
                        color: isSelected ? '#070b14' : '#f8fafc',
                        border: `2px solid ${isSelected ? 'var(--color-primary)' : '#475569'}`,
                        display: 'flex',
                        alignItems: 'center',
                        justifyContent: 'center',
                        fontFamily: 'var(--font-mono)',
                        fontWeight: 800,
                        fontSize: '0.82rem',
                        flexShrink: 0,
                      }}
                    >
                      {station.sequenceOrder}
                    </div>

                    <div>
                      <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                        <span style={{ fontWeight: 800, fontSize: '1.05rem', color: '#f8fafc' }}>
                          {station.stationName}
                        </span>
                        <span
                          style={{
                            fontFamily: 'var(--font-mono)',
                            fontSize: '0.72rem',
                            background: 'rgba(56, 189, 248, 0.15)',
                            padding: '2px 6px',
                            borderRadius: '4px',
                            color: 'var(--color-primary)',
                            fontWeight: 800,
                          }}
                        >
                          {station.stationCode}
                        </span>
                        {station.terminalFlag && (
                          <span
                            style={{
                              fontSize: '0.62rem',
                              fontWeight: 800,
                              background: 'rgba(245, 158, 11, 0.2)',
                              color: '#f59e0b',
                              border: '1px solid #f59e0b',
                              padding: '1px 5px',
                              borderRadius: '3px',
                            }}
                          >
                            MAJOR TERMINAL HUB
                          </span>
                        )}
                        {station.fastLineSupport && (
                          <span style={{ fontSize: '0.65rem', color: '#38bdf8', fontWeight: 600 }}>
                            ⚡ Fast Line Node
                          </span>
                        )}
                      </div>
                    </div>
                  </div>

                  {/* Right: Berth mini-pills & Expand Toggle */}
                  <div style={{ display: 'flex', alignItems: 'center', gap: '12px' }}>
                    {/* Platform Occupancy Indicators Grouped by Line (Fast UP, Slow UP, Fast DN, Slow DN) */}
                    <div style={{ display: 'flex', alignItems: 'center', gap: '6px', flexWrap: 'wrap' }}>
                      {[
                        { key: 'Fast UP', label: 'Fast UP', color: '#38bdf8', plats: stationPlatforms.filter((p) => p.lineType === 'FAST_UP') },
                        { key: 'Slow UP', label: 'Slow UP', color: '#10b981', plats: stationPlatforms.filter((p) => p.lineType === 'SLOW_UP') },
                        { key: 'Fast DN', label: 'Fast DN', color: '#f59e0b', plats: stationPlatforms.filter((p) => p.lineType === 'FAST_DOWN') },
                        { key: 'Slow DN', label: 'Slow DN', color: '#f97316', plats: stationPlatforms.filter((p) => p.lineType === 'SLOW_DOWN') },
                      ]
                        .filter((g) => g.plats.length > 0)
                        .map((g) => (
                          <div
                            key={g.key}
                            style={{
                              display: 'inline-flex',
                              alignItems: 'center',
                              gap: '4px',
                              background: 'rgba(15, 23, 42, 0.75)',
                              border: `1px solid rgba(255, 255, 255, 0.08)`,
                              borderRadius: '4px',
                              padding: '2px 5px',
                            }}
                          >
                            <span style={{ fontSize: '0.62rem', fontWeight: 700, color: g.color }}>{g.label}:</span>
                            <div style={{ display: 'inline-flex', alignItems: 'center', gap: '3px' }}>
                              {g.plats.map((plat) => {
                                const platColor = plat.closed ? '#ef4444' : plat.occupied ? '#eab308' : '#10b981';
                                return (
                                  <span
                                    key={plat.platformId}
                                    title={`Platform ${plat.platformNumber} (${g.label}): ${plat.closed ? 'CLOSED' : plat.occupied ? 'OCCUPIED' : 'CLEAR'}`}
                                    style={{
                                      display: 'inline-flex',
                                      alignItems: 'center',
                                      justifyContent: 'center',
                                      width: '18px',
                                      height: '18px',
                                      borderRadius: '3px',
                                      background: plat.closed
                                        ? 'rgba(239, 68, 68, 0.2)'
                                        : plat.occupied
                                        ? 'rgba(234, 179, 8, 0.2)'
                                        : 'rgba(16, 185, 129, 0.2)',
                                      border: `1px solid ${platColor}`,
                                      fontSize: '0.6rem',
                                      fontFamily: 'var(--font-mono)',
                                      fontWeight: 800,
                                      color: platColor,
                                    }}
                                  >
                                    P{plat.platformNumber}
                                  </span>
                                );
                              })}
                            </div>
                          </div>
                        ))}
                    </div>

                    {stationDockedTrains.length > 0 && (
                      <span
                        style={{
                          fontSize: '0.68rem',
                          background: 'rgba(56, 189, 248, 0.15)',
                          color: 'var(--color-primary)',
                          padding: '2px 8px',
                          borderRadius: '4px',
                          fontWeight: 700,
                        }}
                      >
                        🚆 {stationDockedTrains.length} Berthed
                      </span>
                    )}

                    <button
                      onClick={(e) => {
                        e.stopPropagation();
                        toggleStationExpanded(station.stationCode);
                      }}
                      style={{
                        background: 'transparent',
                        border: '1px solid rgba(56, 189, 248, 0.3)',
                        borderRadius: '4px',
                        color: 'var(--text-muted)',
                        padding: '4px 8px',
                        fontSize: '0.7rem',
                        cursor: 'pointer',
                        display: 'flex',
                        alignItems: 'center',
                        gap: '4px',
                      }}
                    >
                      {expandedStations[station.stationCode] ? (
                        <>
                          <ChevronUp size={12} /> Hide Berths
                        </>
                      ) : (
                        <>
                          <ChevronDown size={12} /> {stationPlatforms.length} Berths
                        </>
                      )}
                    </button>
                  </div>
                </div>

                {/* CONTEXTUAL PLATFORM BERTHS DECK (Expandable) */}
                {stationPlatforms.length > 0 && (isSelected || expandedStations[station.stationCode]) && (
                  <div
                    className="glass-panel"
                    style={{
                      margin: '6px 0 10px 48px',
                      padding: '12px 16px',
                    }}
                  >
                    <div
                      style={{
                        fontSize: '0.7rem',
                        fontWeight: 800,
                        color: 'var(--text-dim)',
                        textTransform: 'uppercase',
                        marginBottom: '8px',
                        display: 'flex',
                        justifyContent: 'space-between',
                      }}
                    >
                      <span>{station.stationName} ({station.stationCode}) Platform Interlocking Berths</span>
                      <span style={{ color: 'var(--text-muted)', fontFamily: 'var(--font-mono)' }}>
                        Track Blocks: BLK-{station.stationCode}-P[1..{stationPlatforms.length}]
                      </span>
                    </div>

                    <div
                      style={{
                        display: 'grid',
                        gridTemplateColumns: 'repeat(auto-fill, minmax(210px, 1fr))',
                        gap: '8px',
                      }}
                    >
                      {stationPlatforms.map((plat) => {
                        const platHomeSignal = signals.find(
                          (s) => (s.protectedBlockId === `BLK-${station.stationCode}-P${plat.platformNumber}` || s.signalId === `SIG-${station.stationCode}-P${plat.platformNumber}`) && !s.signalId.endsWith('-STARTER')
                        );
                        const platStarterSignal = signals.find(
                          (s) => s.signalId === `SIG-${station.stationCode}-P${plat.platformNumber}-STARTER`
                        );
                        const platTrain = activeTrains.find(
                          (t) =>
                            (t.assignedPlatform === plat.platformNumber ||
                             t.currentBlockId === `BLK-${station.stationCode}-P${plat.platformNumber}`) &&
                            (t.status === 'AT_PLATFORM' ||
                              t.status === 'DEPARTED' ||
                              t.currentStationCode === station.stationCode)
                        );
                        const waitingTrain = plat.nextWaitingTrainNumber ||
                          activeTrains.find(
                            (t) =>
                              t.assignedPlatform === plat.platformNumber &&
                              (t.status === 'WAITING' || t.status === 'APPROACHING') &&
                              t.trainNumber !== platTrain?.trainNumber
                          )?.trainNumber;

                        return (
                          <div
                            key={plat.platformId}
                            style={{
                              background: plat.closed
                                ? 'rgba(239, 68, 68, 0.08)'
                                : plat.occupied
                                ? 'rgba(234, 179, 8, 0.08)'
                                : 'rgba(30, 41, 59, 0.5)',
                              border: `1.5px solid ${
                                plat.closed
                                  ? '#ef4444'
                                  : plat.occupied
                                  ? 'rgba(234, 179, 8, 0.5)'
                                  : 'rgba(56, 189, 248, 0.15)'
                              }`,
                              borderRadius: 'var(--radius-sm)',
                              padding: '8px 10px',
                              display: 'flex',
                              flexDirection: 'column',
                              gap: '6px',
                            }}
                          >
                            <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
                              <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                                <div style={{ display: 'flex', alignItems: 'center', gap: '4px' }}>
                                  {platHomeSignal && (
                                    <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', gap: '1px' }}>
                                      <SignalHead
                                        signal={platHomeSignal}
                                        onClick={() => setSelectedSignal(platHomeSignal)}
                                      />
                                      <span style={{ fontSize: '0.52rem', fontWeight: 800, color: 'var(--text-dim)', letterSpacing: '0.02em' }}>HOME</span>
                                    </div>
                                  )}
                                  {platStarterSignal && (
                                    <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', gap: '1px' }}>
                                      <SignalHead
                                        signal={platStarterSignal}
                                        onClick={() => setSelectedSignal(platStarterSignal)}
                                      />
                                      <span style={{ fontSize: '0.52rem', fontWeight: 800, color: 'var(--text-dim)', letterSpacing: '0.02em' }}>START</span>
                                    </div>
                                  )}
                                </div>
                                <div>
                                  <div style={{ display: 'flex', alignItems: 'center', gap: '6px' }}>
                                    <span style={{ fontWeight: 800, fontSize: '0.78rem', color: '#f8fafc' }}>
                                      Platform {plat.platformNumber}
                                    </span>
                                    <span style={{ fontSize: '0.6rem', color: 'var(--text-dim)', fontFamily: 'var(--font-mono)' }}>
                                      {plat.direction || (plat.lineType.includes('UP') ? 'UP' : plat.lineType.includes('DOWN') ? 'DN' : 'CM')}
                                    </span>
                                    {plat.closed ? (
                                      <span style={{ fontSize: '0.62rem', color: '#ef4444', fontWeight: 800 }}>
                                        ⛔ CLOSED
                                      </span>
                                    ) : plat.occupied ? (
                                      <span style={{ fontSize: '0.62rem', color: '#eab308', fontWeight: 700 }}>
                                        OCCUPIED
                                      </span>
                                    ) : (
                                      <span style={{ fontSize: '0.62rem', color: '#10b981', fontWeight: 600 }}>
                                        CLEAR
                                      </span>
                                    )}
                                  </div>
                                  <div style={{ fontSize: '0.65rem', color: 'var(--text-muted)', fontFamily: 'var(--font-mono)' }}>
                                    BLK-{station.stationCode}-P{plat.platformNumber}
                                  </div>
                                </div>
                              </div>

                              {plat.dwellSecondsRemaining != null && plat.occupied && (
                                <span style={{ fontSize: '0.68rem', color: '#38bdf8', fontFamily: 'var(--font-mono)', fontWeight: 700 }}>
                                  ⏱ {plat.dwellSecondsRemaining}s left
                                </span>
                              )}
                            </div>

                            {/* Docked Train Berth Visualization */}
                            {platTrain && (
                              <div
                                style={{
                                  background: 'rgba(56, 189, 248, 0.12)',
                                  border: '1px solid var(--color-primary)',
                                  borderRadius: '4px',
                                  padding: '4px 8px',
                                  display: 'flex',
                                  alignItems: 'center',
                                  justifyContent: 'space-between',
                                }}
                              >
                                <button
                                  onClick={() => handleTrainClick(platTrain)}
                                  style={{
                                    background: 'transparent',
                                    border: 'none',
                                    fontFamily: 'var(--font-mono)',
                                    color: 'var(--color-primary)',
                                    cursor: 'pointer',
                                    display: 'flex',
                                    alignItems: 'center',
                                    gap: '6px',
                                    fontSize: '0.72rem',
                                    fontWeight: 700,
                                    padding: 0,
                                  }}
                                >
                                  <TrainIcon size={12} />
                                  <span>{platTrain.trainNumber}</span>
                                  <span style={{ fontSize: '0.62rem', color: 'var(--text-dim)' }}>
                                    ({platTrain.trainType ? platTrain.trainType.substring(0, 4) : 'TRN'})
                                  </span>
                                </button>
                                <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'flex-end', gap: '1px' }}>
                                  <span style={{ fontSize: '0.62rem', color: '#10b981', fontWeight: 800 }}>
                                    {formatTrainLocation(platTrain)} (0 km/h)
                                  </span>
                                  <span style={{ fontSize: '0.58rem', color: '#38bdf8', fontFamily: 'var(--font-mono)' }}>
                                    Next: {formatNextStop(platTrain)}
                                  </span>
                                </div>
                              </div>
                            )}

                            {/* Next Waiting Train */}
                            {waitingTrain && (
                              <div style={{ fontSize: '0.65rem', color: '#eab308', display: 'flex', alignItems: 'center', gap: '4px' }}>
                                <span>Next Waiting:</span>
                                <span style={{ fontFamily: 'var(--font-mono)', fontWeight: 700 }}>{waitingTrain}</span>
                              </div>
                            )}
                          </div>
                        );
                      })}
                    </div>
                  </div>
                )}

                {/* CONTINUOUS TRACK SEGMENT CONNECTING TO NEXT STATION */}
                {nextStation && (
                  <div
                    style={{
                      margin: '4px 0 4px 48px',
                      padding: '8px 14px',
                      borderLeft: '3px dashed rgba(56, 189, 248, 0.3)',
                      display: 'flex',
                      flexDirection: 'column',
                      gap: '8px',
                    }}
                  >
                    {[
                      {
                        key: 'FAST_UP',
                        show: trackLineFilter === 'ALL' || trackLineFilter === 'FAST_UP',
                        label: 'FAST LINE UP',
                        dirLabel: '▼ Southbound (UP)',
                        color: '#38bdf8',
                        block: fastUpBlock,
                        signal: fastUpSignal,
                        trains: fastUpTrains,
                        isDn: false,
                      },
                      {
                        key: 'SLOW_UP',
                        show: trackLineFilter === 'ALL' || trackLineFilter === 'SLOW_UP',
                        label: 'SLOW LINE UP',
                        dirLabel: '▼ Southbound (UP)',
                        color: '#10b981',
                        block: slowUpBlock,
                        signal: slowUpSignal,
                        trains: slowUpTrains,
                        isDn: false,
                      },
                      {
                        key: 'FAST_DN',
                        show: trackLineFilter === 'ALL' || trackLineFilter === 'FAST_DN',
                        label: 'FAST LINE DN',
                        dirLabel: '▲ Northbound (DN)',
                        color: '#f59e0b',
                        block: fastDnBlock,
                        signal: fastDnSignal,
                        trains: fastDnTrains,
                        isDn: true,
                      },
                      {
                        key: 'SLOW_DN',
                        show: trackLineFilter === 'ALL' || trackLineFilter === 'SLOW_DN',
                        label: 'SLOW LINE DN',
                        dirLabel: '▲ Northbound (DN)',
                        color: '#f97316',
                        block: slowDnBlock,
                        signal: slowDnSignal,
                        trains: slowDnTrains,
                        isDn: true,
                      },
                    ]
                      .filter((t) => t.show && t.block)
                      .map((track) => {
                        const isBlockOccupied = track.block?.occupied || track.trains.length > 0;
                        return (
                          <div
                            key={track.key}
                            style={{
                              background: isBlockOccupied ? 'rgba(239, 68, 68, 0.08)' : 'rgba(15, 23, 42, 0.5)',
                              border: `1.5px solid ${isBlockOccupied ? 'rgba(239, 68, 68, 0.6)' : `${track.color}40`}`,
                              borderRadius: 'var(--radius-md)',
                              padding: '8px 12px',
                              display: 'flex',
                              flexDirection: 'column',
                              gap: '6px',
                              boxShadow: isBlockOccupied ? '0 0 12px rgba(239, 68, 68, 0.2)' : 'none',
                            }}
                          >
                            {/* Header: Block Info & Signal */}
                            <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
                              <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                                <span
                                  style={{
                                    width: '10px',
                                    height: '10px',
                                    borderRadius: '2px',
                                    background: track.color,
                                    boxShadow: `0 0 6px ${track.color}`,
                                  }}
                                />
                                <span style={{ fontSize: '0.72rem', fontWeight: 800, color: track.color, fontFamily: 'var(--font-mono)' }}>
                                  {track.label}
                                </span>
                                <span style={{ fontSize: '0.68rem', fontFamily: 'var(--font-mono)', color: 'var(--text-muted)' }}>
                                  {track.block?.blockId}
                                </span>
                                <span style={{ fontSize: '0.65rem', color: 'var(--text-dim)' }}>
                                  &bull; {track.block?.lengthKm || 2.5} km &bull; {track.dirLabel}
                                </span>
                              </div>

                              {/* 4-Aspect Signal Head */}
                              <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                                <span style={{ fontSize: '0.68rem', fontFamily: 'var(--font-mono)', color: 'var(--text-dim)' }}>
                                  {track.signal?.signalId || (track.block ? `SIG-${track.block.blockId.substring(4)}` : '')}
                                </span>
                                <SignalHead
                                  signal={track.signal}
                                  onClick={() => track.signal && setSelectedSignal(track.signal)}
                                />
                              </div>
                            </div>

                            {/* Physical Railway Track Bed */}
                            <div className="railway-track-bed">
                              {track.trains.length === 0 ? (
                                <div
                                  style={{
                                    width: '100%',
                                    textAlign: 'center',
                                    fontSize: '0.68rem',
                                    color: 'rgba(148, 163, 184, 0.45)',
                                    letterSpacing: '0.08em',
                                    userSelect: 'none',
                                  }}
                                >
                                  ━━ Clear Track ━━
                                </div>
                              ) : (
                                track.trains.map((train) => {
                                  const isWaiting = train.status === 'WAITING' || train.signalAspect === 'RED';
                                  const trainCatColor = getTrainCategoryColor(train.trainType);
                                  const rawPct = train.progressPercent ?? 0;
                                  const posPercent = isWaiting ? (track.isDn ? 88 : 12) : Math.min(92, Math.max(8, track.isDn ? 100 - rawPct : rawPct));

                                  return (
                                    <div
                                      key={train.trainId}
                                      className="track-train-marker"
                                      onClick={() => handleTrainClick(train)}
                                      style={{ left: `${posPercent}%` }}
                                      title={`Train ${train.trainNumber}: ${formatTrainLocation(train)} | Next: ${formatNextStop(train)}${isWaiting ? ' | ' + (train.waitingReason || 'Waiting at signal') : ''}`}
                                    >
                                      <div
                                        className={isWaiting ? 'pulse-danger' : ''}
                                        style={{
                                          background: '#090d16',
                                          border: `1.5px solid ${isWaiting ? '#ef4444' : trainCatColor}`,
                                          borderRadius: '6px',
                                          padding: '2px 8px',
                                          display: 'inline-flex',
                                          alignItems: 'center',
                                          gap: '6px',
                                          boxShadow: isWaiting
                                            ? '0 0 12px rgba(239, 68, 68, 0.6)'
                                            : `0 0 10px ${trainCatColor}40`,
                                        }}
                                      >
                                        <span style={{ fontSize: '0.7rem' }}>🚆</span>
                                        <span style={{ fontFamily: 'var(--font-mono)', fontWeight: 800, fontSize: '0.72rem', color: '#f8fafc' }}>
                                          {train.trainNumber}
                                        </span>
                                        <span style={{ fontSize: '0.6rem', color: 'var(--text-muted)' }}>
                                          {train.direction === 'DOWN' ? '▲ DN' : '▼ UP'}
                                        </span>

                                        {isWaiting ? (
                                          <span
                                            style={{
                                              fontSize: '0.6rem',
                                              fontWeight: 800,
                                              background: 'rgba(239, 68, 68, 0.3)',
                                              color: '#ef4444',
                                              padding: '1px 4px',
                                              borderRadius: '2px',
                                            }}
                                          >
                                            🛑 HALT
                                          </span>
                                        ) : (
                                          <span
                                            style={{
                                              fontSize: '0.6rem',
                                              fontFamily: 'var(--font-mono)',
                                              fontWeight: 700,
                                              color: 'var(--color-primary)',
                                            }}
                                          >
                                            {Math.round(train.speedKmH ?? 0)} km/h
                                          </span>
                                        )}

                                        {train.delayMinutes > 0 && (
                                          <span
                                            style={{
                                              fontSize: '0.58rem',
                                              background: 'rgba(239, 68, 68, 0.25)',
                                              color: '#ef4444',
                                              fontWeight: 800,
                                              padding: '1px 4px',
                                              borderRadius: '2px',
                                            }}
                                          >
                                            +{train.delayMinutes}m
                                          </span>
                                        )}
                                      </div>
                                    </div>
                                  );
                                })
                              )}
                            </div>
                          </div>
                        );
                      })}
                  </div>
                )}
              </div>
            );
          })}
        </div>
      </div>

      {/* Shared Train Diagnostics Drawer */}
      {inspectingTrain && (
        <TrainSignalDrawer
          train={inspectingTrain}
          signals={signals}
          trackBlocks={trackBlocks}
          onClose={() => setInspectingTrain(null)}
        />
      )}
    </div>
  );
};
