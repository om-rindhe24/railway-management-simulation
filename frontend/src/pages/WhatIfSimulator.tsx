import React, { useState } from 'react';
import { Platform, Train, ReallocationResult } from '../types';
import { Sliders, AlertTriangle, ShieldCheck, Flame, RotateCcw, ArrowRight } from 'lucide-react';

interface WhatIfSimulatorProps {
  selectedStation: string;
  platforms: Platform[];
  trains: Train[];
  onInjectDelay: (trainId: number, delayMinutes: number, reason: string) => Promise<ReallocationResult>;
  onClosePlatform: (platformId: number, reason: string) => Promise<ReallocationResult>;
  onResetSimulation: () => void;
}

export const WhatIfSimulator: React.FC<WhatIfSimulatorProps> = ({
  selectedStation,
  platforms,
  trains,
  onInjectDelay,
  onClosePlatform,
  onResetSimulation,
}) => {
  const [selectedScenario, setSelectedScenario] = useState<'DELAY' | 'CLOSURE' | 'EMERGENCY'>('DELAY');
  const [selectedTrainId, setSelectedTrainId] = useState<number>(trains[0]?.trainId || 1);
  const [delayMinutes, setDelayMinutes] = useState<number>(20);
  const [selectedPlatformId, setSelectedPlatformId] = useState<number>(platforms[0]?.platformId || 1);
  const [impactResult, setImpactResult] = useState<ReallocationResult | null>(null);
  const [isSimulating, setIsSimulating] = useState(false);

  const handleRunScenario = async () => {
    setIsSimulating(true);
    try {
      if (selectedScenario === 'DELAY') {
        const res = await onInjectDelay(selectedTrainId, delayMinutes, `What-If Scenario: ${delayMinutes}m delay injection`);
        setImpactResult(res);
      } else if (selectedScenario === 'CLOSURE') {
        const res = await onClosePlatform(selectedPlatformId, 'What-If Scenario: Simulated Track Repair');
        setImpactResult(res);
      }
    } catch (err: any) {
      alert('Error running scenario: ' + err.message);
    } finally {
      setIsSimulating(false);
    }
  };

  return (
    <div className="page-container">
      <div className="card-header" style={{ marginBottom: '20px' }}>
        <div>
          <h2 style={{ fontSize: '1.4rem', fontWeight: 800 }}>
            What-If Operations Impact Studio
          </h2>
          <p style={{ color: 'var(--text-muted)', fontSize: '0.85rem' }}>
            Test operational perturbations (unplanned delays, platform shutdowns) and simulate algorithmic cascading resolution before committing.
          </p>
        </div>

        <button className="btn btn-secondary" onClick={onResetSimulation}>
          <RotateCcw size={16} />
          <span>Reset Simulation State</span>
        </button>
      </div>

      <div style={{ display: 'grid', gridTemplateColumns: '1fr 1.2fr', gap: '24px' }}>
        {/* Scenario Configuration Controls */}
        <div className="surface-card">
          <h3 style={{ fontSize: '1.1rem', fontWeight: 700, marginBottom: '16px' }}>
            1. Configure Perturbation Scenario
          </h3>

          <div style={{ display: 'flex', gap: '10px', marginBottom: '20px' }}>
            <button
              className={`btn ${selectedScenario === 'DELAY' ? 'btn-primary' : 'btn-secondary'}`}
              style={{ flex: 1 }}
              onClick={() => setSelectedScenario('DELAY')}
            >
              Train Delay Cascade
            </button>
            <button
              className={`btn ${selectedScenario === 'CLOSURE' ? 'btn-primary' : 'btn-secondary'}`}
              style={{ flex: 1 }}
              onClick={() => setSelectedScenario('CLOSURE')}
            >
              Platform Closure
            </button>
          </div>

          {selectedScenario === 'DELAY' ? (
            <div>
              <div style={{ marginBottom: '16px' }}>
                <label style={{ display: 'block', fontSize: '0.75rem', fontWeight: 600, color: 'var(--text-dim)', marginBottom: '6px' }}>
                  TARGET TRAIN SERVICE
                </label>
                <select
                  value={selectedTrainId}
                  onChange={(e) => setSelectedTrainId(Number(e.target.value))}
                  style={{
                    width: '100%',
                    background: 'var(--glass-bg)',
                    border: '1px solid var(--glass-border)',
                    color: 'var(--text-main)',
                    padding: '10px 12px',
                    borderRadius: 'var(--radius-sm)',
                    fontWeight: 600,
                  }}
                >
                  {trains.map((t) => (
                    <option key={t.trainId} value={t.trainId}>
                      {t.trainNumber} - {t.trainName} ({t.trainType}, P{t.assignedPlatformNumber || '?'})
                    </option>
                  ))}
                </select>
              </div>

              <div style={{ marginBottom: '24px' }}>
                <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: '6px' }}>
                  <label style={{ fontSize: '0.75rem', fontWeight: 600, color: 'var(--text-dim)' }}>
                    DELAY MAGNITUDE (MINUTES)
                  </label>
                  <span style={{ fontFamily: 'var(--font-mono)', fontWeight: 800, color: 'var(--color-warning)' }}>
                    +{delayMinutes} mins
                  </span>
                </div>
                <input
                  type="range"
                  min="5"
                  max="60"
                  step="5"
                  value={delayMinutes}
                  onChange={(e) => setDelayMinutes(Number(e.target.value))}
                  style={{ width: '100%', cursor: 'pointer' }}
                />
              </div>
            </div>
          ) : (
            <div style={{ marginBottom: '24px' }}>
              <label style={{ display: 'block', fontSize: '0.75rem', fontWeight: 600, color: 'var(--text-dim)', marginBottom: '6px' }}>
                SELECT PLATFORM TO CLOSE AT {selectedStation}
              </label>
              <select
                value={selectedPlatformId}
                onChange={(e) => setSelectedPlatformId(Number(e.target.value))}
                style={{
                  width: '100%',
                  background: 'var(--glass-bg)',
                  border: '1px solid var(--glass-border)',
                  color: 'var(--text-main)',
                  padding: '10px 12px',
                  borderRadius: 'var(--radius-sm)',
                  fontWeight: 600,
                }}
              >
                {platforms.map((p) => (
                  <option key={p.platformId} value={p.platformId}>
                    Platform {p.platformNumber} ({p.lineType}) {p.isClosed ? '[ALREADY CLOSED]' : ''}
                  </option>
                ))}
              </select>
            </div>
          )}

          <button
            className="btn btn-primary"
            style={{ width: '100%', padding: '12px' }}
            onClick={handleRunScenario}
            disabled={isSimulating}
          >
            <Flame size={16} />
            <span>{isSimulating ? 'Evaluating Algorithm...' : 'Apply Scenario & Reallocate'}</span>
          </button>
        </div>

        {/* Impact Prediction Dashboard */}
        <div className="surface-card">
          <h3 style={{ fontSize: '1.1rem', fontWeight: 700, marginBottom: '16px' }}>
            2. Algorithmic Impact Assessment
          </h3>

          {impactResult ? (
            <div>
              {/* Impact KPI summary */}
              <div
                style={{
                  display: 'grid',
                  gridTemplateColumns: 'repeat(3, 1fr)',
                  gap: '12px',
                  marginBottom: '20px',
                }}
              >
                <div className="surface-card" style={{ padding: '14px', textAlign: 'center' }}>
                  <div style={{ fontSize: '0.7rem', color: 'var(--text-dim)', textTransform: 'uppercase' }}>Affected Trains</div>
                  <div style={{ fontSize: '1.6rem', fontWeight: 800, color: 'var(--color-primary)' }}>
                    {impactResult.totalAffectedTrains}
                  </div>
                </div>

                <div className="surface-card" style={{ padding: '14px', textAlign: 'center' }}>
                  <div style={{ fontSize: '0.7rem', color: 'var(--text-dim)', textTransform: 'uppercase' }}>Reallocated</div>
                  <div style={{ fontSize: '1.6rem', fontWeight: 800, color: 'var(--color-success)' }}>
                    {impactResult.reallocatedCount}
                  </div>
                </div>

                <div className="surface-card" style={{ padding: '14px', textAlign: 'center' }}>
                  <div style={{ fontSize: '0.7rem', color: 'var(--text-dim)', textTransform: 'uppercase' }}>Conflicts Resolved</div>
                  <div style={{ fontSize: '1.6rem', fontWeight: 800, color: 'var(--color-accent)' }}>
                    {impactResult.conflictsResolved}
                  </div>
                </div>
              </div>

              {/* Summary message */}
              <div style={{ background: 'rgba(16, 185, 129, 0.1)', border: '1px solid var(--color-success)', borderRadius: 'var(--radius-sm)', padding: '12px 14px', marginBottom: '16px', fontSize: '0.85rem' }}>
                <span style={{ fontWeight: 700, color: 'var(--color-success)' }}>Success: </span>
                {impactResult.summaryMessage}
              </div>

              {/* Reallocation Decisions list */}
              <div style={{ maxHeight: '280px', overflowY: 'auto' }}>
                {impactResult.reallocationDecisions.map((dec, idx) => (
                  <div
                    key={idx}
                    className="glass-panel"
                    style={{
                      padding: '12px',
                      marginBottom: '8px',
                    }}
                  >
                    <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '4px' }}>
                      <span style={{ fontWeight: 700 }}>{dec.trainNumber} - {dec.trainName}</span>
                      <span style={{ fontWeight: 800, color: 'var(--color-primary)' }}>
                        &rarr; Platform {dec.selectedPlatformNumber}
                      </span>
                    </div>
                    <div style={{ fontSize: '0.75rem', color: 'var(--text-muted)' }}>
                      {dec.detailedBreakdown}
                    </div>
                  </div>
                ))}
              </div>
            </div>
          ) : (
            <div style={{ textAlign: 'center', padding: '60px 20px', color: 'var(--text-dim)' }}>
              <Sliders size={40} style={{ margin: '0 auto 12px', opacity: 0.3 }} />
              <div>No scenario run yet. Configure a delay or platform closure to observe cascading reallocation.</div>
            </div>
          )}
        </div>
      </div>
    </div>
  );
};
