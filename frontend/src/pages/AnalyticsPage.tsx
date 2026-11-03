import React, { useState } from 'react';
import { ShieldCheck, Clock, Layers, Activity, Zap, HelpCircle, ArrowUpRight, AlertTriangle } from 'lucide-react';
import { api } from '../services/api';

interface AnalyticsPageProps {
  kpis: Record<string, any>;
  onRefresh?: () => void;
}

interface StationUtilization {
  stationCode: string;
  stationName: string;
  utilizationPercent: number;
  occupiedMinutes: number;
  availableMinutes: number;
}

export const AnalyticsPage: React.FC<AnalyticsPageProps> = ({ kpis, onRefresh }) => {
  const [togglingPeak, setTogglingPeak] = useState<boolean>(false);
  const [localPeakHour, setLocalPeakHour] = useState<boolean | null>(null);
  const [controlError, setControlError] = useState<string | null>(null);

  const isPeakHour = localPeakHour !== null ? localPeakHour : Boolean(kpis?.isPeakHour);
  const categoryDist: Record<string, number> = kpis?.categoryDistribution || {};
  const maxCategoryCount = Math.max(...Object.values(categoryDist), 1);

  const busiestStations: StationUtilization[] = kpis?.busiestStations || [];
  const lineTypeUtilization: Record<string, number> = kpis?.lineTypeUtilization || {};

  const handleTogglePeakHour = async () => {
    try {
      setTogglingPeak(true);
      setControlError(null);
      const nextState = !isPeakHour;
      setLocalPeakHour(nextState);
      await api.setPeakHour(nextState);
      if (onRefresh) {
        onRefresh();
      }
    } catch (err: any) {
      console.error('Failed to toggle peak hour mode:', err);
      setLocalPeakHour(isPeakHour);
      setControlError(err?.message || 'Control request failed (HTTP Error)');
    } finally {
      setTogglingPeak(false);
    }
  };

  const lineTypeLabels: Record<string, { label: string; desc: string; color: string }> = {
    FAST_UP: { label: 'Fast Up (Northbound)', desc: 'Through express & fast local services', color: '#38bdf8' },
    FAST_DOWN: { label: 'Fast Down (Southbound)', desc: 'Terminal express & fast local services', color: '#0ea5e9' },
    SLOW_UP: { label: 'Slow Up (Northbound)', desc: 'All-station suburban stopping services', color: '#f59e0b' },
    SLOW_DOWN: { label: 'Slow Down (Southbound)', desc: 'All-station suburban stopping services', color: '#10b981' },
  };

  const historyMins = kpis?.historyMinutes ?? 0;
  const occupiedMins = kpis?.occupiedPlatformMinutes ?? 0;
  const availableMins = kpis?.availablePlatformMinutes ?? 0;
  const rollingPercent = availableMins > 0 ? (kpis?.rolling60MinUtilizationPercent ?? 0) : 0;

  const occupiedNow = kpis?.occupiedPlatforms || 0;
  const totalPlats = kpis?.totalPlatforms || 0;
  const closedPlats = kpis?.closedPlatforms || 0;
  const openPlats = Math.max(0, totalPlats - closedPlats);
  const snapshotPercent = kpis?.snapshotPlatformUtilizationPercent ?? (openPlats > 0 ? Math.round((occupiedNow / openPlats) * 1000) / 10 : 0);

  return (
    <div className="page-container">
      {/* Control Error Toast / Inline Banner */}
      {controlError && (
        <div
          role="alert"
          style={{
            background: 'rgba(239, 68, 68, 0.15)',
            border: '1px solid #ef4444',
            borderRadius: 'var(--radius-md)',
            padding: '12px 18px',
            marginBottom: '20px',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'space-between',
            color: '#fca5a5',
            fontSize: '0.88rem',
            animation: 'fadeIn 0.2s ease',
          }}
        >
          <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
            <AlertTriangle size={18} color="#ef4444" />
            <span><strong>Control Error:</strong> {controlError}</span>
          </div>
          <button
            onClick={() => setControlError(null)}
            style={{
              background: 'transparent',
              border: '1px solid rgba(239, 68, 68, 0.4)',
              borderRadius: '4px',
              color: '#fca5a5',
              cursor: 'pointer',
              padding: '4px 10px',
              fontWeight: 700,
              fontSize: '0.75rem',
            }}
          >
            Dismiss
          </button>
        </div>
      )}

      {/* Header with Peak Hour Mode Toggle */}
      <div className="card-header" style={{ marginBottom: '20px', display: 'flex', justifyContent: 'space-between', alignItems: 'center', flexWrap: 'wrap', gap: '16px' }}>
        <div>
          <h2 style={{ fontSize: '1.4rem', fontWeight: 800 }}>Corridor Operations Analytics</h2>
          <p style={{ color: 'var(--text-muted)', fontSize: '0.85rem' }}>
            Authoritative rolling utilization, real arrival delays, dynamic reallocation counts, and peak load controls.
          </p>
        </div>

        <div style={{ display: 'flex', alignItems: 'center', gap: '12px' }}>
          <button
            id="peak-hour-toggle-btn"
            onClick={handleTogglePeakHour}
            disabled={togglingPeak}
            title="Formula: Toggles between 27 baseline trains and 57 peak-hour trains with realistic headway spacing and signal interlocking."
            style={{
              display: 'flex',
              alignItems: 'center',
              gap: '8px',
              padding: '10px 18px',
              borderRadius: 'var(--radius-md)',
              border: isPeakHour ? '1px solid #f59e0b' : '1px solid var(--border-subtle)',
              background: isPeakHour ? 'rgba(245, 158, 11, 0.15)' : 'var(--bg-surface)',
              color: isPeakHour ? '#f59e0b' : 'var(--text-main)',
              cursor: togglingPeak ? 'not-allowed' : 'pointer',
              fontWeight: 700,
              fontSize: '0.88rem',
              transition: 'all 0.2s ease',
              opacity: togglingPeak ? 0.6 : 1,
            }}
          >
            <Zap size={16} color={isPeakHour ? '#f59e0b' : 'var(--text-muted)'} />
            <span>{isPeakHour ? 'Peak Hour: ACTIVE' : 'Peak Hour: OFF'}</span>
            <span
              style={{
                fontSize: '0.75rem',
                padding: '2px 8px',
                borderRadius: '12px',
                background: isPeakHour ? '#f59e0b' : 'var(--bg-primary)',
                color: isPeakHour ? '#000' : 'var(--text-muted)',
                fontWeight: 800,
              }}
            >
              {isPeakHour ? `${kpis?.activeTrains || 57} Trains` : `${kpis?.activeTrains || 27} Trains`}
            </span>
          </button>
        </div>
      </div>

      {/* KPI Cards Grid */}
      <div className="kpi-grid" style={{ marginBottom: '24px', gridTemplateColumns: 'repeat(auto-fit, minmax(220px, 1fr))' }}>
        {/* Main Rolling 60-min Utilization */}
        <div className="kpi-card" title="Platform utilization (last 60 sim min) = occupied platform-minutes / available platform-minutes over a rolling 60-simulated-minute window, excluding closed platforms from available time.">
          <div className="kpi-title" style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
            <span style={{ fontWeight: 700 }}>Platform utilization (last 60 sim min)</span>
            <Activity size={16} color="var(--color-primary)" />
          </div>
          <div className="kpi-value" style={{ color: 'var(--color-primary)' }}>
            {rollingPercent}%
          </div>
          <div className="kpi-footer" style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
            <span>
              {historyMins < 60
                ? `building history (${historyMins} min) • ${occupiedMins}m occupied / ${availableMins}m available`
                : `${occupiedMins}m occupied / ${availableMins}m available`}
            </span>
            <span title="Formula: occupied_platform_minutes / available_platform_minutes (rolling 60m window)"><HelpCircle size={13} color="var(--text-muted)" /></span>
          </div>
        </div>

        {/* Snapshot Utilization */}
        <div className="kpi-card" title="Platforms occupied now (snapshot) = occupied platforms / (total platforms - closed platforms) at this exact simulation tick.">
          <div className="kpi-title" style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
            <span>Platforms occupied now (snapshot)</span>
            <Activity size={16} color="var(--color-accent)" />
          </div>
          <div className="kpi-value" style={{ color: 'var(--color-accent)' }}>
            {snapshotPercent}%
          </div>
          <div className="kpi-footer" style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
            <span>{`${occupiedNow} of ${openPlats} occupied now, ${closedPlats} closed`}</span>
            <span title="Formula: current_occupied_platforms / (total_platforms - closed_platforms)"><HelpCircle size={13} color="var(--text-muted)" /></span>
          </div>
        </div>

        {/* Real Delay Metric */}
        <div className="kpi-card" title="Average Corridor Delay = mean delay across active trains calculated from real arrival delays (actual vs scheduled arrival) and waiting hold durations.">
          <div className="kpi-title" style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
            <span>Average Corridor Delay</span>
            <Clock size={16} color="var(--color-warning)" />
          </div>
          <div className="kpi-value" style={{ color: 'var(--color-warning)' }}>
            {kpis?.averageDelayMinutes || 0} <span style={{ fontSize: '1rem' }}>min</span>
          </div>
          <div className="kpi-footer" style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
            <span>{kpis?.delayedTrains || 0} trains currently delayed</span>
            <span title="Formula: sum(actual_arrival - scheduled_arrival + waiting_duration) / active_trains"><HelpCircle size={13} color="var(--text-muted)" /></span>
          </div>
        </div>

        {/* Dynamic Reallocations Metric */}
        <div className="kpi-card" title="Dynamic Reallocations = total count of persisted DYNAMIC_REALLOCATION, TRAIN_REALLOCATED, and TRAIN_HELD events. Rises automatically when platforms are closed.">
          <div className="kpi-title" style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
            <span>Dynamic Reallocations</span>
            <ArrowUpRight size={16} color="var(--color-success)" />
          </div>
          <div className="kpi-value" style={{ color: 'var(--color-success)' }}>
            {kpis?.totalReallocations || 0}
          </div>
          <div className="kpi-footer" style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
            <span>Persisted reallocations & holds</span>
            <span title="Formula: count(TRAIN_REALLOCATED + TRAIN_HELD + DYNAMIC_REALLOCATION)"><HelpCircle size={13} color="var(--text-muted)" /></span>
          </div>
        </div>
      </div>

      {/* Utilization Breakdowns: Busiest Stations & Line Type Utilization */}
      <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '24px', marginBottom: '24px' }}>
        {/* Busiest 3 Stations by Utilization */}
        <div className="surface-card">
          <h3 style={{ fontSize: '1.05rem', fontWeight: 700, marginBottom: '14px', display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
            <span style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
              <Activity size={18} color="var(--color-primary)" />
              <span>Busiest 3 Stations by Utilization</span>
            </span>
            <span title="Formula: Top 3 stations by occupied platform-minutes / available platform-minutes over the rolling window.">
              <HelpCircle size={15} color="var(--text-muted)" />
            </span>
          </h3>

          <div style={{ display: 'flex', flexDirection: 'column', gap: '14px' }}>
            {busiestStations && busiestStations.length > 0 ? (
              busiestStations.map((stn, idx) => (
                <div key={stn.stationCode} style={{ background: 'var(--bg-primary)', padding: '12px 14px', borderRadius: 'var(--radius-sm)', border: '1px solid var(--border-subtle)' }}>
                  <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '8px' }}>
                    <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
                      <span style={{
                        width: '22px',
                        height: '22px',
                        borderRadius: '50%',
                        background: idx === 0 ? '#f59e0b' : idx === 1 ? 'var(--color-primary)' : 'var(--text-muted)',
                        color: '#000',
                        display: 'flex',
                        alignItems: 'center',
                        justifyContent: 'center',
                        fontSize: '0.75rem',
                        fontWeight: 800,
                      }}>
                        #{idx + 1}
                      </span>
                      <span style={{ fontWeight: 700, fontSize: '0.9rem' }}>{stn.stationName}</span>
                      <span style={{ fontFamily: 'var(--font-mono)', fontSize: '0.75rem', color: 'var(--text-muted)', background: 'var(--bg-surface)', padding: '1px 6px', borderRadius: '4px' }}>
                        {stn.stationCode}
                      </span>
                    </div>
                    <span style={{ fontFamily: 'var(--font-mono)', fontWeight: 800, color: 'var(--color-primary)', fontSize: '0.95rem' }}>
                      {stn.utilizationPercent}%
                    </span>
                  </div>
                  <div style={{ height: '6px', background: 'var(--bg-surface)', borderRadius: '3px', overflow: 'hidden' }}>
                    <div
                      style={{
                        height: '100%',
                        width: `${Math.min(100, stn.utilizationPercent)}%`,
                        background: idx === 0 ? 'linear-gradient(90deg, #f59e0b, #ef4444)' : 'linear-gradient(90deg, var(--color-primary), var(--color-accent))',
                        borderRadius: '3px',
                      }}
                    />
                  </div>
                  <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: '0.72rem', color: 'var(--text-muted)', marginTop: '6px' }}>
                    <span>{stn.occupiedMinutes}m occupied</span>
                    <span>{stn.availableMinutes}m available</span>
                  </div>
                </div>
              ))
            ) : (
              <div style={{ padding: '20px', textAlign: 'center', color: 'var(--text-muted)', fontSize: '0.85rem' }}>
                Accumulating station platform utilization history...
              </div>
            )}
          </div>
        </div>

        {/* Utilization per Line Type */}
        <div className="surface-card">
          <h3 style={{ fontSize: '1.05rem', fontWeight: 700, marginBottom: '14px', display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
            <span style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
              <Layers size={18} color="var(--color-accent)" />
              <span>Utilization per Line Type</span>
            </span>
            <span title="Formula: Sum of occupied platform-minutes for all platforms configured on this line type / available platform-minutes on this line type.">
              <HelpCircle size={15} color="var(--text-muted)" />
            </span>
          </h3>

          <div style={{ display: 'flex', flexDirection: 'column', gap: '12px' }}>
            {['FAST_UP', 'FAST_DOWN', 'SLOW_UP', 'SLOW_DOWN'].map((lt) => {
              const cfg = lineTypeLabels[lt] || { label: lt, desc: '', color: 'var(--color-primary)' };
              const util = lineTypeUtilization[lt] != null ? lineTypeUtilization[lt] : 0;
              return (
                <div key={lt} style={{ background: 'var(--bg-primary)', padding: '10px 14px', borderRadius: 'var(--radius-sm)', border: '1px solid var(--border-subtle)' }}>
                  <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '6px' }}>
                    <div>
                      <span style={{ fontWeight: 700, fontSize: '0.85rem' }}>{cfg.label}</span>
                      <span style={{ fontSize: '0.75rem', color: 'var(--text-muted)', marginLeft: '8px' }}>{cfg.desc}</span>
                    </div>
                    <span style={{ fontFamily: 'var(--font-mono)', fontWeight: 800, color: cfg.color, fontSize: '0.9rem' }}>
                      {util}%
                    </span>
                  </div>
                  <div style={{ height: '6px', background: 'var(--bg-surface)', borderRadius: '3px', overflow: 'hidden' }}>
                    <div
                      style={{
                        height: '100%',
                        width: `${Math.min(100, util)}%`,
                        background: cfg.color,
                        borderRadius: '3px',
                        transition: 'width 0.4s ease',
                      }}
                    />
                  </div>
                </div>
              );
            })}
          </div>
        </div>
      </div>

      {/* Visual Analytics Panels: Train Category Distribution & Reliability Invariants */}
      <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '24px' }}>
        {/* Train Category Distribution Chart */}
        <div className="surface-card">
          <h3 style={{ fontSize: '1.05rem', fontWeight: 700, marginBottom: '16px', display: 'flex', alignItems: 'center', gap: '8px' }}>
            <Layers size={18} color="var(--color-primary)" />
            <span>Active Train Service Category Distribution</span>
          </h3>

          <div style={{ display: 'flex', flexDirection: 'column', gap: '14px' }}>
            {Object.entries(categoryDist).map(([category, count]) => {
              const percent = Math.round((count / maxCategoryCount) * 100);
              return (
                <div key={category}>
                  <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: '0.82rem', marginBottom: '6px' }}>
                    <span style={{ fontWeight: 600 }}>{category.replace('_', ' ')}</span>
                    <span style={{ fontFamily: 'var(--font-mono)', color: 'var(--text-muted)' }}>{count} trains</span>
                  </div>
                  <div style={{ height: '8px', background: 'var(--bg-primary)', borderRadius: '4px', overflow: 'hidden' }}>
                    <div
                      style={{
                        height: '100%',
                        width: `${percent}%`,
                        background: 'linear-gradient(90deg, var(--color-primary), var(--color-accent))',
                        borderRadius: '4px',
                        transition: 'width 0.5s ease',
                      }}
                    />
                  </div>
                </div>
              );
            })}
          </div>
        </div>

        {/* Operational Reliability & Stability Panel */}
        <div className="surface-card">
          <h3 style={{ fontSize: '1.05rem', fontWeight: 700, marginBottom: '16px', display: 'flex', alignItems: 'center', gap: '8px' }}>
            <ShieldCheck size={18} color="var(--color-success)" />
            <span>Operational Invariants & Scheduling Integrity</span>
          </h3>

          <div style={{ display: 'flex', flexDirection: 'column', gap: '12px' }}>
            <div style={{ background: 'var(--bg-primary)', padding: '14px', borderRadius: 'var(--radius-sm)', border: '1px solid var(--border-subtle)' }}>
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                <span style={{ fontWeight: 700, color: (kpis?.signalsPassedAtDanger ?? 0) === 0 ? 'var(--color-success)' : '#ef4444', fontSize: '0.9rem' }}>
                  Signals Passed At Danger (SPAD)
                </span>
                <span
                  style={{
                    fontFamily: 'var(--font-mono)',
                    fontWeight: 700,
                    fontSize: '0.85rem',
                    background: (kpis?.signalsPassedAtDanger ?? 0) === 0 ? 'rgba(16, 185, 129, 0.15)' : 'rgba(239, 68, 68, 0.2)',
                    color: (kpis?.signalsPassedAtDanger ?? 0) === 0 ? 'var(--color-success)' : '#ef4444',
                    padding: '2px 8px',
                    borderRadius: '4px',
                    border: `1px solid ${(kpis?.signalsPassedAtDanger ?? 0) === 0 ? 'rgba(16, 185, 129, 0.3)' : 'rgba(239, 68, 68, 0.4)'}`,
                  }}
                >
                  {kpis?.signalsPassedAtDanger ?? 0} (Expected: 0)
                </span>
              </div>
              <p style={{ fontSize: '0.8rem', color: 'var(--text-muted)', marginTop: '4px' }}>
                Strict stop-line interlocking prevents trains from entering blocks protected by RED signals at any simulation speed multiplier.
              </p>
            </div>

            <div style={{ background: 'var(--bg-primary)', padding: '14px', borderRadius: 'var(--radius-sm)', border: '1px solid var(--border-subtle)' }}>
              <div style={{ fontWeight: 700, color: 'var(--color-success)', fontSize: '0.9rem' }}>
                Zero Platform Head-On Collisions
              </div>
              <p style={{ fontSize: '0.8rem', color: 'var(--text-muted)', marginTop: '4px' }}>
                Interval conflict detection enforces 3-minute safety buffers and prevents simultaneous block occupation across all platform berths.
              </p>
            </div>

            <div style={{ background: 'var(--bg-primary)', padding: '14px', borderRadius: 'var(--radius-sm)', border: '1px solid var(--border-subtle)' }}>
              <div style={{ fontWeight: 700, color: 'var(--color-primary)', fontSize: '0.9rem' }}>
                Priority Ordering & Safety Interlocking
              </div>
              <p style={{ fontSize: '0.8rem', color: 'var(--text-muted)', marginTop: '4px' }}>
                Starter signals hold trains at platforms until the departure block is verified clear, enforcing speed limits and aspect safety.
              </p>
            </div>

            <div style={{ background: 'var(--bg-primary)', padding: '14px', borderRadius: 'var(--radius-sm)', border: '1px solid var(--border-subtle)' }}>
              <div style={{ fontWeight: 700, color: 'var(--color-accent)', fontSize: '0.9rem' }}>
                Automated Audit Trail Compliance
              </div>
              <p style={{ fontSize: '0.8rem', color: 'var(--text-muted)', marginTop: '4px' }}>
                Every reallocation and train hold is recorded in AllocationHistory with exact reasons and timestamps.
              </p>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
};
