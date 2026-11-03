import React from 'react';
import {
  LayoutDashboard,
  Grid,
  Clock,
  GitGraph,
  Sliders,
  Train as TrainIcon,
  History,
  BarChart3,
  ShieldCheck,
  Radar,
} from 'lucide-react';

interface SidebarProps {
  activeTab: string;
  onTabChange: (tab: string) => void;
}

export const Sidebar: React.FC<SidebarProps> = ({ activeTab, onTabChange }) => {
  const navItems = [
    { id: 'dashboard', label: 'Dashboard Overview', icon: LayoutDashboard },
    { id: 'train-spot', label: 'Live Train Spot', icon: Radar },
    { id: 'platforms', label: 'Platform Occupancy', icon: Grid },
    { id: 'timeline', label: 'Occupancy Timeline', icon: Clock },
    { id: 'map', label: 'Corridor Network Map', icon: GitGraph },
    { id: 'simulator', label: 'What-If Simulator', icon: Sliders },
    { id: 'trains', label: 'Train Management', icon: TrainIcon },
    { id: 'history', label: 'Allocation History', icon: History },
    { id: 'analytics', label: 'Operations Analytics', icon: BarChart3 },
  ];

  return (
    <aside className="sidebar">
      <div className="sidebar-header">
        <div className="sidebar-logo-icon">
          <TrainIcon size={22} />
        </div>
        <div>
          <div className="sidebar-brand-title">RAILFLOW ENGINE</div>
          <div className="sidebar-brand-sub">WR Corridor Simulator</div>
        </div>
      </div>

      <nav className="sidebar-nav">
        {navItems.map((item) => {
          const Icon = item.icon;
          const isActive = activeTab === item.id;
          return (
            <div
              key={item.id}
              className={`nav-item ${isActive ? 'active' : ''}`}
              onClick={() => onTabChange(item.id)}
            >
              <Icon size={18} />
              <span>{item.label}</span>
            </div>
          );
        })}
      </nav>

      <div style={{ padding: '16px', borderTop: '1px solid var(--border-subtle)', fontSize: '0.75rem', color: 'var(--text-dim)' }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: '6px', color: 'var(--color-success)', marginBottom: '4px', fontWeight: 600 }}>
          <ShieldCheck size={14} />
          <span>Simulation Mode Active</span>
        </div>
        <div>Virar - Mumbai Central</div>
        <div style={{ fontSize: '0.7rem', marginTop: '4px', color: 'var(--text-dim)' }}>
          Configurable simulation parameters
        </div>
      </div>
    </aside>
  );
};
