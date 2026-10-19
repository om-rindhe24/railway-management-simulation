import React, { useState } from 'react';
import { Platform, Train, PlatformAllocation } from '../types';
import { Clock, Info, AlertTriangle, ShieldCheck, X } from 'lucide-react';

interface OccupancyTimelineProps {
  selectedStation: string;
  platforms: Platform[];
  trains: Train[];
  allocations: PlatformAllocation[];
  simulationTime: string;
}

export const OccupancyTimeline: React.FC<OccupancyTimelineProps> = ({
  selectedStation,
  platforms,
  trains,
  allocations,
  simulationTime,
}) => {
  const [selectedTrain, setSelectedTrain] = useState<Train | null>(null);

  // Simulation time window: Dynamically anchored to simulation time (15 mins prior, 90 mins forward)
  const baseSimDate = new Date(simulationTime || '2026-09-18T10:00:00');
  // Anchor to 15 minutes before the current simulation hour/minute for context
  const windowStart = new Date(baseSimDate.getTime() - 15 * 60000);
  const totalWindowMinutes = 90;

  // Time slots in 10-minute increments
  const timeSlots = [];
  for (let i = 0; i <= totalWindowMinutes; i += 10) {
    const slotDate = new Date(windowStart.getTime() + i * 60000);
    timeSlots.push(slotDate.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit', hour12: false }));
  }

  // Helper to calculate pixel/percentage offset from windowStart
  const getOffsetPercent = (dateStr?: string) => {
    if (!dateStr) return 0;
    try {
      const d = new Date(dateStr);
      const diffMinutes = (d.getTime() - windowStart.getTime()) / 60000;
      return Math.max(0, Math.min(100, (diffMinutes / totalWindowMinutes) * 100));
    } catch {
      return 0;
    }
  };

  const getWidthPercent = (startStr?: string, endStr?: string) => {
    if (!startStr || !endStr) return 5;
    try {
      const s = new Date(startStr);
      const e = new Date(endStr);
      const duration = Math.max(3, (e.getTime() - s.getTime()) / 60000);
      return Math.max(2, Math.min(100, (duration / totalWindowMinutes) * 100));
    } catch {
      return 5;
    }
  };

  const getCategoryColor = (type?: string) => {
    switch (type) {
      case 'SPECIAL': return 'linear-gradient(135deg, #ef4444, #dc2626)';
      case 'PREMIUM': return 'linear-gradient(135deg, #f59e0b, #d97706)';
      case 'SUPERFAST': return 'linear-gradient(135deg, #a855f7, #9333ea)';
      case 'EXPRESS_MAIL': return 'linear-gradient(135deg, #3b82f6, #2563eb)';
      case 'LOCAL': return 'linear-gradient(135deg, #10b981, #059669)';
      default: return 'linear-gradient(135deg, #64748b, #475569)';
    }
  };

  const simTimeOffset = getOffsetPercent(simulationTime);

  // Compute total occupied/allocated slots across all platforms at this station
  let totalOccupancyCount = 0;
  const platformOccupancyMap: { [platformId: number]: Array<{ train: Partial<Train>; start: string; end: string }> } = {};

  platforms.forEach((p) => {
    platformOccupancyMap[p.platformId] = [];
    
    // 1. From allocations
    const allocs = allocations.filter((a) => a.platformId === p.platformId || (a.stationCode === selectedStation && a.platformNumber === p.platformNumber));
    allocs.forEach((a) => {
      const matchedTrain = trains.find((t) => t.trainId === a.trainId || t.trainNumber === a.trainNumber);
      platformOccupancyMap[p.platformId].push({
        train: matchedTrain || {
          trainId: a.trainId,
          trainNumber: a.trainNumber,
          trainName: a.trainName,
          trainType: 'EXPRESS_MAIL',
          delayMinutes: a.delayMinutes || 0,
          assignedPlatformNumber: a.platformNumber,
          scheduledArrival: a.startTime,
          scheduledDeparture: a.endTime,
          actualArrival: a.startTime,
          actualDeparture: a.endTime,
          status: 'SCHEDULED',
        },
        start: a.startTime,
        end: a.endTime,
      });
    });

    // 2. From currently occupied platform train
    if (p.isOccupied && p.currentTrainNumber) {
      const dockedTrain = trains.find((t) => t.trainNumber === p.currentTrainNumber);
      const alreadyAdded = platformOccupancyMap[p.platformId].some((item) => item.train.trainNumber === p.currentTrainNumber);
      if (!alreadyAdded) {
        platformOccupancyMap[p.platformId].push({
          train: dockedTrain || {
            trainId: p.currentTrainId || 9999,
            trainNumber: p.currentTrainNumber,
            trainName: 'Active Docked Service',
            trainType: 'LOCAL',
            delayMinutes: 0,
            assignedPlatformNumber: p.platformNumber,
            status: 'AT_PLATFORM',
          },
          start: p.occupancyStart || simulationTime,
          end: p.occupancyEnd || new Date(new Date(simulationTime).getTime() + 60000).toISOString(),
        });
      }
    }

    // 3. From trains targeting this station and assigned to this platform
    const stationTrains = trains.filter(
      (t) => (t.assignedPlatformNumber === p.platformNumber || t.assignedPlatformId === p.platformId) &&
             (t.targetStationCode === selectedStation || t.currentStationCode === selectedStation)
    );
    stationTrains.forEach((st) => {
      const alreadyAdded = platformOccupancyMap[p.platformId].some((item) => item.train.trainId === st.trainId);
      if (!alreadyAdded) {
        platformOccupancyMap[p.platformId].push({
          train: st,
          start: st.actualArrival || st.scheduledArrival,
          end: st.actualDeparture || st.scheduledDeparture,
        });
      }
    });

    totalOccupancyCount += platformOccupancyMap[p.platformId].length;
  });

  return (
    <div className="page-container">
      <div className="card-header" style={{ marginBottom: '20px' }}>
        <div>
          <h2 style={{ fontSize: '1.4rem', fontWeight: 800 }}>
            Platform Occupancy Timeline (Gantt Interval View)
          </h2>
          <p style={{ color: 'var(--text-muted)', fontSize: '0.85rem' }}>
            Visual interval scheduling showing train platform occupation intervals [arr - buffer, dep + buffer] at Station {selectedStation}.
          </p>
        </div>

        <div style={{ display: 'flex', alignItems: 'center', gap: '16px', fontSize: '0.75rem' }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: '6px' }}>
            <span style={{ width: '12px', height: '12px', background: '#ef4444', borderRadius: '2px' }}></span>
            <span>Special / Emergency</span>
          </div>
          <div style={{ display: 'flex', alignItems: 'center', gap: '6px' }}>
            <span style={{ width: '12px', height: '12px', background: '#f59e0b', borderRadius: '2px' }}></span>
            <span>Premium</span>
          </div>
          <div style={{ display: 'flex', alignItems: 'center', gap: '6px' }}>
            <span style={{ width: '12px', height: '12px', background: '#a855f7', borderRadius: '2px' }}></span>
            <span>Superfast</span>
          </div>
          <div style={{ display: 'flex', alignItems: 'center', gap: '6px' }}>
            <span style={{ width: '12px', height: '12px', background: '#3b82f6', borderRadius: '2px' }}></span>
            <span>Express</span>
          </div>
          <div style={{ display: 'flex', alignItems: 'center', gap: '6px' }}>
            <span style={{ width: '12px', height: '12px', background: '#10b981', borderRadius: '2px' }}></span>
            <span>Local</span>
          </div>
        </div>
      </div>

      {/* Empty State Banner if no scheduled occupancy */}
      {totalOccupancyCount === 0 && (
        <div
          style={{
            background: 'rgba(56, 189, 248, 0.08)',
            border: '1px solid rgba(56, 189, 248, 0.3)',
            borderRadius: 'var(--radius-md)',
            padding: '16px 20px',
            marginBottom: '20px',
            display: 'flex',
            alignItems: 'center',
            gap: '14px',
          }}
        >
          <Info size={24} style={{ color: 'var(--color-primary)', flexShrink: 0 }} />
          <div>
            <div style={{ fontWeight: 700, fontSize: '0.95rem', color: 'var(--text-main)' }}>
              No scheduled/allocated platform occupancy in the selected time range.
            </div>
            <div style={{ fontSize: '0.8rem', color: 'var(--text-muted)', marginTop: '2px' }}>
              Station <strong>{selectedStation}</strong> currently has no active train allocations within this simulation time slice.
              Major corridor junction stations with scheduled traffic include <strong>BVI (Borivali)</strong>, <strong>AND (Andheri)</strong>, <strong>DDR (Dadar)</strong>, <strong>MMCT (Mumbai Central)</strong>, and <strong>VR (Virar)</strong>.
            </div>
          </div>
        </div>
      )}

      {/* Timeline Scrollable Grid */}
      <div className="timeline-scroll-container glass-panel" style={{ border: '1px solid rgba(255, 255, 255, 0.12)', background: 'rgba(255, 255, 255, 0.04)' }}>
        {/* Time Header Row */}
        <div className="timeline-header">
          <div className="timeline-platform-label-col">Platform</div>
          <div className="timeline-time-slots" style={{ position: 'relative' }}>
            {timeSlots.map((time, idx) => (
              <div key={idx} className="timeline-slot" style={{ flex: 1 }}>
                {time}
              </div>
            ))}
          </div>
        </div>

        {/* Platform Lanes */}
        <div style={{ position: 'relative' }}>
          {/* Current Simulation Clock Vertical Needle */}
          {simTimeOffset > 0 && simTimeOffset < 100 && (
            <div
              style={{
                position: 'absolute',
                top: 0,
                bottom: 0,
                left: `calc(140px + (100% - 140px) * ${simTimeOffset / 100})`,
                width: '2px',
                background: '#38bdf8',
                boxShadow: '0 0 8px #38bdf8',
                zIndex: 30,
                pointerEvents: 'none',
              }}
            >
              <div
                style={{
                  position: 'absolute',
                  top: '-6px',
                  left: '-4px',
                  width: '10px',
                  height: '10px',
                  borderRadius: '50%',
                  background: '#38bdf8',
                }}
              />
            </div>
          )}

          {platforms.map((platform) => {
            const platformEntries = platformOccupancyMap[platform.platformId] || [];

            return (
              <div key={platform.platformId} className="timeline-row">
                {/* Platform Label */}
                <div className="timeline-platform-label-col" style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
                  <span style={{ fontWeight: 800, color: 'var(--color-primary)' }}>
                    P{platform.platformNumber}
                  </span>
                  <span style={{ fontSize: '0.65rem', color: 'var(--text-dim)' }}>
                    {platform.lineType.includes('FAST') ? 'FAST' : 'SLOW'}
                  </span>
                </div>

                {/* Platform Track Lane with Train Bars */}
                <div style={{ flex: 1, position: 'relative', height: '100%' }}>
                  {platform.isClosed ? (
                    <div
                      style={{
                        position: 'absolute',
                        inset: '4px',
                        background: 'repeating-linear-gradient(45deg, rgba(239, 68, 68, 0.1), rgba(239, 68, 68, 0.1) 10px, rgba(239, 68, 68, 0.2) 10px, rgba(239, 68, 68, 0.2) 20px)',
                        borderRadius: 'var(--radius-sm)',
                        display: 'flex',
                        alignItems: 'center',
                        justifyContent: 'center',
                        color: 'var(--color-danger)',
                        fontSize: '0.75rem',
                        fontWeight: 700,
                        letterSpacing: '1px',
                      }}
                    >
                      CLOSED — {platform.closureReason || 'MAINTENANCE'}
                    </div>
                  ) : (
                    platformEntries.map(({ train, start, end }) => {
                      const leftPercent = getOffsetPercent(start);
                      const widthPercent = getWidthPercent(start, end);

                      return (
                        <div
                          key={train.trainId || train.trainNumber}
                          className="timeline-bar"
                          style={{
                            left: `${leftPercent}%`,
                            width: `${widthPercent}%`,
                            background: getCategoryColor(train.trainType),
                            color: '#fff',
                          }}
                          onClick={() => setSelectedTrain(train as Train)}
                          title={`Click to inspect ${train.trainNumber}`}
                        >
                          <span style={{ overflow: 'hidden', textOverflow: 'ellipsis' }}>
                            {train.trainNumber} ({train.trainType ? train.trainType.substring(0, 4) : 'TRN'})
                          </span>
                          {(train.delayMinutes || 0) > 0 && (
                            <span style={{ background: '#000', color: '#f87171', padding: '1px 4px', borderRadius: '3px', fontSize: '0.65rem' }}>
                              +{train.delayMinutes}m
                            </span>
                          )}
                        </div>
                      );
                    })
                  )}
                </div>
              </div>
            );
          })}
        </div>
      </div>

      {/* Train Detail Inspection Modal */}
      {selectedTrain && (
        <div className="modal-overlay" onClick={() => setSelectedTrain(null)}>
          <div className="modal-content" onClick={(e) => e.stopPropagation()}>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '16px' }}>
              <h3 style={{ fontSize: '1.25rem', fontWeight: 800 }}>
                Train Interval Details — {selectedTrain.trainNumber}
              </h3>
              <button className="btn-icon" onClick={() => setSelectedTrain(null)}>
                <X size={18} />
              </button>
            </div>

            <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '14px', marginBottom: '20px' }}>
              <div>
                <span style={{ fontSize: '0.72rem', color: 'var(--text-dim)', textTransform: 'uppercase' }}>Service Name</span>
                <div style={{ fontWeight: 700, fontSize: '0.95rem' }}>{selectedTrain.trainName}</div>
              </div>
              <div>
                <span style={{ fontSize: '0.72rem', color: 'var(--text-dim)', textTransform: 'uppercase' }}>Category & Priority</span>
                <div>
                  <span className={`badge`} style={{ background: 'var(--bg-surface)' }}>
                    {selectedTrain.trainType} (Tier {selectedTrain.simulationPriority})
                  </span>
                </div>
              </div>
              <div>
                <span style={{ fontSize: '0.72rem', color: 'var(--text-dim)', textTransform: 'uppercase' }}>Scheduled Interval</span>
                <div style={{ fontFamily: 'var(--font-mono)', fontWeight: 600 }}>
                  {new Date(selectedTrain.scheduledArrival).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })} &rarr;{' '}
                  {new Date(selectedTrain.scheduledDeparture).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })}
                </div>
              </div>
              <div>
                <span style={{ fontSize: '0.72rem', color: 'var(--text-dim)', textTransform: 'uppercase' }}>Effective Interval</span>
                <div style={{ fontFamily: 'var(--font-mono)', fontWeight: 700, color: selectedTrain.delayMinutes > 0 ? 'var(--color-warning)' : 'var(--color-success)' }}>
                  {new Date(selectedTrain.actualArrival).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })} &rarr;{' '}
                  {new Date(selectedTrain.actualDeparture).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })}
                  {selectedTrain.delayMinutes > 0 && ` (+${selectedTrain.delayMinutes}m delay)`}
                </div>
              </div>
              <div>
                <span style={{ fontSize: '0.72rem', color: 'var(--text-dim)', textTransform: 'uppercase' }}>Assigned Platform</span>
                <div style={{ fontWeight: 800, color: 'var(--color-primary)', fontSize: '1.1rem' }}>
                  Platform {selectedTrain.assignedPlatformNumber || 'Unassigned'}
                </div>
              </div>
              <div>
                <span style={{ fontSize: '0.72rem', color: 'var(--text-dim)', textTransform: 'uppercase' }}>Route & Status</span>
                <div style={{ fontWeight: 600 }}>
                  {selectedTrain.sourceStation} &rarr; {selectedTrain.destinationStation} ({selectedTrain.status})
                </div>
              </div>
            </div>

            <div style={{ background: 'var(--bg-primary)', padding: '12px 16px', borderRadius: 'var(--radius-sm)', border: '1px solid var(--border-subtle)' }}>
              <div style={{ display: 'flex', alignItems: 'center', gap: '6px', fontSize: '0.75rem', color: 'var(--color-primary)', fontWeight: 700, marginBottom: '4px' }}>
                <ShieldCheck size={14} />
                <span>ALGORITHMIC PLATFORM ALLOCATION REASONING</span>
              </div>
              <p style={{ fontSize: '0.82rem', color: 'var(--text-muted)' }}>
                Platform selected via explainable multi-factor scoring. Evaluated compatibility, line alignment,
                idle spacing buffer, and 3-minute safety clearance without overlapping intervals.
              </p>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};
