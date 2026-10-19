import React from 'react';
import { Play, Pause, RotateCcw, Sun, Moon, Sparkles, MapPin } from 'lucide-react';
import { SimulationState } from '../types';

interface TopBarProps {
  simulationState: SimulationState;
  selectedStation: string;
  onSelectStation: (code: string) => void;
  onStart: () => void;
  onPause: () => void;
  onReset: () => void;
  onSetSpeed: (speed: number) => void;
  onRunDemo: () => void;
  isLightMode: boolean;
  onToggleTheme: () => void;
}

export const TopBar: React.FC<TopBarProps> = ({
  simulationState,
  selectedStation,
  onSelectStation,
  onStart,
  onPause,
  onReset,
  onSetSpeed,
  onRunDemo,
  isLightMode,
  onToggleTheme,
}) => {
  // Format simulation ISO string to digital clock format HH:mm:ss
  const formatTime = (isoString?: string) => {
    if (!isoString) return '10:00:00';
    try {
      const d = new Date(isoString);
      return d.toTimeString().split(' ')[0];
    } catch {
      return '10:00:00';
    }
  };

  return (
    <header className="topbar">
      <div className="topbar-left">
        {/* Station Selector Hub */}
        <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
          <MapPin size={18} color="var(--color-primary)" />
          <select
            value={selectedStation}
            onChange={(e) => onSelectStation(e.target.value)}
            style={{
              background: 'var(--bg-surface)',
              color: 'var(--text-main)',
              border: '1px solid var(--border-card)',
              padding: '6px 12px',
              borderRadius: 'var(--radius-sm)',
              fontWeight: 600,
              cursor: 'pointer',
              outline: 'none',
            }}
          >
            <option value="BVI">Borivali (BVI) - 8 Platforms</option>
            <option value="AND">Andheri (AND) - 8 Platforms</option>
            <option value="DDR">Dadar (DDR) - 7 Platforms</option>
            <option value="VR">Virar (VR) - 6 Platforms</option>
            <option value="BA">Bandra (BA) - 6 Platforms</option>
            <option value="MMCT">Mumbai Central (MMCT) - 5 Platforms</option>
            <option value="BSR">Vasai Road (BSR) - 5 Platforms</option>
          </select>
        </div>

        {/* Digital Simulation Clock */}
        <div className="sim-clock">
          <span style={{ fontSize: '0.72rem', textTransform: 'uppercase', color: 'var(--text-dim)' }}>
            SIM CLOCK
          </span>
          <span className="sim-clock-digital">{formatTime(simulationState?.simulationTime)}</span>
        </div>

        {/* Simulation Play/Pause/Reset Controls */}
        <div style={{ display: 'flex', alignItems: 'center', gap: '6px' }}>
          {simulationState?.running ? (
            <button className="btn-icon" onClick={onPause} title="Pause Simulation">
              <Pause size={16} />
            </button>
          ) : (
            <button className="btn-icon" onClick={onStart} title="Start Simulation">
              <Play size={16} fill="var(--color-primary)" />
            </button>
          )}

          <button className="btn-icon" onClick={onReset} title="Reset Simulation">
            <RotateCcw size={16} />
          </button>
        </div>

        {/* Speed Multipliers */}
        <div className="speed-button-group">
          {[1, 2, 5, 10].map((s) => (
            <button
              key={s}
              className={`speed-btn ${simulationState?.speedMultiplier === s ? 'active' : ''}`}
              onClick={() => onSetSpeed(s)}
            >
              {s}x
            </button>
          ))}
        </div>
      </div>

      <div className="topbar-right">
        {/* Placement 1-Click Demo Scenario CTA */}
        <button className="btn-demo-cta" onClick={onRunDemo}>
          <Sparkles size={16} />
          <span>Run Demo Scenario</span>
        </button>

        {/* Dark / Light Mode Toggle */}
        <button className="btn-icon" onClick={onToggleTheme} title="Toggle Theme">
          {isLightMode ? <Moon size={16} /> : <Sun size={16} />}
        </button>
      </div>
    </header>
  );
};
