import React from 'react';
import { BarChart3, TrendingUp, ShieldCheck, Clock, Layers, Activity } from 'lucide-react';

interface AnalyticsPageProps {
  kpis: Record<string, any>;
}

export const AnalyticsPage: React.FC<AnalyticsPageProps> = ({ kpis }) => {
  const categoryDist: Record<string, number> = kpis?.categoryDistribution || {};
  const maxCategoryCount = Math.max(...Object.values(categoryDist), 1);

  return (
    <div className="page-container">
      <div className="card-header" style={{ marginBottom: '20px' }}>
        <div>
          <h2 style={{ fontSize: '1.4rem', fontWeight: 800 }}>Corridor Performance Analytics</h2>
          <p style={{ color: 'var(--text-muted)', fontSize: '0.85rem' }}>
            Throughput efficiency, platform utilization rates, delay distributions, and conflict resolution metrics.
          </p>
        </div>
      </div>

      {/* Metric Cards Grid */}
      <div className="kpi-grid" style={{ marginBottom: '24px' }}>
        <div className="kpi-card">
          <div className="kpi-title">
            <span>Utilization Rate</span>
            <Activity size={16} color="var(--color-primary)" />
          </div>
          <div className="kpi-value" style={{ color: 'var(--color-primary)' }}>
            {kpis?.platformUtilizationPercent || 0}%
          </div>
          <div className="kpi-footer">
            <span>{kpis?.occupiedPlatforms || 0} of {kpis?.totalPlatforms || 0} Platforms Active</span>
          </div>
        </div>

        <div className="kpi-card">
          <div className="kpi-title">
            <span>Average Corridor Delay</span>
            <Clock size={16} color="var(--color-warning)" />
          </div>
          <div className="kpi-value" style={{ color: 'var(--color-warning)' }}>
            {kpis?.averageDelayMinutes || 0} <span style={{ fontSize: '1rem' }}>min</span>
          </div>
          <div className="kpi-footer">
            <span>{kpis?.delayedTrains || 0} trains currently delayed</span>
          </div>
        </div>

        <div className="kpi-card">
          <div className="kpi-title">
            <span>Conflicts Prevented</span>
            <ShieldCheck size={16} color="var(--color-success)" />
          </div>
          <div className="kpi-value" style={{ color: 'var(--color-success)' }}>
            {kpis?.conflictsResolved || 0}
          </div>
          <div className="kpi-footer">
            <span>Via dynamic interval reallocation</span>
          </div>
        </div>

        <div className="kpi-card">
          <div className="kpi-title">
            <span>Dynamic Reallocations</span>
            <TrendingUp size={16} color="var(--color-accent)" />
          </div>
          <div className="kpi-value" style={{ color: 'var(--color-accent)' }}>
            {kpis?.totalReallocations || 0}
          </div>
          <div className="kpi-footer">
            <span>Auto-resolved operational shifts</span>
          </div>
        </div>
      </div>

      {/* Visual Analytics Panels */}
      <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '24px' }}>
        {/* Train Category Distribution Chart */}
        <div className="surface-card">
          <h3 style={{ fontSize: '1.1rem', fontWeight: 700, marginBottom: '16px', display: 'flex', alignItems: 'center', gap: '8px' }}>
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
          <h3 style={{ fontSize: '1.1rem', fontWeight: 700, marginBottom: '16px', display: 'flex', alignItems: 'center', gap: '8px' }}>
            <ShieldCheck size={18} color="var(--color-success)" />
            <span>Scheduling Invariants & System Reliability</span>
          </h3>

          <div style={{ display: 'flex', flexDirection: 'column', gap: '12px' }}>
            <div style={{ background: 'var(--bg-primary)', padding: '14px', borderRadius: 'var(--radius-sm)', border: '1px solid var(--border-subtle)' }}>
              <div style={{ fontWeight: 700, color: 'var(--color-success)', fontSize: '0.9rem' }}>
                Zero Platform Head-On Collisions
              </div>
              <p style={{ fontSize: '0.8rem', color: 'var(--text-muted)', marginTop: '4px' }}>
                Interval conflict detection enforces 3-minute safety buffers across all platform intervals.
              </p>
            </div>

            <div style={{ background: 'var(--bg-primary)', padding: '14px', borderRadius: 'var(--radius-sm)', border: '1px solid var(--border-subtle)' }}>
              <div style={{ fontWeight: 700, color: 'var(--color-primary)', fontSize: '0.9rem' }}>
                Priority Ordering Preservation
              </div>
              <p style={{ fontSize: '0.8rem', color: 'var(--text-muted)', marginTop: '4px' }}>
                Min-Heap priority queue guarantees Emergency and Premium services maintain allocation precedence under configurable priority ordering.
              </p>
            </div>

            <div style={{ background: 'var(--bg-primary)', padding: '14px', borderRadius: 'var(--radius-sm)', border: '1px solid var(--border-subtle)' }}>
              <div style={{ fontWeight: 700, color: 'var(--color-accent)', fontSize: '0.9rem' }}>
                Automated Audit Trail Compliance
              </div>
              <p style={{ fontSize: '0.8rem', color: 'var(--text-muted)', marginTop: '4px' }}>
                Every reallocation event is recorded in AllocationHistory with detailed before/after diffs and reasons.
              </p>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
};
