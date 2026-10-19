import React, { useState } from 'react';
import { Sparkles, CheckCircle, ArrowRight, X, Play, RotateCcw, AlertTriangle, ShieldCheck } from 'lucide-react';

interface DemoWalkthroughModalProps {
  isOpen: boolean;
  onClose: () => void;
  onRunInitialAllocation: () => Promise<any>;
  onInjectDelay: () => Promise<any>;
  onReset: () => Promise<any>;
  onNavigateTab: (tab: string) => void;
}

export const DemoWalkthroughModal: React.FC<DemoWalkthroughModalProps> = ({
  isOpen,
  onClose,
  onRunInitialAllocation,
  onInjectDelay,
  onReset,
  onNavigateTab,
}) => {
  const [currentStep, setCurrentStep] = useState(1);
  const [loading, setLoading] = useState(false);
  const [stepLogs, setStepLogs] = useState<string[]>([
    'System initialized with 24 Western Railway stations and configurable simulated platform configuration for the 24-station corridor.',
  ]);

  if (!isOpen) return null;

  const handleStep1 = async () => {
    setLoading(true);
    try {
      await onReset();
      setStepLogs((prev) => [...prev, 'Step 1: Simulation clock and corridor state reset to baseline (10:00:00).']);
      setCurrentStep(2);
    } catch (err: any) {
      alert(err.message);
    } finally {
      setLoading(false);
    }
  };

  const handleStep2 = async () => {
    setLoading(true);
    try {
      await onRunInitialAllocation();
      setStepLogs((prev) => [
        ...prev,
        'Step 2: Min-Heap Priority Queue evaluated upcoming trains and allocated optimal platforms at Borivali (BVI).',
      ]);
      setCurrentStep(3);
    } catch (err: any) {
      alert(err.message);
    } finally {
      setLoading(false);
    }
  };

  const handleStep3 = async () => {
    setLoading(true);
    try {
      await onInjectDelay();
      setStepLogs((prev) => [
        ...prev,
        'Step 3: Injected +15 min delay on Express Train. IntervalConflictDetector caught overlap on Platform 2.',
      ]);
      setStepLogs((prev) => [
        ...prev,
        'Step 4: DynamicReallocationEngine resolved the conflict by evaluating available platforms and reassigning the train.',
      ]);
      setCurrentStep(4);
    } catch (err: any) {
      alert(err.message);
    } finally {
      setLoading(false);
    }
  };

  const handleFinish = () => {
    onClose();
    onNavigateTab('timeline');
  };

  return (
    <div className="modal-overlay">
      <div className="modal-content" style={{ maxWidth: '650px' }}>
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '16px' }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
            <Sparkles size={20} color="var(--color-primary)" />
            <h3 style={{ fontSize: '1.25rem', fontWeight: 800 }}>
              Automated Viva / Placement Demo Walkthrough
            </h3>
          </div>
          <button className="btn-icon" onClick={onClose}>
            <X size={18} />
          </button>
        </div>

        <p style={{ color: 'var(--text-muted)', fontSize: '0.85rem', marginBottom: '20px' }}>
          Follow this guided demonstration to showcase the end-to-end algorithmic scheduling and dynamic reallocation lifecycle to evaluators.
        </p>

        {/* Stepper Visualizer */}
        <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: '24px', position: 'relative' }}>
          {[
            { num: 1, title: 'Reset' },
            { num: 2, title: 'Greedy Alloc' },
            { num: 3, title: 'Delay & Conflict' },
            { num: 4, title: 'Resolved' },
          ].map((s) => {
            const isDone = currentStep > s.num;
            const isCurr = currentStep === s.num;
            return (
              <div key={s.num} style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', zIndex: 2 }}>
                <div
                  style={{
                    width: '32px',
                    height: '32px',
                    borderRadius: '50%',
                    background: isDone ? 'var(--color-success)' : isCurr ? 'var(--color-primary)' : 'var(--bg-surface)',
                    color: isDone || isCurr ? '#0b0f19' : 'var(--text-dim)',
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'center',
                    fontWeight: 800,
                    fontSize: '0.8rem',
                    marginBottom: '4px',
                  }}
                >
                  {isDone ? <CheckCircle size={16} /> : s.num}
                </div>
                <span style={{ fontSize: '0.72rem', color: isCurr ? 'var(--color-primary)' : 'var(--text-muted)' }}>
                  {s.title}
                </span>
              </div>
            );
          })}
        </div>

        {/* Live Execution Logs Terminal */}
        <div
          style={{
            background: 'var(--bg-primary)',
            border: '1px solid var(--border-card)',
            borderRadius: 'var(--radius-sm)',
            padding: '14px',
            fontFamily: 'var(--font-mono)',
            fontSize: '0.78rem',
            maxHeight: '160px',
            overflowY: 'auto',
            marginBottom: '24px',
            display: 'flex',
            flexDirection: 'column',
            gap: '6px',
          }}
        >
          {stepLogs.map((log, idx) => (
            <div key={idx} style={{ color: idx === stepLogs.length - 1 ? 'var(--color-primary)' : 'var(--text-muted)' }}>
              &gt; {log}
            </div>
          ))}
        </div>

        {/* Step Action Buttons */}
        <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '12px' }}>
          {currentStep === 1 && (
            <button className="btn btn-primary" onClick={handleStep1} disabled={loading}>
              <RotateCcw size={14} />
              <span>Step 1: Reset to Baseline</span>
            </button>
          )}

          {currentStep === 2 && (
            <button className="btn btn-primary" onClick={handleStep2} disabled={loading}>
              <Play size={14} />
              <span>Step 2: Execute Greedy Platform Allocation</span>
            </button>
          )}

          {currentStep === 3 && (
            <button className="btn btn-primary" onClick={handleStep3} disabled={loading}>
              <AlertTriangle size={14} />
              <span>Step 3: Inject Delay & Reallocate Dynamically</span>
            </button>
          )}

          {currentStep === 4 && (
            <button className="btn btn-primary" onClick={handleFinish}>
              <ShieldCheck size={14} />
              <span>Inspect Occupancy Timeline & Audit Log</span>
            </button>
          )}
        </div>
      </div>
    </div>
  );
};
