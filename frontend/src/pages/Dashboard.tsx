import React from 'react';
import {
  Train as TrainIcon,
  AlertTriangle,
  Clock,
  Activity,
  CheckCircle2,
  TrendingUp,
  Flame,
  ArrowRight,
} from 'lucide-react';
import { Train, Platform, ConflictReport, PlatformAllocation } from '../types';
import { formatTrainLocation, formatNextStop } from '../utils/trainFormatters';

interface DashboardProps {
  selectedStation: string;
  trains: Train[];
  platforms: Platform[];
  allocations: PlatformAllocation[];
  conflicts: ConflictReport[];
  kpis: Record<string, any>;
  onRunAllocation: () => void;
  onNavigateTab: (tab: string) => void;
  onInjectSampleDelay: () => void;
}

export const Dashboard: React.FC<DashboardProps> = ({
  selectedStation,
  trains,
  platforms,
  conflicts,
  kpis,
  onRunAllocation,
  onNavigateTab,
  onInjectSampleDelay,
}) => {
  const stationTrains = trains.filter((t) => t.targetStationCode === selectedStation);
  const occupiedPlatforms = platforms.filter((p) => p.isOccupied).length;
  const closedPlatforms = platforms.filter((p) => p.isClosed).length;
  const availablePlatforms = platforms.length - occupiedPlatforms - closedPlatforms;

  const getBadgeClass = (type: string) => {
    switch (type) {
      case 'SPECIAL': return 'badge-special';
      case 'PREMIUM': return 'badge-premium';
      case 'SUPERFAST': return 'badge-superfast';
      case 'EXPRESS_MAIL': return 'badge-express';
      case 'LOCAL': return 'badge-local';
      default: return 'badge-freight';
    }
  };

  const getStatusBadge = (status: string) => {
    switch (status) {
      case 'AT_PLATFORM': return <span className="badge badge-success">AT PLATFORM</span>;
      case 'ARRIVING': return <span className="badge badge-warning">ARRIVING</span>;
      case 'APPROACHING': return <span className="badge badge-warning">APPROACHING</span>;
      case 'DELAYED': return <span className="badge badge-danger">DELAYED</span>;
      case 'WAITING': return <span className="badge badge-danger">WAITING</span>;
      default: return <span className="badge" style={{ background: 'var(--glass-bg)', color: 'var(--text-muted)', border: '1px solid var(--glass-border)' }}>{status}</span>;
    }
  };

  return (
    <div className="page-container">
      {/* Hero Welcome Banner */}
      <div
        className="glass-panel"
        style={{
          padding: '24px 28px',
          marginBottom: '24px',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'space-between',
          position: 'relative',
          overflow: 'hidden',
        }}
      >
        <div>
          <div style={{ display: 'inline-flex', alignItems: 'center', gap: '6px', background: 'rgba(56, 189, 248, 0.1)', color: 'var(--color-primary)', padding: '4px 10px', borderRadius: 'var(--radius-full)', fontSize: '0.75rem', fontWeight: 700, marginBottom: '10px' }}>
            <Activity size={14} />
            <span>MUMBAI CORRIDOR SIMULATION HUB</span>
          </div>
          <h1 style={{ fontSize: '1.75rem', fontWeight: 800, letterSpacing: '-0.5px', marginBottom: '6px' }}>
            Dynamic Railway Platform Allocation & Train Scheduling Simulator
          </h1>
          <p style={{ color: 'var(--text-muted)', fontSize: '0.9rem', maxWidth: '850px' }}>
            Algorithm-driven railway platform allocation engine for the Western Railway corridor (Virar to Mumbai Central).
            Demonstrating Min-Heap Priority Queues, Corridor Graph routing, Interval Overlap Conflict Detection, and Dynamic Reallocation.
          </p>
        </div>

        <div style={{ display: 'flex', flexDirection: 'column', gap: '8px', minWidth: '220px' }}>
          <button className="btn btn-primary" onClick={onRunAllocation}>
            <CheckCircle2 size={16} />
            <span>Run Platform Allocation</span>
          </button>
          <button className="btn btn-secondary" onClick={onInjectSampleDelay}>
            <Flame size={16} color="var(--color-warning)" />
            <span>Inject +15m Delay & Reallocate</span>
          </button>
        </div>
      </div>

      {/* Active Conflict Banner if detected */}
      {conflicts.length > 0 && (
        <div
          className="glass-panel"
          style={{
            borderColor: 'rgba(239, 68, 68, 0.4)',
            padding: '16px 20px',
            marginBottom: '24px',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'space-between',
          }}
        >
          <div style={{ display: 'flex', alignItems: 'center', gap: '14px' }}>
            <div style={{ background: 'rgba(239, 68, 68, 0.2)', padding: '10px', borderRadius: '50%', color: 'var(--color-danger)' }}>
              <AlertTriangle size={24} />
            </div>
            <div>
              <div style={{ fontWeight: 700, color: 'var(--color-danger)', fontSize: '1rem' }}>
                {conflicts.length} Platform Interval Conflict Detected!
              </div>
              <div style={{ color: 'var(--text-muted)', fontSize: '0.85rem' }}>
                {conflicts[0].description}
              </div>
            </div>
          </div>

          <button className="btn btn-danger" onClick={() => onNavigateTab('simulator')}>
            <span>Resolve in What-If Studio</span>
            <ArrowRight size={14} />
          </button>
        </div>
      )}

      {/* KPI Cards Grid */}
      <div className="kpi-grid">
        <div className="kpi-card">
          <div className="kpi-title">
            <span>Active Trains</span>
            <TrainIcon size={16} color="var(--color-primary)" />
          </div>
          <div className="kpi-value">{stationTrains.length}</div>
          <div className="kpi-footer">
            <span>Targeting {selectedStation}</span>
          </div>
        </div>

        <div className="kpi-card">
          <div className="kpi-title">
            <span>Available Platforms</span>
            <CheckCircle2 size={16} color="var(--color-success)" />
          </div>
          <div className="kpi-value" style={{ color: 'var(--color-success)' }}>
            {availablePlatforms} <span style={{ fontSize: '1rem', color: 'var(--text-dim)' }}>/ {platforms.length}</span>
          </div>
          <div className="kpi-footer">
            <span>{occupiedPlatforms} Occupied | {closedPlatforms} Closed</span>
          </div>
        </div>

        <div className="kpi-card">
          <div className="kpi-title">
            <span>Delayed Trains</span>
            <Clock size={16} color="var(--color-warning)" />
          </div>
          <div className="kpi-value" style={{ color: 'var(--color-warning)' }}>
            {stationTrains.filter((t) => t.delayMinutes > 0).length}
          </div>
          <div className="kpi-footer">
            <span>Avg Delay: {kpis?.averageDelayMinutes || 0} min</span>
          </div>
        </div>

        <div className="kpi-card">
          <div className="kpi-title">
            <span>Platform Utilization</span>
            <TrendingUp size={16} color="var(--color-accent)" />
          </div>
          <div className="kpi-value" style={{ color: 'var(--color-accent)' }}>
            {kpis?.platformUtilizationPercent || 0}%
          </div>
          <div className="kpi-footer">
            <span>{kpis?.conflictsResolved || 0} Conflicts Avoided</span>
          </div>
        </div>
      </div>

      {/* Live Train Feed for selected station */}
      <div className="surface-card">
        <div className="card-header">
          <div className="card-title">
            <TrainIcon size={20} color="var(--color-primary)" />
            <span>Live Approaching & Docked Trains — Station {selectedStation}</span>
          </div>
          <div style={{ fontSize: '0.8rem', color: 'var(--text-dim)' }}>
            Showing {stationTrains.length} scheduled services
          </div>
        </div>

        <div style={{ overflowX: 'auto' }}>
          <table className="data-table">
            <thead>
              <tr>
                <th>Train No.</th>
                <th>Service Name</th>
                <th>Category</th>
                <th>Location</th>
                <th>Next Stop</th>
                <th>Route</th>
                <th>Priority</th>
                <th>Scheduled Arr/Dep</th>
                <th>Delay</th>
                <th>Assigned Platform</th>
                <th>Status</th>
              </tr>
            </thead>
            <tbody>
              {stationTrains.map((train) => (
                <tr key={train.trainId}>
                  <td style={{ fontFamily: 'var(--font-mono)', fontWeight: 700 }}>{train.trainNumber}</td>
                  <td style={{ fontWeight: 600 }}>{train.trainName}</td>
                  <td>
                    <span className={`badge ${getBadgeClass(train.trainType)}`}>
                      {train.trainType.replace('_', ' ')}
                    </span>
                  </td>
                  <td style={{ fontSize: '0.8rem', color: '#e2e8f0', fontWeight: 600 }}>
                    {formatTrainLocation(train)}
                  </td>
                  <td style={{ fontFamily: 'var(--font-mono)', fontSize: '0.8rem', color: '#38bdf8', fontWeight: 700 }}>
                    {formatNextStop(train)}
                  </td>
                  <td style={{ color: 'var(--text-muted)' }}>
                    {train.sourceStation} &rarr; {train.destinationStation}
                  </td>
                  <td style={{ textAlign: 'center', fontFamily: 'var(--font-mono)' }}>
                    Tier {train.simulationPriority}
                  </td>
                  <td style={{ fontFamily: 'var(--font-mono)', fontSize: '0.8rem' }}>
                    {new Date(train.scheduledArrival).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })} -{' '}
                    {new Date(train.scheduledDeparture).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })}
                  </td>
                  <td>
                    {train.delayMinutes > 0 ? (
                      <span style={{ color: 'var(--color-danger)', fontWeight: 700 }}>+{train.delayMinutes}m</span>
                    ) : (
                      <span style={{ color: 'var(--color-success)' }}>On Time</span>
                    )}
                  </td>
                  <td style={{ fontWeight: 800 }}>
                    {train.assignedPlatformNumber ? (
                      <span
                        style={{
                          background: 'rgba(56, 189, 248, 0.15)',
                          color: 'var(--color-primary)',
                          padding: '4px 10px',
                          borderRadius: 'var(--radius-sm)',
                          border: '1px solid var(--border-glow)',
                        }}
                      >
                        Platform {train.assignedPlatformNumber}
                      </span>
                    ) : (
                      <span style={{ color: 'var(--text-dim)' }}>Unassigned</span>
                    )}
                  </td>
                  <td>{getStatusBadge(train.status)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </div>
    </div>
  );
};
