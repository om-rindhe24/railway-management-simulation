import React, { useState } from 'react';
import { Platform, Train, ReallocationResult } from '../types';
import { formatTrainLocation, formatNextStop } from '../utils/trainFormatters';
import { getPlatformBerthOccupant, getPlatformWaitingTrains } from '../utils/platformMatching';
import { CheckCircle, AlertOctagon, XCircle, Train as TrainIcon, Sliders, ShieldAlert } from 'lucide-react';

interface PlatformViewProps {
  selectedStation: string;
  platforms: Platform[];
  trains: Train[];
  onClosePlatform: (platformId: number, reason: string) => Promise<ReallocationResult>;
  onOpenPlatform: (platformId: number) => Promise<void>;
}

export const PlatformView: React.FC<PlatformViewProps> = ({
  selectedStation,
  platforms,
  trains,
  onClosePlatform,
  onOpenPlatform,
}) => {
  const [closingPlatformId, setClosingPlatformId] = useState<number | null>(null);
  const [closureReason, setClosureReason] = useState('Track maintenance and signal testing');
  const [lastReallocationResult, setLastReallocationResult] = useState<ReallocationResult | null>(null);

  const handleConfirmClose = async () => {
    if (closingPlatformId == null) return;
    try {
      const result = await onClosePlatform(closingPlatformId, closureReason);
      setLastReallocationResult(result);
      setClosingPlatformId(null);
    } catch (err: any) {
      alert('Error closing platform: ' + err.message);
    }
  };

  return (
    <div className="page-container">
      <div className="card-header" style={{ marginBottom: '20px' }}>
        <div>
          <h2 style={{ fontSize: '1.4rem', fontWeight: 800 }}>
            Station Platforms — {selectedStation}
          </h2>
          <p style={{ color: 'var(--text-muted)', fontSize: '0.85rem' }}>
            Real-time occupancy and operational status. Close any platform to simulate dynamic eviction and algorithmic reallocation.
          </p>
        </div>
      </div>

      {/* Reallocation Impact Alert banner after closure */}
      {lastReallocationResult && (
        <div
          style={{
            background: 'rgba(56, 189, 248, 0.1)',
            border: '1px solid var(--color-primary)',
            borderRadius: 'var(--radius-md)',
            padding: '16px 20px',
            marginBottom: '24px',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'space-between',
          }}
        >
          <div style={{ display: 'flex', alignItems: 'center', gap: '12px' }}>
            <ShieldAlert size={22} color="var(--color-primary)" />
            <div>
              <div style={{ fontWeight: 700, color: 'var(--color-primary)' }}>
                Dynamic Reallocation Result
              </div>
              <div style={{ fontSize: '0.85rem', color: 'var(--text-muted)' }}>
                {lastReallocationResult.summaryMessage}
              </div>
            </div>
          </div>
          <button
            className="btn btn-secondary"
            style={{ padding: '6px 12px', fontSize: '0.75rem' }}
            onClick={() => setLastReallocationResult(null)}
          >
            Dismiss
          </button>
        </div>
      )}

      {/* Platform Cards Grid */}
      <div
        style={{
          display: 'grid',
          gridTemplateColumns: 'repeat(auto-fill, minmax(320px, 1fr))',
          gap: '20px',
        }}
      >
        {platforms.map((platform) => {
          const currentTrain = getPlatformBerthOccupant(platform, selectedStation, trains);
          const isOccupied = Boolean(currentTrain);
          const waitingTrains = getPlatformWaitingTrains(platform, selectedStation, trains, currentTrain?.trainNumber, 2);

          return (
            <div
              key={platform.platformId}
              className="glass-panel"
              style={{
                borderColor: platform.isClosed
                  ? 'rgba(239, 68, 68, 0.4)'
                  : isOccupied
                  ? 'rgba(245, 158, 11, 0.4)'
                  : undefined,
                padding: '20px',
                display: 'flex',
                flexDirection: 'column',
                justifyContent: 'space-between',
              }}
            >
              <div>
                {/* Platform Header */}
                <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: '14px' }}>
                  <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
                    <div
                      className="surface-card"
                      style={{
                        color: 'var(--color-primary)',
                        fontWeight: 800,
                        fontSize: '1.25rem',
                        fontFamily: 'var(--font-mono)',
                        padding: '6px 14px',
                        borderRadius: 'var(--radius-md)',
                      }}
                    >
                      P{platform.platformNumber}
                    </div>
                    <div>
                      <div style={{ fontWeight: 700, fontSize: '0.95rem' }}>Platform {platform.platformNumber}</div>
                      <div style={{ fontSize: '0.72rem', color: 'var(--text-dim)', textTransform: 'uppercase' }}>
                        {platform.lineType.replace('_', ' ')}
                      </div>
                    </div>
                  </div>

                  {platform.isClosed ? (
                    <span className="badge badge-danger">
                      <XCircle size={12} /> CLOSED
                    </span>
                  ) : isOccupied ? (
                    <span className="badge badge-warning">
                      <AlertOctagon size={12} /> OCCUPIED
                    </span>
                  ) : (
                    <span className="badge badge-success">
                      <CheckCircle size={12} /> AVAILABLE
                    </span>
                  )}
                </div>

                {/* Platform Signals: Home & Starter */}
                <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '8px', marginBottom: '12px' }}>
                  <div style={{
                    background: 'rgba(15, 23, 42, 0.7)',
                    border: `1px solid ${platform.homeSignalAspect === 'RED' ? '#ef4444' : platform.homeSignalAspect === 'YELLOW' ? '#f59e0b' : '#10b981'}40`,
                    borderRadius: 'var(--radius-sm)',
                    padding: '6px 8px',
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'space-between',
                  }}>
                    <div style={{ display: 'flex', alignItems: 'center', gap: '5px' }}>
                      <span style={{ fontSize: '0.62rem', fontWeight: 800, color: 'var(--text-dim)', textTransform: 'uppercase' }}>HOME</span>
                      <span style={{ fontFamily: 'var(--font-mono)', fontSize: '0.65rem', color: '#94a3b8' }}>
                        P{platform.platformNumber}
                      </span>
                    </div>
                    <span style={{
                      fontFamily: 'var(--font-mono)',
                      fontSize: '0.68rem',
                      fontWeight: 800,
                      color: platform.homeSignalAspect === 'RED' ? '#ef4444' : platform.homeSignalAspect === 'YELLOW' ? '#f59e0b' : '#10b981',
                    }}>
                      ● {platform.homeSignalAspect || (isOccupied || platform.isClosed ? 'RED' : 'GREEN')}
                    </span>
                  </div>

                  <div style={{
                    background: 'rgba(15, 23, 42, 0.7)',
                    border: `1px solid ${platform.starterSignalAspect === 'RED' ? '#ef4444' : platform.starterSignalAspect === 'YELLOW' ? '#f59e0b' : '#10b981'}40`,
                    borderRadius: 'var(--radius-sm)',
                    padding: '6px 8px',
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'space-between',
                  }}>
                    <div style={{ display: 'flex', alignItems: 'center', gap: '5px' }}>
                      <span style={{ fontSize: '0.62rem', fontWeight: 800, color: 'var(--text-dim)', textTransform: 'uppercase' }}>STARTER</span>
                      <span style={{ fontFamily: 'var(--font-mono)', fontSize: '0.65rem', color: '#94a3b8' }}>
                        P{platform.platformNumber}
                      </span>
                    </div>
                    <span style={{
                      fontFamily: 'var(--font-mono)',
                      fontSize: '0.68rem',
                      fontWeight: 800,
                      color: platform.starterSignalAspect === 'RED' ? '#ef4444' : platform.starterSignalAspect === 'YELLOW' ? '#f59e0b' : '#10b981',
                    }}>
                      ● {platform.starterSignalAspect || (isOccupied && (platform.dwellSecondsRemaining ?? 0) === 0 ? 'GREEN' : 'RED')}
                    </span>
                  </div>
                </div>

                {/* Occupancy Status Content */}
                <div
                  className="surface-card"
                  style={{
                    borderRadius: 'var(--radius-sm)',
                    padding: '12px 14px',
                    marginBottom: '14px',
                    minHeight: '80px',
                  }}
                >
                  {platform.isClosed ? (
                    <div>
                      <div style={{ color: 'var(--color-danger)', fontWeight: 600, fontSize: '0.85rem' }}>
                        Platform Temporarily Suspended
                      </div>
                      <div style={{ fontSize: '0.75rem', color: 'var(--text-muted)', marginTop: '4px' }}>
                        Reason: {platform.closureReason || 'Scheduled track repair'}
                      </div>
                    </div>
                  ) : isOccupied && currentTrain ? (
                    <div>
                      <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
                        <div style={{ display: 'flex', alignItems: 'center', gap: '6px', color: 'var(--color-warning)', fontWeight: 700, fontSize: '0.85rem' }}>
                          <TrainIcon size={14} />
                          <span>Docked: {currentTrain.trainNumber}</span>
                        </div>
                        {platform.dwellSecondsRemaining != null && (
                          <span style={{ fontSize: '0.72rem', color: '#38bdf8', fontFamily: 'var(--font-mono)', fontWeight: 700 }}>
                            ⏱ {platform.dwellSecondsRemaining}s left
                          </span>
                        )}
                      </div>
                      <div style={{ fontSize: '0.78rem', color: 'var(--text-main)', marginTop: '4px' }}>
                        {currentTrain.trainName || 'Suburban Train Service'}
                      </div>
                      <div style={{ fontSize: '0.72rem', color: '#94a3b8', marginTop: '3px', display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                        <span>Location: <strong style={{ color: '#f8fafc' }}>{formatTrainLocation(currentTrain)}</strong></span>
                        <span style={{ color: '#38bdf8', fontFamily: 'var(--font-mono)', fontWeight: 700 }}>Next: {formatNextStop(currentTrain)}</span>
                      </div>
                      {waitingTrains.length > 0 && (
                        <div style={{ fontSize: '0.72rem', color: '#eab308', marginTop: '6px', display: 'flex', alignItems: 'center', gap: '4px', flexWrap: 'wrap' }}>
                          <span>Next Waiting:</span>
                          {waitingTrains.map((wt) => (
                            <span key={wt.trainNumber} style={{ fontFamily: 'var(--font-mono)', fontWeight: 700, background: 'rgba(234, 179, 8, 0.1)', padding: '1px 6px', borderRadius: '3px', border: '1px solid rgba(234, 179, 8, 0.3)' }}>
                              {wt.trainNumber}
                            </span>
                          ))}
                        </div>
                      )}
                    </div>
                  ) : (
                    <div>
                      <div style={{ color: 'var(--text-dim)', fontSize: '0.82rem' }}>
                        Track clear. Ready for incoming arrival.
                      </div>
                      {waitingTrains.length > 0 && (
                        <div style={{ fontSize: '0.72rem', color: '#38bdf8', marginTop: '6px', display: 'flex', alignItems: 'center', gap: '4px', flexWrap: 'wrap' }}>
                          <span>Next Waiting:</span>
                          {waitingTrains.map((wt) => (
                            <span key={wt.trainNumber} style={{ fontFamily: 'var(--font-mono)', fontWeight: 700, background: 'rgba(56, 189, 248, 0.1)', padding: '1px 6px', borderRadius: '3px', border: '1px solid rgba(56, 189, 248, 0.3)' }}>
                              {wt.trainNumber}
                            </span>
                          ))}
                        </div>
                      )}
                    </div>
                  )}
                </div>

                {/* Supported Categories Pills */}
                <div style={{ display: 'flex', flexWrap: 'wrap', gap: '5px', marginBottom: '16px' }}>
                  {platform.supportedTrainTypes.map((type) => (
                    <span
                      key={type}
                      className="surface-card"
                      style={{
                        color: 'var(--text-muted)',
                        fontSize: '0.68rem',
                        padding: '2px 8px',
                        borderRadius: 'var(--radius-sm)',
                      }}
                    >
                      {type}
                    </span>
                  ))}
                </div>
              </div>

              {/* Action Buttons: Close or Open Platform */}
              <div>
                {platform.isClosed ? (
                  <button
                    className="btn btn-secondary"
                    style={{ width: '100%', color: 'var(--color-success)', borderColor: 'rgba(16, 185, 129, 0.4)' }}
                    onClick={() => onOpenPlatform(platform.platformId)}
                  >
                    <CheckCircle size={14} />
                    <span>Reopen Platform</span>
                  </button>
                ) : (
                  <button
                    className="btn btn-secondary"
                    style={{ width: '100%', color: 'var(--color-danger)', borderColor: 'rgba(239, 68, 68, 0.3)' }}
                    onClick={() => setClosingPlatformId(platform.platformId)}
                  >
                    <Sliders size={14} />
                    <span>Close Platform (Simulate Reallocation)</span>
                  </button>
                )}
              </div>
            </div>
          );
        })}
      </div>

      {/* Platform Closure Confirmation Modal */}
      {closingPlatformId != null && (
        <div className="modal-overlay">
          <div className="modal-content">
            <h3 style={{ fontSize: '1.2rem', fontWeight: 800, marginBottom: '10px' }}>
              Confirm Platform Closure Simulation
            </h3>
            <p style={{ color: 'var(--text-muted)', fontSize: '0.85rem', marginBottom: '16px' }}>
              Closing this platform will trigger the <strong>Dynamic Reallocation Engine</strong>.
              All scheduled trains on this platform will be evacuated and algorithmically reassigned to compatible open platforms.
            </p>

            <div style={{ marginBottom: '20px' }}>
              <label style={{ display: 'block', fontSize: '0.75rem', fontWeight: 600, textTransform: 'uppercase', color: 'var(--text-dim)', marginBottom: '6px' }}>
                Closure Reason
              </label>
              <input
                type="text"
                value={closureReason}
                onChange={(e) => setClosureReason(e.target.value)}
                style={{
                  width: '100%',
                  background: 'var(--glass-bg)',
                  border: '1px solid var(--glass-border)',
                  color: 'var(--text-main)',
                  padding: '10px 14px',
                  borderRadius: 'var(--radius-sm)',
                  outline: 'none',
                }}
              />
            </div>

            <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '12px' }}>
              <button className="btn btn-secondary" onClick={() => setClosingPlatformId(null)}>
                Cancel
              </button>
              <button className="btn btn-danger" onClick={handleConfirmClose}>
                Confirm & Reallocate
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};
