import React, { useState } from 'react';
import { Platform, Train, ReallocationResult, WhatIfPreviewResult } from '../types';
import { api } from '../services/api';
import { TrainCombobox } from '../components/TrainCombobox';
import {
  Sliders,
  AlertTriangle,
  Flame,
  RotateCcw,
  CheckCircle2,
  ArrowRight,
  Info,
  ShieldCheck,
  Zap,
} from 'lucide-react';

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
  const [selectedScenario, setSelectedScenario] = useState<'DELAY' | 'CLOSURE'>('DELAY');
  const [selectedTrainId, setSelectedTrainId] = useState<number>(trains[0]?.trainId || 1);
  const [delayMinutes, setDelayMinutes] = useState<number>(20);
  const [selectedPlatformId, setSelectedPlatformId] = useState<number>(platforms[0]?.platformId || 1);

  const [previewResult, setPreviewResult] = useState<WhatIfPreviewResult | null>(null);
  const [isSimulating, setIsSimulating] = useState(false);
  const [isApplying, setIsApplying] = useState(false);
  const [appliedSuccess, setAppliedSuccess] = useState<string | null>(null);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);

  // Evaluate scenario on isolated copy
  const handleEvaluateScenario = async () => {
    setIsSimulating(true);
    setErrorMessage(null);
    setAppliedSuccess(null);
    try {
      if (selectedScenario === 'DELAY') {
        const preview = await api.previewTrainDelay(
          selectedTrainId,
          delayMinutes,
          `What-If Scenario: ${delayMinutes}m delay injection`
        );
        setPreviewResult(preview);
      } else {
        const preview = await api.previewPlatformClosure(
          selectedPlatformId,
          'What-If Scenario: Simulated Track Repair'
        );
        setPreviewResult(preview);
      }
    } catch (err: any) {
      setErrorMessage(err.message || 'Failed to simulate scenario');
    } finally {
      setIsSimulating(false);
    }
  };

  // Commit evaluated scenario to live simulation
  const handleApplyToLive = async () => {
    if (!previewResult) return;
    setIsApplying(true);
    setErrorMessage(null);
    try {
      if (selectedScenario === 'DELAY') {
        await onInjectDelay(
          selectedTrainId,
          delayMinutes,
          `Committed What-If: +${delayMinutes}m delay`
        );
        setAppliedSuccess(`Successfully committed ${delayMinutes}m delay on target train to the live simulation!`);
      } else {
        await onClosePlatform(
          selectedPlatformId,
          'Committed What-If: Platform closure'
        );
        setAppliedSuccess(`Successfully committed platform closure to the live simulation!`);
      }
    } catch (err: any) {
      setErrorMessage(err.message || 'Failed to apply scenario to live simulation');
    } finally {
      setIsApplying(false);
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
            Test operational perturbations (unplanned delays, platform shutdowns) on an isolated simulation copy before committing to live traffic.
          </p>
        </div>

        <button className="btn btn-secondary" onClick={onResetSimulation}>
          <RotateCcw size={16} />
          <span>Reset Simulation State</span>
        </button>
      </div>

      {/* Error alert */}
      {errorMessage && (
        <div
          data-testid="what-if-error-banner"
          style={{
            background: 'rgba(239, 68, 68, 0.15)',
            border: '1px solid var(--color-danger, #ef4444)',
            color: '#fca5a5',
            padding: '12px 16px',
            borderRadius: 'var(--radius-sm, 8px)',
            marginBottom: '20px',
            display: 'flex',
            alignItems: 'center',
            gap: '10px',
            fontSize: '0.85rem',
          }}
        >
          <AlertTriangle size={18} style={{ color: '#ef4444', flexShrink: 0 }} />
          <span>{errorMessage}</span>
        </div>
      )}

      {/* Success alert */}
      {appliedSuccess && (
        <div
          data-testid="what-if-success-banner"
          style={{
            background: 'rgba(16, 185, 129, 0.15)',
            border: '1px solid var(--color-success, #10b981)',
            color: '#6ee7b7',
            padding: '12px 16px',
            borderRadius: 'var(--radius-sm, 8px)',
            marginBottom: '20px',
            display: 'flex',
            alignItems: 'center',
            gap: '10px',
            fontSize: '0.85rem',
          }}
        >
          <CheckCircle2 size={18} style={{ color: '#10b981', flexShrink: 0 }} />
          <span>{appliedSuccess}</span>
        </div>
      )}

      <div style={{ display: 'grid', gridTemplateColumns: '1fr 1.35fr', gap: '24px' }}>
        {/* Scenario Configuration Controls */}
        <div className="surface-card">
          <h3 style={{ fontSize: '1.1rem', fontWeight: 700, marginBottom: '16px' }}>
            1. Configure Perturbation Scenario
          </h3>

          <div style={{ display: 'flex', gap: '10px', marginBottom: '20px' }}>
            <button
              data-testid="tab-delay-cascade"
              className={`btn ${selectedScenario === 'DELAY' ? 'btn-primary' : 'btn-secondary'}`}
              style={{ flex: 1 }}
              onClick={() => {
                setSelectedScenario('DELAY');
                setPreviewResult(null);
                setAppliedSuccess(null);
                setErrorMessage(null);
              }}
            >
              Train Delay Cascade
            </button>
            <button
              data-testid="tab-platform-closure"
              className={`btn ${selectedScenario === 'CLOSURE' ? 'btn-primary' : 'btn-secondary'}`}
              style={{ flex: 1 }}
              onClick={() => {
                setSelectedScenario('CLOSURE');
                setPreviewResult(null);
                setAppliedSuccess(null);
                setErrorMessage(null);
              }}
            >
              Platform Closure
            </button>
          </div>

          {selectedScenario === 'DELAY' ? (
            <div>
              <div style={{ marginBottom: '16px' }}>
                <label style={{ display: 'block', fontSize: '0.75rem', fontWeight: 600, color: 'var(--text-dim)', marginBottom: '8px' }}>
                  TARGET TRAIN SERVICE ({trains.length} FLEET TRAINS)
                </label>
                <TrainCombobox
                  trains={trains}
                  selectedTrainId={selectedTrainId}
                  onSelectTrain={(id) => {
                    setSelectedTrainId(id);
                    setPreviewResult(null);
                    setAppliedSuccess(null);
                  }}
                  disabled={isSimulating || isApplying}
                />
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
                  data-testid="delay-slider"
                  type="range"
                  min="5"
                  max="60"
                  step="5"
                  value={delayMinutes}
                  onChange={(e) => {
                    setDelayMinutes(Number(e.target.value));
                    setPreviewResult(null);
                    setAppliedSuccess(null);
                  }}
                  style={{ width: '100%', cursor: 'pointer' }}
                  disabled={isSimulating || isApplying}
                />
              </div>
            </div>
          ) : (
            <div style={{ marginBottom: '24px' }}>
              <label style={{ display: 'block', fontSize: '0.75rem', fontWeight: 600, color: 'var(--text-dim)', marginBottom: '8px' }}>
                SELECT PLATFORM TO CLOSE AT {selectedStation}
              </label>
              <select
                data-testid="platform-closure-select"
                value={selectedPlatformId}
                onChange={(e) => {
                  setSelectedPlatformId(Number(e.target.value));
                  setPreviewResult(null);
                  setAppliedSuccess(null);
                }}
                disabled={isSimulating || isApplying}
                style={{
                  width: '100%',
                  background: '#0f172a',
                  border: '1px solid rgba(255, 255, 255, 0.15)',
                  color: '#f8fafc',
                  padding: '10px 12px',
                  borderRadius: 'var(--radius-sm, 6px)',
                  fontWeight: 600,
                  fontSize: '0.85rem',
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
            data-testid="btn-evaluate-scenario"
            className="btn btn-primary"
            style={{ width: '100%', padding: '12px', marginBottom: '12px' }}
            onClick={handleEvaluateScenario}
            disabled={isSimulating || isApplying}
          >
            <Flame size={16} />
            <span>{isSimulating ? 'Evaluating Isolated Simulation...' : 'Simulate What-If (Isolated Copy)'}</span>
          </button>

          <div style={{ fontSize: '0.72rem', color: 'var(--text-dim)', lineHeight: 1.4 }}>
            <Info size={12} style={{ display: 'inline', marginRight: '4px' }} />
            Scenarios run against an isolated memory clone. Your live operational simulation will NOT be modified until you confirm and apply.
          </div>
        </div>

        {/* Impact Prediction Dashboard */}
        <div className="surface-card">
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '16px' }}>
            <h3 style={{ fontSize: '1.1rem', fontWeight: 700 }}>
              2. Algorithmic Impact Assessment
            </h3>
            {previewResult && (
              <span
                data-testid="preview-mode-badge"
                style={{
                  fontSize: '0.68rem',
                  fontWeight: 800,
                  background: 'rgba(56, 189, 248, 0.15)',
                  border: '1px solid #38bdf8',
                  color: '#38bdf8',
                  padding: '3px 8px',
                  borderRadius: '12px',
                  letterSpacing: '0.05em',
                  textTransform: 'uppercase',
                }}
              >
                Isolated Preview
              </span>
            )}
          </div>

          {previewResult ? (
            <div>
              {/* Isolated Copy Notice */}
              <div
                style={{
                  background: 'rgba(56, 189, 248, 0.08)',
                  border: '1px solid rgba(56, 189, 248, 0.3)',
                  borderRadius: 'var(--radius-sm, 6px)',
                  padding: '10px 14px',
                  marginBottom: '16px',
                  fontSize: '0.8rem',
                  display: 'flex',
                  alignItems: 'center',
                  gap: '8px',
                  color: '#bae6fd',
                }}
              >
                <ShieldCheck size={16} style={{ color: '#38bdf8', flexShrink: 0 }} />
                <span>
                  <strong>Isolated Copy Execution:</strong> Live simulation remains running unchanged. Review the before/after cascade below.
                </span>
              </div>

              {/* Impact KPI summary */}
              <div
                style={{
                  display: 'grid',
                  gridTemplateColumns: 'repeat(4, 1fr)',
                  gap: '10px',
                  marginBottom: '16px',
                }}
              >
                <div className="surface-card" style={{ padding: '12px', textAlign: 'center' }}>
                  <div style={{ fontSize: '0.65rem', color: 'var(--text-dim)', textTransform: 'uppercase' }}>Affected Trains</div>
                  <div data-testid="kpi-affected" style={{ fontSize: '1.4rem', fontWeight: 800, color: 'var(--color-primary, #38bdf8)' }}>
                    {previewResult.totalAffectedTrains}
                  </div>
                </div>

                <div className="surface-card" style={{ padding: '12px', textAlign: 'center' }}>
                  <div style={{ fontSize: '0.65rem', color: 'var(--text-dim)', textTransform: 'uppercase' }}>Reallocated</div>
                  <div data-testid="kpi-reallocated" style={{ fontSize: '1.4rem', fontWeight: 800, color: '#10b981' }}>
                    {previewResult.reallocatedCount}
                  </div>
                </div>

                <div className="surface-card" style={{ padding: '12px', textAlign: 'center' }}>
                  <div style={{ fontSize: '0.65rem', color: 'var(--text-dim)', textTransform: 'uppercase' }}>Trains Held</div>
                  <div data-testid="kpi-held" style={{ fontSize: '1.4rem', fontWeight: 800, color: previewResult.heldCount > 0 ? '#ef4444' : '#94a3b8' }}>
                    {previewResult.heldCount}
                  </div>
                </div>

                <div className="surface-card" style={{ padding: '12px', textAlign: 'center' }}>
                  <div style={{ fontSize: '0.65rem', color: 'var(--text-dim)', textTransform: 'uppercase' }}>Conflicts Solved</div>
                  <div data-testid="kpi-conflicts" style={{ fontSize: '1.4rem', fontWeight: 800, color: '#a855f7' }}>
                    {previewResult.conflictsResolved}
                  </div>
                </div>
              </div>

              {/* Before vs After Impact Table */}
              <div style={{ marginBottom: '16px' }}>
                <div style={{ fontSize: '0.75rem', fontWeight: 700, color: 'var(--text-dim)', textTransform: 'uppercase', marginBottom: '8px' }}>
                  Before vs After Comparison (Isolated Preview)
                </div>

                <div style={{ maxHeight: '220px', overflowY: 'auto', border: '1px solid rgba(255,255,255,0.08)', borderRadius: '6px' }}>
                  <table style={{ width: '100%', borderCollapse: 'collapse', fontSize: '0.75rem' }}>
                    <thead>
                      <tr style={{ background: '#1e293b', color: '#94a3b8', textAlign: 'left', borderBottom: '1px solid rgba(255,255,255,0.1)' }}>
                        <th style={{ padding: '8px 10px' }}>Train</th>
                        <th style={{ padding: '8px 10px' }}>Platform (Before &rarr; After)</th>
                        <th style={{ padding: '8px 10px' }}>Delay Delta</th>
                        <th style={{ padding: '8px 10px' }}>Status</th>
                      </tr>
                    </thead>
                    <tbody>
                      {previewResult.diffs.map((diff) => (
                        <tr
                          key={diff.trainId}
                          data-testid="what-if-diff-row"
                          style={{
                            borderBottom: '1px solid rgba(255,255,255,0.05)',
                            background: diff.isHeld ? 'rgba(239, 68, 68, 0.08)' : diff.isReallocated ? 'rgba(16, 185, 129, 0.08)' : 'transparent',
                          }}
                        >
                          <td style={{ padding: '8px 10px', fontWeight: 700, color: '#f8fafc' }}>
                            {diff.trainNumber}
                            <div style={{ fontSize: '0.68rem', color: '#94a3b8', fontWeight: 400 }}>{diff.trainName}</div>
                          </td>
                          <td style={{ padding: '8px 10px' }}>
                            <span style={{ color: '#94a3b8' }}>
                              {diff.beforePlatformNumber ? `P${diff.beforePlatformNumber}` : 'None'}
                            </span>
                            <ArrowRight size={12} style={{ display: 'inline', margin: '0 6px', color: '#64748b' }} />
                            <span
                              style={{
                                fontWeight: 700,
                                color: diff.afterPlatformNumber ? '#38bdf8' : '#ef4444',
                              }}
                            >
                              {diff.afterPlatformNumber ? `P${diff.afterPlatformNumber}` : 'HELD (No Plat)'}
                            </span>
                          </td>
                          <td style={{ padding: '8px 10px', fontFamily: 'var(--font-mono)' }}>
                            <span style={{ color: '#94a3b8' }}>{diff.beforeDelayMinutes}m</span>
                            <ArrowRight size={12} style={{ display: 'inline', margin: '0 6px', color: '#64748b' }} />
                            <span style={{ fontWeight: 700, color: diff.afterDelayMinutes > diff.beforeDelayMinutes ? '#f59e0b' : '#10b981' }}>
                              {diff.afterDelayMinutes}m
                            </span>
                          </td>
                          <td style={{ padding: '8px 10px' }}>
                            <span
                              style={{
                                padding: '2px 6px',
                                borderRadius: '4px',
                                fontSize: '0.68rem',
                                fontWeight: 700,
                                background: diff.isHeld
                                  ? 'rgba(239, 68, 68, 0.2)'
                                  : diff.isReallocated
                                  ? 'rgba(16, 185, 129, 0.2)'
                                  : 'rgba(255, 255, 255, 0.1)',
                                color: diff.isHeld
                                  ? '#fca5a5'
                                  : diff.isReallocated
                                  ? '#6ee7b7'
                                  : '#cbd5e1',
                              }}
                            >
                              {diff.afterStatus}
                            </span>
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              </div>

              {/* Apply to Live Confirmation Action */}
              <div
                style={{
                  display: 'flex',
                  gap: '12px',
                  alignItems: 'center',
                  background: '#1e293b',
                  padding: '12px 16px',
                  borderRadius: 'var(--radius-sm, 6px)',
                  border: '1px solid rgba(255,255,255,0.1)',
                }}
              >
                <div style={{ flex: 1, fontSize: '0.78rem', color: '#94a3b8' }}>
                  Ready to deploy these changes to the live simulator?
                </div>
                <button
                  type="button"
                  data-testid="btn-apply-to-live"
                  className="btn btn-primary"
                  onClick={handleApplyToLive}
                  disabled={isApplying || isSimulating}
                  style={{
                    background: '#10b981',
                    borderColor: '#10b981',
                    display: 'flex',
                    alignItems: 'center',
                    gap: '6px',
                    padding: '8px 14px',
                  }}
                >
                  <Zap size={15} />
                  <span>{isApplying ? 'Applying to Live...' : 'Apply to Live Simulation'}</span>
                </button>
              </div>
            </div>
          ) : (
            <div style={{ textAlign: 'center', padding: '60px 20px', color: 'var(--text-dim)' }}>
              <Sliders size={40} style={{ margin: '0 auto 12px', opacity: 0.3 }} />
              <div>No scenario run yet. Configure a delay or platform closure to observe cascading reallocation on an isolated copy.</div>
            </div>
          )}
        </div>
      </div>
    </div>
  );
};
