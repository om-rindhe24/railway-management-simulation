import React, { useState } from 'react';
import { AllocationHistory } from '../types';
import { History, ArrowRight, ShieldCheck, Filter } from 'lucide-react';

interface AllocationHistoryPageProps {
  history: AllocationHistory[];
  selectedStation: string;
}

export const AllocationHistoryPage: React.FC<AllocationHistoryPageProps> = ({
  history,
  selectedStation,
}) => {
  const [filterType, setFilterType] = useState<string>('ALL');

  const filteredHistory = history.filter((h) => {
    if (filterType === 'ALL') return true;
    return h.changeType === filterType;
  });

  const getChangeTypeBadge = (type: string) => {
    switch (type) {
      case 'DYNAMIC_REALLOCATION':
        return <span className="badge badge-warning">REALLOCATION</span>;
      case 'PLATFORM_CLOSURE':
        return <span className="badge badge-danger">PLATFORM CLOSURE</span>;
      default:
        return <span className="badge badge-success">INITIAL ALLOCATION</span>;
    }
  };

  return (
    <div className="page-container">
      <div className="card-header" style={{ marginBottom: '20px' }}>
        <div>
          <h2 style={{ fontSize: '1.4rem', fontWeight: 800 }}>
            Allocation & Dynamic Reallocation Audit Log
          </h2>
          <p style={{ color: 'var(--text-muted)', fontSize: '0.85rem' }}>
            Immutable chronological record of all platform allocations, perturbation events, and conflict avoidance metrics.
          </p>
        </div>

        <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
          <Filter size={16} color="var(--text-dim)" />
          <select
            value={filterType}
            onChange={(e) => setFilterType(e.target.value)}
            style={{
              background: 'var(--bg-secondary)',
              border: '1px solid var(--border-card)',
              color: 'var(--text-main)',
              padding: '8px 12px',
              borderRadius: 'var(--radius-sm)',
            }}
          >
            <option value="ALL">All Event Types</option>
            <option value="DYNAMIC_REALLOCATION">Dynamic Reallocations</option>
            <option value="PLATFORM_CLOSURE">Platform Closures</option>
            <option value="INITIAL_ALLOCATION">Initial Allocations</option>
          </select>
        </div>
      </div>

      <div className="surface-card" style={{ padding: 0, overflow: 'hidden' }}>
        <table className="data-table">
          <thead>
            <tr>
              <th>Timestamp</th>
              <th>Train</th>
              <th>Station</th>
              <th>Platform Transition</th>
              <th>Event Type</th>
              <th>Algorithmic Reasoning</th>
              <th>Delay Delta</th>
              <th>Conflicts Resolved</th>
            </tr>
          </thead>
          <tbody>
            {filteredHistory.length === 0 ? (
              <tr>
                <td colSpan={8} style={{ textAlign: 'center', padding: '40px', color: 'var(--text-dim)' }}>
                  No allocation history records found for the selected filter.
                </td>
              </tr>
            ) : (
              filteredHistory.map((entry) => (
                <tr key={entry.historyId}>
                  <td style={{ fontFamily: 'var(--font-mono)', fontSize: '0.78rem', color: 'var(--text-dim)' }}>
                    {new Date(entry.timestamp).toLocaleTimeString()}
                  </td>
                  <td>
                    <div style={{ fontWeight: 700 }}>{entry.trainNumber}</div>
                    <div style={{ fontSize: '0.75rem', color: 'var(--text-muted)' }}>{entry.trainName}</div>
                  </td>
                  <td style={{ fontFamily: 'var(--font-mono)' }}>{entry.stationCode}</td>
                  <td>
                    <div style={{ display: 'flex', alignItems: 'center', gap: '8px', fontWeight: 800 }}>
                      <span style={{ color: entry.fromPlatformNumber ? 'var(--text-muted)' : 'var(--text-dim)' }}>
                        {entry.fromPlatformNumber ? `P${entry.fromPlatformNumber}` : 'Timetable'}
                      </span>
                      <ArrowRight size={14} color="var(--color-primary)" />
                      <span style={{ color: 'var(--color-primary)', background: 'rgba(56, 189, 248, 0.12)', padding: '2px 8px', borderRadius: '4px' }}>
                        P{entry.toPlatformNumber}
                      </span>
                    </div>
                  </td>
                  <td>{getChangeTypeBadge(entry.changeType)}</td>
                  <td style={{ maxWidth: '350px', fontSize: '0.82rem', color: 'var(--text-main)' }}>
                    {entry.reason}
                  </td>
                  <td style={{ fontFamily: 'var(--font-mono)' }}>
                    {entry.delayImpactMinutes > 0 ? (
                      <span style={{ color: 'var(--color-warning)' }}>+{entry.delayImpactMinutes}m</span>
                    ) : (
                      <span style={{ color: 'var(--color-success)' }}>0m</span>
                    )}
                  </td>
                  <td>
                    <span style={{ display: 'inline-flex', alignItems: 'center', gap: '4px', color: 'var(--color-success)', fontWeight: 700 }}>
                      <ShieldCheck size={14} />
                      +{entry.conflictsAvoided}
                    </span>
                  </td>
                </tr>
              ))
            )}
          </tbody>
        </table>
      </div>
    </div>
  );
};
