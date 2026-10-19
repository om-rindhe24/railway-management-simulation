import React, { useState, useEffect } from 'react';
import { AlgorithmInfo } from '../types';
import { api } from '../services/api';
import {
  Cpu,
  Terminal,
  CheckCircle,
  Code,
  ShieldCheck,
  Zap,
  ArrowRight,
  Radio,
  Layers,
  Activity,
  Workflow,
} from 'lucide-react';

export const AlgorithmLab: React.FC = () => {
  const [algorithms, setAlgorithms] = useState<AlgorithmInfo[]>([]);
  const [selectedAlgo, setSelectedAlgo] = useState<AlgorithmInfo | null>(null);

  useEffect(() => {
    api.getAlgorithmMetadata()
      .then((data) => {
        setAlgorithms(data);
        if (data.length > 0) setSelectedAlgo(data[0]);
      })
      .catch((err) => console.error('Failed to load algorithm metadata:', err));
  }, []);

  const isSignallingAlgo = selectedAlgo?.name.toLowerCase().includes('signalling');

  return (
    <div className="page-container">
      <div className="card-header" style={{ marginBottom: '20px' }}>
        <div>
          <h2 style={{ fontSize: '1.4rem', fontWeight: 800 }}>
            DSA Algorithm Lab & Viva Defense Showcase
          </h2>
          <p style={{ color: 'var(--text-muted)', fontSize: '0.85rem' }}>
            Technical breakdown of core Data Structures & Algorithms powering scheduling, graph traversal, interval conflict detection, dynamic reallocation, and simulation signalling state evaluation.
          </p>
        </div>

        <div
          style={{
            background: 'rgba(56, 189, 248, 0.1)',
            color: 'var(--color-primary)',
            border: '1px solid var(--border-glow)',
            padding: '6px 14px',
            borderRadius: 'var(--radius-full)',
            fontSize: '0.75rem',
            fontWeight: 700,
          }}
        >
          Placement-Ready Viva Panel
        </div>
      </div>

      <div style={{ display: 'grid', gridTemplateColumns: '320px 1fr', gap: '24px' }}>
        {/* Algorithm List Navigation */}
        <div className="surface-card" style={{ padding: '16px' }}>
          <div
            style={{
              fontSize: '0.75rem',
              fontWeight: 700,
              textTransform: 'uppercase',
              color: 'var(--text-dim)',
              marginBottom: '12px',
              paddingLeft: '8px',
            }}
          >
            CORE ALGORITHMS & DSA ({algorithms.length})
          </div>

          <div style={{ display: 'flex', flexDirection: 'column', gap: '8px' }}>
            {algorithms.map((algo, idx) => {
              const isSelected = selectedAlgo?.name === algo.name;
              return (
                <div
                  key={idx}
                  style={{
                    background: isSelected ? 'rgba(56, 189, 248, 0.12)' : 'var(--glass-bg)',
                    border: `1px solid ${isSelected ? 'var(--color-primary)' : 'var(--glass-border)'}`,
                    borderRadius: 'var(--radius-md)',
                    padding: '12px 14px',
                    cursor: 'pointer',
                    transition: 'all 0.2s',
                  }}
                  onClick={() => setSelectedAlgo(algo)}
                >
                  <div
                    style={{
                      fontWeight: 700,
                      fontSize: '0.9rem',
                      color: isSelected ? 'var(--color-primary)' : 'var(--text-main)',
                    }}
                  >
                    {algo.name}
                  </div>
                  <div style={{ fontSize: '0.72rem', color: 'var(--text-muted)', marginTop: '2px' }}>
                    {algo.dataStructure.split('(')[0]}
                  </div>
                </div>
              );
            })}
          </div>
        </div>

        {/* Selected Algorithm Deep-Dive Details */}
        {selectedAlgo && (
          <div className="surface-card">
            <div
              style={{
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'space-between',
                marginBottom: '20px',
                paddingBottom: '16px',
                borderBottom: '1px solid var(--border-subtle)',
              }}
            >
              <div>
                <h3 style={{ fontSize: '1.35rem', fontWeight: 800 }}>{selectedAlgo.name}</h3>
                <div
                  style={{
                    fontFamily: 'var(--font-mono)',
                    fontSize: '0.8rem',
                    color: 'var(--color-primary)',
                    marginTop: '2px',
                  }}
                >
                  {selectedAlgo.dataStructure}
                </div>
              </div>

              <div style={{ display: 'flex', gap: '10px' }}>
                <span
                  className="badge"
                  style={{
                    background: 'var(--glass-bg)',
                    border: '1px solid var(--glass-border)',
                    fontFamily: 'var(--font-mono)',
                  }}
                >
                  Time: {selectedAlgo.timeComplexity.split('|')[0]}
                </span>
                <span
                  className="badge"
                  style={{
                    background: 'var(--glass-bg)',
                    border: '1px solid var(--glass-border)',
                    fontFamily: 'var(--font-mono)',
                  }}
                >
                  Space: {selectedAlgo.spaceComplexity.split('where')[0]}
                </span>
              </div>
            </div>

            {/* PART 11 SPECIAL VISUALIZATION: SIGNALLING PIPELINE */}
            {isSignallingAlgo && (
              <div
                className="glass-panel"
                style={{
                  padding: '20px',
                  marginBottom: '24px',
                }}
              >
                <div
                  style={{
                    fontSize: '0.8rem',
                    fontWeight: 800,
                    textTransform: 'uppercase',
                    color: 'var(--color-primary)',
                    marginBottom: '16px',
                    display: 'flex',
                    alignItems: 'center',
                    gap: '8px',
                  }}
                >
                  <Workflow size={18} />
                  <span>Educational Signalling Evaluation Pipeline</span>
                </div>

                {/* 5-Stage Architecture Flow */}
                <div
                  style={{
                    display: 'grid',
                    gridTemplateColumns: 'repeat(5, 1fr)',
                    gap: '10px',
                    position: 'relative',
                  }}
                >
                  {/* Stage 1 */}
                  <div
                    className="surface-card"
                    style={{
                      padding: '12px 10px',
                      textAlign: 'center',
                    }}
                  >
                    <div style={{ fontSize: '0.68rem', color: 'var(--text-dim)', fontWeight: 700 }}>STAGE 1</div>
                    <div style={{ fontWeight: 800, fontSize: '0.82rem', color: 'var(--color-primary)', marginTop: '4px' }}>
                      Track Block
                    </div>
                    <div style={{ fontSize: '0.7rem', color: 'var(--text-muted)', marginTop: '4px' }}>
                      Discrete topological segment (BLK-*)
                    </div>
                  </div>

                  {/* Stage 2 */}
                  <div
                    className="surface-card"
                    style={{
                      padding: '12px 10px',
                      textAlign: 'center',
                    }}
                  >
                    <div style={{ fontSize: '0.68rem', color: 'var(--text-dim)', fontWeight: 700 }}>STAGE 2</div>
                    <div style={{ fontWeight: 800, fontSize: '0.82rem', color: '#f59e0b', marginTop: '4px' }}>
                      Occupancy
                    </div>
                    <div style={{ fontSize: '0.7rem', color: 'var(--text-muted)', marginTop: '4px' }}>
                      Train berthing or track maintenance lock
                    </div>
                  </div>

                  {/* Stage 3 */}
                  <div
                    className="surface-card"
                    style={{
                      borderColor: 'var(--color-primary)',
                      padding: '12px 10px',
                      textAlign: 'center',
                      boxShadow: '0 0 10px rgba(56, 189, 248, 0.2)',
                    }}
                  >
                    <div style={{ fontSize: '0.68rem', color: 'var(--color-primary)', fontWeight: 700 }}>STAGE 3</div>
                    <div style={{ fontWeight: 800, fontSize: '0.82rem', color: 'var(--color-primary)', marginTop: '4px' }}>
                      SignalStateEngine
                    </div>
                    <div style={{ fontSize: '0.7rem', color: 'var(--text-muted)', marginTop: '4px' }}>
                      O(S) sequential multi-aspect evaluator
                    </div>
                  </div>

                  {/* Stage 4 */}
                  <div
                    className="surface-card"
                    style={{
                      padding: '12px 10px',
                      textAlign: 'center',
                    }}
                  >
                    <div style={{ fontSize: '0.68rem', color: 'var(--text-dim)', fontWeight: 700 }}>STAGE 4</div>
                    <div style={{ fontWeight: 800, fontSize: '0.82rem', color: '#10b981', marginTop: '4px' }}>
                      Signal Aspect
                    </div>
                    <div style={{ fontSize: '0.7rem', color: 'var(--text-muted)', marginTop: '4px' }}>
                      RED ➔ YELLOW ➔ 2xYELLOW ➔ GREEN
                    </div>
                  </div>

                  {/* Stage 5 */}
                  <div
                    className="surface-card"
                    style={{
                      padding: '12px 10px',
                      textAlign: 'center',
                    }}
                  >
                    <div style={{ fontSize: '0.68rem', color: 'var(--text-dim)', fontWeight: 700 }}>STAGE 5</div>
                    <div style={{ fontWeight: 800, fontSize: '0.82rem', color: '#818cf8', marginTop: '4px' }}>
                      Train Movement
                    </div>
                    <div style={{ fontSize: '0.7rem', color: 'var(--text-muted)', marginTop: '4px' }}>
                      Wait at RED or proceed at speed limit
                    </div>
                  </div>
                </div>

                <div
                  className="surface-card"
                  style={{
                    marginTop: '14px',
                    fontSize: '0.78rem',
                    color: 'var(--text-muted)',
                    padding: '10px 14px',
                    lineHeight: 1.5,
                  }}
                >
                  <strong style={{ color: 'var(--text-main)' }}>Signalling Multi-Aspect Fallback Rule:</strong> If protected block is occupied or closed ➔ <strong>RED (STOP)</strong>; else if next signal is RED ➔ <strong>YELLOW (CAUTION)</strong>; else if next signal is YELLOW ➔ <strong>DOUBLE YELLOW (ADVANCED CAUTION)</strong>; else ➔ <strong>GREEN (CLEAR / PROCEED)</strong>.
                </div>
              </div>
            )}

            {/* Purpose & Objective */}
            <div style={{ marginBottom: '20px' }}>
              <div
                style={{
                  fontSize: '0.75rem',
                  fontWeight: 700,
                  textTransform: 'uppercase',
                  color: 'var(--text-dim)',
                  marginBottom: '6px',
                }}
              >
                Algorithmic Objective
              </div>
              <p style={{ fontSize: '0.92rem', color: 'var(--text-main)', lineHeight: 1.6 }}>
                {selectedAlgo.purpose}
              </p>
            </div>

            {/* Code Implementation Location */}
            <div
              className="surface-card"
              style={{
                padding: '12px 16px',
                marginBottom: '24px',
              }}
            >
              <div
                style={{
                  display: 'flex',
                  alignItems: 'center',
                  gap: '8px',
                  fontSize: '0.75rem',
                  color: 'var(--color-primary)',
                  fontWeight: 700,
                  marginBottom: '4px',
                }}
              >
                <Code size={14} />
                <span>SOURCE CODE LOCATION & USAGE</span>
              </div>
              <div style={{ fontFamily: 'var(--font-mono)', fontSize: '0.82rem', color: 'var(--text-muted)' }}>
                {selectedAlgo.whereUsed}
              </div>
            </div>

            {/* Complexity Specs */}
            <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '16px', marginBottom: '24px' }}>
              <div
                className="surface-card"
                style={{
                  padding: '16px',
                }}
              >
                <div
                  style={{
                    fontSize: '0.72rem',
                    textTransform: 'uppercase',
                    color: 'var(--text-dim)',
                    fontWeight: 700,
                    marginBottom: '6px',
                  }}
                >
                  Time Complexity
                </div>
                <div
                  style={{
                    fontFamily: 'var(--font-mono)',
                    fontWeight: 700,
                    fontSize: '0.95rem',
                    color: 'var(--color-warning)',
                  }}
                >
                  {selectedAlgo.timeComplexity}
                </div>
              </div>

              <div
                className="surface-card"
                style={{
                  padding: '16px',
                }}
              >
                <div
                  style={{
                    fontSize: '0.72rem',
                    textTransform: 'uppercase',
                    color: 'var(--text-dim)',
                    fontWeight: 700,
                    marginBottom: '6px',
                  }}
                >
                  Space Complexity
                </div>
                <div
                  style={{
                    fontFamily: 'var(--font-mono)',
                    fontWeight: 700,
                    fontSize: '0.95rem',
                    color: 'var(--color-accent)',
                  }}
                >
                  {selectedAlgo.spaceComplexity}
                </div>
              </div>
            </div>

            {/* Placement Interview Key Viva Points */}
            <div>
              <div
                style={{
                  fontSize: '0.8rem',
                  fontWeight: 800,
                  textTransform: 'uppercase',
                  color: 'var(--color-success)',
                  marginBottom: '12px',
                  display: 'flex',
                  alignItems: 'center',
                  gap: '6px',
                }}
              >
                <Zap size={16} />
                <span>INTERVIEW & PLACEMENT TALKING POINTS</span>
              </div>

              <div style={{ display: 'flex', flexDirection: 'column', gap: '8px' }}>
                {selectedAlgo.interviewKeyPoints.map((point, pIdx) => (
                  <div
                    key={pIdx}
                    style={{
                      display: 'flex',
                      alignItems: 'flex-start',
                      gap: '10px',
                      background: 'rgba(16, 185, 129, 0.05)',
                      border: '1px solid rgba(16, 185, 129, 0.2)',
                      padding: '10px 14px',
                      borderRadius: 'var(--radius-sm)',
                      fontSize: '0.85rem',
                    }}
                  >
                    <CheckCircle
                      size={16}
                      color="var(--color-success)"
                      style={{ marginTop: '2px', flexShrink: 0 }}
                    />
                    <span style={{ color: 'var(--text-main)' }}>{point}</span>
                  </div>
                ))}
              </div>
            </div>
          </div>
        )}
      </div>
    </div>
  );
};
