import React from 'react';
import { TrainSnapshot, Signal, TrackBlock } from '../types';
import { formatTrainLocation, formatNextStop } from '../utils/trainFormatters';
import {
  X,
  Train as TrainIcon,
  AlertTriangle,
  CheckCircle2,
  Radio,
  MapPin,
  Clock,
  Navigation,
  ShieldAlert,
  Layers,
  ArrowRight,
} from 'lucide-react';

interface TrainSignalDrawerProps {
  train: TrainSnapshot | null;
  signals: Signal[];
  trackBlocks: TrackBlock[];
  onClose: () => void;
}

export const TrainSignalDrawer: React.FC<TrainSignalDrawerProps> = ({
  train,
  signals,
  trackBlocks,
  onClose,
}) => {
  if (!train) return null;

  const isWaiting =
    train.status === 'WAITING' ||
    (train.waitingReason && train.waitingReason.trim().length > 0) ||
    train.signalAspect === 'RED';

  // Find corresponding signal and track block
  const controllingSignal = signals.find((s) => s.signalId === train.nextSignalId);
  const currentBlock = trackBlocks.find((b) => b.blockId === train.currentBlockId);

  const isUp = currentBlock
    ? Boolean(currentBlock.direction === 'UP' || currentBlock.lineType?.includes('UP'))
    : (train.direction === 'UP');
  const isFast = currentBlock
    ? Boolean(currentBlock.lineType?.includes('FAST'))
    : false;
  const dirBadge = `${isUp ? 'UP ↓' : 'DN ↑'} ${isFast ? 'Fast' : 'Slow'}`;
  const dirColor = isUp ? '#10b981' : '#f97316';
  const stoppedReason = train.waitingReason || (controllingSignal?.reason ? `Signal ${controllingSignal.signalId}: ${controllingSignal.reason}` : 'Awaiting platform or route block clearance');

  const getAspectBadge = (aspect?: string) => {
    switch (aspect) {
      case 'RED':
        return {
          label: 'RED (STOP)',
          color: '#ef4444',
          bg: 'rgba(239, 68, 68, 0.2)',
          border: '#ef4444',
          icon: '🔴',
        };
      case 'YELLOW':
        return {
          label: 'YELLOW (CAUTION)',
          color: '#f59e0b',
          bg: 'rgba(245, 158, 11, 0.2)',
          border: '#f59e0b',
          icon: '🟡',
        };
      case 'DOUBLE_YELLOW':
        return {
          label: 'DOUBLE YELLOW (ADV. CAUTION)',
          color: '#eab308',
          bg: 'rgba(234, 179, 8, 0.2)',
          border: '#eab308',
          icon: '🟡🟡',
        };
      case 'GREEN':
        return {
          label: 'GREEN (PROCEED)',
          color: '#10b981',
          bg: 'rgba(16, 185, 129, 0.2)',
          border: '#10b981',
          icon: '🟢',
        };
      default:
        return {
          label: aspect || 'UNKNOWN',
          color: '#94a3b8',
          bg: 'rgba(148, 163, 184, 0.1)',
          border: '#64748b',
          icon: '⚪',
        };
    }
  };

  const aspectInfo = getAspectBadge(train.signalAspect || controllingSignal?.aspect);

  return (
    <div
      className="glass-panel"
      style={{
        position: 'fixed',
        top: 0,
        right: 0,
        bottom: 0,
        width: '440px',
        maxWidth: '90vw',
        borderRadius: 0,
        borderRight: 'none',
        borderTop: 'none',
        borderBottom: 'none',
        zIndex: 1000,
        display: 'flex',
        flexDirection: 'column',
        animation: 'slideInRight 0.25s ease-out',
      }}
    >
      {/* Header */}
      <div
        style={{
          padding: '18px 24px',
          borderBottom: '1px solid var(--glass-border)',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'space-between',
        }}
      >
        <div style={{ display: 'flex', alignItems: 'center', gap: '12px' }}>
          <div
            style={{
              width: '40px',
              height: '40px',
              borderRadius: 'var(--radius-md)',
              background: isWaiting ? 'rgba(239, 68, 68, 0.2)' : 'rgba(56, 189, 248, 0.2)',
              border: `1px solid ${isWaiting ? '#ef4444' : 'var(--color-primary)'}`,
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              color: isWaiting ? '#ef4444' : 'var(--color-primary)',
            }}
          >
            <TrainIcon size={22} />
          </div>
          <div>
            <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
              <span style={{ fontFamily: 'var(--font-mono)', fontWeight: 800, fontSize: '1.15rem' }}>
                {train.trainNumber}
              </span>
              <span
                style={{
                  fontFamily: 'var(--font-mono)',
                  fontSize: '0.7rem',
                  fontWeight: 800,
                  padding: '2px 6px',
                  borderRadius: '4px',
                  background: isUp ? 'rgba(16, 185, 129, 0.2)' : 'rgba(249, 115, 22, 0.2)',
                  color: dirColor,
                  border: `1px solid ${isUp ? 'rgba(16, 185, 129, 0.4)' : 'rgba(249, 115, 22, 0.4)'}`,
                }}
              >
                {dirBadge}
              </span>
              <span
                className={`badge ${
                  train.status === 'APPROACHING' || train.status === 'ARRIVING' || train.status === 'AT_PLATFORM'
                    ? 'badge-success'
                    : isWaiting
                    ? 'badge-danger'
                    : 'badge-info'
                }`}
                style={{ fontSize: '0.7rem' }}
              >
                {train.status}
              </span>
            </div>
            <div style={{ fontSize: '0.8rem', color: 'var(--text-muted)' }}>
              {train.trainName}
            </div>
          </div>
        </div>

        <button
          onClick={onClose}
          style={{
            background: 'transparent',
            border: 'none',
            color: 'var(--text-dim)',
            cursor: 'pointer',
            padding: '6px',
            borderRadius: 'var(--radius-sm)',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
          }}
          title="Close Inspector"
        >
          <X size={20} />
        </button>
      </div>

      {/* Drawer Content Body */}
      <div
        style={{
          flex: 1,
          overflowY: 'auto',
          padding: '24px',
          display: 'flex',
          flexDirection: 'column',
          gap: '20px',
        }}
      >
        {/* PROMINENT STOPPED / WAITING DIAGNOSTIC CARD */}
        {isWaiting ? (
          <div
            style={{
              background: 'rgba(239, 68, 68, 0.1)',
              border: '1px solid #ef4444',
              borderRadius: 'var(--radius-md)',
              padding: '16px',
              position: 'relative',
              overflow: 'hidden',
            }}
          >
            <div
              style={{
                display: 'flex',
                alignItems: 'center',
                gap: '8px',
                color: '#ef4444',
                fontWeight: 700,
                fontSize: '0.9rem',
                marginBottom: '8px',
              }}
            >
              <ShieldAlert size={18} />
              <span>WHY IS THIS TRAIN STOPPED?</span>
            </div>

            <div
              style={{
                fontSize: '0.85rem',
                color: 'var(--text-main)',
                lineHeight: 1.5,
                background: 'rgba(0, 0, 0, 0.25)',
                padding: '10px 12px',
                borderRadius: 'var(--radius-sm)',
                borderLeft: '3px solid #ef4444',
                fontFamily: 'var(--font-mono)',
              }}
            >
              <div style={{ color: '#ef4444', fontWeight: 800, marginBottom: '4px' }}>
                Stopped because: {stoppedReason}
              </div>
              {controllingSignal && (
                <div style={{ fontSize: '0.72rem', color: 'var(--text-muted)' }}>
                  Controlling Signal: <strong>{controllingSignal.signalId}</strong> • Aspect: <span style={{ color: controllingSignal.aspect === 'RED' ? '#ef4444' : '#10b981', fontWeight: 700 }}>{controllingSignal.aspect}</span>
                </div>
              )}
            </div>

            <div
              style={{
                marginTop: '12px',
                display: 'grid',
                gridTemplateColumns: '1fr 1fr',
                gap: '8px',
                fontSize: '0.75rem',
              }}
            >
              <div style={{ color: 'var(--text-muted)' }}>
                Target Platform:{' '}
                <strong style={{ color: 'var(--text-main)' }}>
                  {train.assignedPlatform ? `Platform ${train.assignedPlatform}${train.targetStationCode ? ` (${train.targetStationCode})` : ''}` : 'None'}
                </strong>
              </div>
              <div style={{ color: 'var(--text-muted)' }}>
                Current Block:{' '}
                <strong style={{ color: 'var(--text-main)', fontFamily: 'var(--font-mono)' }}>
                  {train.currentBlockId || 'Station Area'}
                </strong>
              </div>
            </div>

            <div
              style={{
                marginTop: '10px',
                fontSize: '0.75rem',
                color: 'var(--color-warning)',
                display: 'flex',
                alignItems: 'center',
                gap: '6px',
              }}
            >
              <Clock size={13} />
              <span>
                Delay: +{train.delayMinutes} min &bull; Awaiting signal clearance or dynamic reallocation
              </span>
            </div>
          </div>
        ) : (
          <div
            style={{
              background: 'rgba(16, 185, 129, 0.1)',
              border: '1px solid #10b981',
              borderRadius: 'var(--radius-md)',
              padding: '14px 16px',
              display: 'flex',
              alignItems: 'center',
              gap: '12px',
            }}
          >
            <CheckCircle2 size={24} color="#10b981" />
            <div>
              <div style={{ fontWeight: 700, fontSize: '0.85rem', color: '#10b981' }}>
                Route Currently Clear
              </div>
              <div style={{ fontSize: '0.75rem', color: 'var(--text-muted)' }}>
                Train is proceeding under active signal authority with no blocking conflicts.
              </div>
            </div>
          </div>
        )}

        {/* SECTION 1: TRAIN METADATA */}
        <div
          className="surface-card"
          style={{
            padding: '16px',
          }}
        >
          <div
            style={{
              fontSize: '0.75rem',
              fontWeight: 700,
              textTransform: 'uppercase',
              letterSpacing: '0.05em',
              color: 'var(--text-dim)',
              marginBottom: '12px',
              display: 'flex',
              alignItems: 'center',
              gap: '6px',
            }}
          >
            <Layers size={14} />
            <span>Train Specification</span>
          </div>

          <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '12px', fontSize: '0.8rem' }}>
            <div>
              <div style={{ color: 'var(--text-dim)', fontSize: '0.7rem' }}>Train Number</div>
              <div style={{ fontFamily: 'var(--font-mono)', fontWeight: 700 }}>{train.trainNumber}</div>
            </div>
            <div>
              <div style={{ color: 'var(--text-dim)', fontSize: '0.7rem' }}>Train Type</div>
              <div style={{ fontWeight: 600 }}>{train.trainType}</div>
            </div>
            <div>
              <div style={{ color: 'var(--text-dim)', fontSize: '0.7rem' }}>Operational Status</div>
              <div style={{ fontWeight: 600 }}>{train.status}</div>
            </div>
            <div>
              <div style={{ color: 'var(--text-dim)', fontSize: '0.7rem' }}>Accumulated Delay</div>
              <div
                style={{
                  fontWeight: 700,
                  color: train.delayMinutes > 0 ? 'var(--color-danger)' : 'var(--color-success)',
                }}
              >
                {train.delayMinutes > 0 ? `+${train.delayMinutes} mins` : 'On Time'}
              </div>
            </div>
          </div>
        </div>

        {/* SECTION 2: POSITION & ROUTE */}
        <div
          className="surface-card"
          style={{
            padding: '16px',
          }}
        >
          <div
            style={{
              fontSize: '0.75rem',
              fontWeight: 700,
              textTransform: 'uppercase',
              letterSpacing: '0.05em',
              color: 'var(--text-dim)',
              marginBottom: '12px',
              display: 'flex',
              alignItems: 'center',
              gap: '6px',
            }}
          >
            <MapPin size={14} />
            <span>Position & Route Progress</span>
          </div>

          <div style={{ display: 'flex', flexDirection: 'column', gap: '10px', fontSize: '0.8rem' }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
              <span style={{ color: 'var(--text-muted)' }}>Location:</span>
              <span style={{ fontFamily: 'var(--font-mono)', fontWeight: 700 }}>
                {formatTrainLocation(train)}
              </span>
            </div>

            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
              <span style={{ color: 'var(--text-muted)' }}>Next Stop:</span>
              <span style={{ fontFamily: 'var(--font-mono)', fontWeight: 800, color: '#22d3ee', fontSize: '0.85rem' }}>
                {formatNextStop(train)}
              </span>
            </div>

            {/* Dwell countdown - ONLY when actually stopped at platform */}
            {train.status === 'AT_PLATFORM' && (
              <div
                style={{
                  background: 'rgba(52, 211, 153, 0.12)',
                  border: '1px solid #10b981',
                  borderRadius: '6px',
                  padding: '8px 10px',
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'space-between',
                }}
              >
                <div style={{ display: 'flex', alignItems: 'center', gap: '6px', color: '#10b981', fontWeight: 700, fontSize: '0.8rem' }}>
                  <Clock size={14} />
                  <span>PLATFORM DWELL COUNTDOWN:</span>
                </div>
                <span style={{ fontFamily: 'var(--font-mono)', fontWeight: 800, color: '#10b981', fontSize: '0.9rem' }}>
                  {train.actualDeparture
                    ? `${Math.max(0, Math.round((new Date(train.actualDeparture).getTime() - Date.now()) / 1000))}s remaining`
                    : 'Stopped at platform'}
                </span>
              </div>
            )}

            {/* STOPS AT */}
            {train.stopStations && train.stopStations.length > 0 && (
              <div style={{ display: 'flex', flexDirection: 'column', gap: '4px', background: 'rgba(255, 255, 255, 0.03)', padding: '8px', borderRadius: '6px', border: '1px solid rgba(255, 255, 255, 0.06)' }}>
                <span style={{ color: 'var(--text-muted)', fontSize: '0.72rem', fontWeight: 600 }}>Stops at:</span>
                <div style={{ display: 'flex', flexWrap: 'wrap', gap: '4px' }}>
                  {train.stopStations.map((stn) => {
                    const isNext = Boolean(train.nextStopStationCode && stn === train.nextStopStationCode);
                    return (
                      <span
                        key={stn}
                        style={{
                          fontFamily: 'var(--font-mono)',
                          fontSize: '0.7rem',
                          fontWeight: 700,
                          padding: '1px 5px',
                          borderRadius: '3px',
                          background: isNext ? 'rgba(34, 211, 238, 0.25)' : 'rgba(255, 255, 255, 0.06)',
                          color: isNext ? '#22d3ee' : '#cbd5e1',
                          border: isNext ? '1px solid #22d3ee' : '1px solid rgba(255, 255, 255, 0.1)',
                        }}
                      >
                        {stn}
                      </span>
                    );
                  })}
                </div>
              </div>
            )}

            {/* SKIPS */}
            {train.skipStations && train.skipStations.length > 0 && (
              <div style={{ display: 'flex', flexDirection: 'column', gap: '4px', background: 'rgba(255, 255, 255, 0.02)', padding: '8px', borderRadius: '6px', border: '1px solid rgba(255, 255, 255, 0.04)' }}>
                <span style={{ color: 'var(--text-dim)', fontSize: '0.72rem', fontWeight: 600 }}>Skips:</span>
                <div style={{ fontSize: '0.72rem', color: '#94a3b8', fontStyle: 'italic', lineHeight: 1.4 }}>
                  {train.skipStations.join(', ')}
                </div>
              </div>
            )}

            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
              <span style={{ color: 'var(--text-muted)' }}>Direction Corridor:</span>
              <span
                style={{
                  fontFamily: 'var(--font-mono)',
                  background: 'var(--glass-bg)',
                  border: `1px solid ${isUp ? 'rgba(16, 185, 129, 0.4)' : 'rgba(249, 115, 22, 0.4)'}`,
                  padding: '2px 8px',
                  borderRadius: '4px',
                  fontSize: '0.75rem',
                  display: 'flex',
                  alignItems: 'center',
                  gap: '4px',
                  color: dirColor,
                  fontWeight: 700,
                }}
              >
                <Navigation size={12} />
                {dirBadge} ({isUp ? 'Southbound to MMCT' : 'Northbound to VR'})
              </span>
            </div>

            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
              <span style={{ color: 'var(--text-muted)' }}>Occupying Track Block:</span>
              <span
                style={{
                  fontFamily: 'var(--font-mono)',
                  color: 'var(--color-primary)',
                  fontWeight: 600,
                  fontSize: '0.8rem',
                }}
              >
                {train.currentBlockId || 'Unassigned / Station Berth'}
              </span>
            </div>

            {/* Live Block Progress & Speed */}
            <div style={{ display: 'flex', flexDirection: 'column', gap: '6px', background: 'var(--glass-bg)', border: '1px solid var(--glass-border)', padding: '8px 10px', borderRadius: 'var(--radius-sm)' }}>
              <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: '0.75rem' }}>
                <span style={{ color: 'var(--text-muted)' }}>Current Speed:</span>
                <span style={{ fontFamily: 'var(--font-mono)', fontWeight: 700, color: (train.speedKmH ?? 0) > 0 ? '#10b981' : '#ef4444' }}>
                  {train.speedKmH ?? 0} km/h
                </span>
              </div>
              <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: '0.75rem' }}>
                <span style={{ color: 'var(--text-muted)' }}>Block Traversal Progress:</span>
                <span style={{ fontFamily: 'var(--font-mono)', fontWeight: 700, color: 'var(--color-primary)' }}>
                  {Math.round(train.progressPercent ?? 0)}%
                </span>
              </div>
              <div style={{ width: '100%', height: '4px', background: 'var(--glass-border)', borderRadius: '2px', overflow: 'hidden' }}>
                <div
                  style={{
                    width: `${Math.min(100, Math.max(0, train.progressPercent ?? 0))}%`,
                    height: '100%',
                    background: isWaiting ? '#ef4444' : '#0ea5e9',
                    transition: 'width 0.3s ease',
                  }}
                />
              </div>
            </div>

            {currentBlock && (
              <div
                style={{
                  fontSize: '0.72rem',
                  color: 'var(--text-dim)',
                  background: 'var(--glass-bg)',
                  border: '1px solid var(--glass-border)',
                  padding: '6px 10px',
                  borderRadius: 'var(--radius-sm)',
                }}
              >
                Block Type: {currentBlock.blockType} &bull; Line: {currentBlock.lineType} &bull;{' '}
                {currentBlock.closed ? '⛔ CLOSED' : 'Active Track'}
              </div>
            )}
          </div>
        </div>

        {/* SECTION 3: CONTROLLING SIGNAL & ASPECT */}
        <div
          className="surface-card"
          style={{
            padding: '16px',
          }}
        >
          <div
            style={{
              fontSize: '0.75rem',
              fontWeight: 700,
              textTransform: 'uppercase',
              letterSpacing: '0.05em',
              color: 'var(--text-dim)',
              marginBottom: '12px',
              display: 'flex',
              alignItems: 'center',
              gap: '6px',
            }}
          >
            <Radio size={14} />
            <span>Controlling Signal Interlock</span>
          </div>

          <div style={{ display: 'flex', flexDirection: 'column', gap: '10px', fontSize: '0.8rem' }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
              <span style={{ color: 'var(--text-muted)' }}>Controlling Signal:</span>
              <span style={{ fontFamily: 'var(--font-mono)', fontWeight: 700 }}>
                {train.nextSignalId || controllingSignal?.signalId || 'SIG-AUTO'}
              </span>
            </div>

            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
              <span style={{ color: 'var(--text-muted)' }}>Current Aspect:</span>
              <span
                style={{
                  fontFamily: 'var(--font-mono)',
                  fontWeight: 700,
                  fontSize: '0.75rem',
                  padding: '3px 8px',
                  borderRadius: 'var(--radius-sm)',
                  color: aspectInfo.color,
                  background: aspectInfo.bg,
                  border: `1px solid ${aspectInfo.border}`,
                  display: 'flex',
                  alignItems: 'center',
                  gap: '6px',
                }}
              >
                <span>{aspectInfo.icon}</span>
                <span>{aspectInfo.label}</span>
              </span>
            </div>

            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
              <span style={{ color: 'var(--text-muted)' }}>Protected Block:</span>
              <span style={{ fontFamily: 'var(--font-mono)', fontSize: '0.75rem' }}>
                {controllingSignal?.protectedBlockId || train.currentBlockId || 'Approaching Section'}
              </span>
            </div>

            {controllingSignal?.reason && (
              <div
                style={{
                  marginTop: '4px',
                  fontSize: '0.72rem',
                  color: 'var(--text-muted)',
                  background: 'var(--glass-bg)',
                  border: '1px solid var(--glass-border)',
                  padding: '8px 10px',
                  borderRadius: 'var(--radius-sm)',
                  borderLeft: `2px solid ${aspectInfo.color}`,
                }}
              >
                <strong>Signal Logic Reason:</strong> {controllingSignal.reason}
              </div>
            )}
          </div>
        </div>

        {/* SECTION 4: PLATFORM ASSIGNMENT */}
        <div
          className="surface-card"
          style={{
            padding: '16px',
          }}
        >
          <div
            style={{
              fontSize: '0.75rem',
              fontWeight: 700,
              textTransform: 'uppercase',
              letterSpacing: '0.05em',
              color: 'var(--text-dim)',
              marginBottom: '10px',
            }}
          >
            Platform Assignment
          </div>
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', fontSize: '0.8rem' }}>
            <span style={{ color: 'var(--text-muted)' }}>Assigned Platform Berth:</span>
            <span
              style={{
                fontFamily: 'var(--font-mono)',
                fontWeight: 800,
                color: 'var(--color-primary)',
                background: 'rgba(56, 189, 248, 0.1)',
                padding: '2px 10px',
                borderRadius: '4px',
                border: '1px solid var(--border-glow)',
              }}
            >
              {train.assignedPlatform ? `Platform ${train.assignedPlatform}${train.targetStationCode ? ` (${train.targetStationCode})` : ''}` : 'None Assigned'}
            </span>
          </div>
        </div>
      </div>
    </div>
  );
};
