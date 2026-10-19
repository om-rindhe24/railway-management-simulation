# Design System: Control-Room Operations Dashboard

## 1. Aesthetic Philosophy
The interface emulates a high-tech modern railway traffic management system (inspired by modern European and Japanese rail operation center displays). It combines dark control-room high-contrast legibility with sleek glassmorphism, crisp typography, and responsive visual hierarchy.

## 2. Color Palette (Tailored HSL & Hex Tokens)

### Theme Tokens
```css
:root {
  /* Surface & Background */
  --bg-primary: #0a0f1c;       /* Dark navy base (#0a0f1c to #0f172a) */
  --bg-secondary: #0f172a;     /* Operations card background */
  --bg-surface: #1e293b;       /* Elevated panel */
  --bg-surface-glass: rgba(255, 255, 255, 0.05);

  /* Subtle Glassmorphism Tokens */
  --glass-bg: rgba(255, 255, 255, 0.05);
  --glass-backdrop: blur(16px) saturate(140%);
  --glass-border: 1px solid rgba(255, 255, 255, 0.12);
  --glass-shadow: 0 10px 30px -5px rgba(0, 0, 0, 0.6), inset 0 1px 0 0 rgba(255, 255, 255, 0.15);
  
  /* Borders & Dividers */
  --border-subtle: rgba(255, 255, 255, 0.08);
  --border-card: rgba(255, 255, 255, 0.12);
  --border-glow: rgba(34, 211, 238, 0.45);
  
  /* Brand & Telemetry */
  --color-primary: #22d3ee;     /* Cyan #22d3ee - active telemetry and selection */
  --color-primary-glow: rgba(34, 211, 238, 0.25);
  --color-accent: #38bdf8;

  /* Railway Line Palette */
  --color-line-up: #10b981;     /* Emerald green - UP Lines (Southbound) */
  --color-line-dn: #f97316;     /* Orange - DOWN Lines (Northbound) */
  --color-line-third: #a855f7;  /* Purple - 3rd line */
  
  /* Status Colors */
  --color-success: #10b981;     /* Emerald green - On Time / Available / Clear Signal */
  --color-warning: #f59e0b;     /* Amber - Caution / Delayed */
  --color-danger: #ef4444;      /* Crimson - Stop Signal / Conflict / Closed */
  --color-info: #06b6d4;        /* Cyan */
  --color-neutral: #94a3b8;     /* Muted slate text */
  
  /* Text */
  --text-main: #f8fafc;
  --text-muted: #94a3b8;
  --text-dim: #64748b;
  
  /* Fonts */
  --font-sans: 'Inter', system-ui, -apple-system, sans-serif;
  --font-mono: 'JetBrains Mono', 'Fira Code', monospace;
}
```

## 3. Train Category Badge Specifications
* **Emergency / Special**: Red pill badge (`#ef4444`), pulse animation
* **Premium (Rajdhani/Shatabdi style)**: Gold gradient pill badge (`#f59e0b` to `#fbbf24`)
* **Superfast**: Purple pill badge (`#a855f7`)
* **Express / Mail**: Royal Blue pill badge (`#3b82f6`)
* **Suburban Local**: Emerald Green pill badge (`#10b981`)
* **Freight / Goods**: Slate Gray pill badge (`#64748b`)

## 4. UI Components & Layouts
1. **Top Bar**:
   * Simulation Clock with live digital flip-counter style.
   * Simulation Speed Controls (`1x`, `2x`, `5x`, `10x`), Pause/Play, Reset.
   * Quick Status indicator ("Corridor Operational", "1 Conflict Detected").
   * Theme switcher (Dark / Light mode toggle with local storage persistence).
2. **Sidebar**:
   * Icon + text navigation: Overview, Live Simulation, Gantt Timeline, Network Map, What-If Simulator, Algorithm Lab, Train Management, Allocation History, Analytics.
   * 1-Click "Run Demo Scenario" CTA button highlighted in gold/primary glow.
3. **KPI Cards**:
   * Glassmorphic cards with subtle hover lift and glow border.
   * Metric value in bold typography with status indicator pills (+2 vs scheduled).
4. **Gantt Platform Occupancy Timeline**:
   * Y-axis: Platform rows (Platform 1, Platform 2, ...).
   * X-axis: Time intervals with dynamic current-time needle.
   * Colored block segments with train number, arrival/departure tags, and conflict red striped overlays.
5. **Corridor Schematic Map**:
   * 24 stations from Virar to Mumbai Central rendered as interactive network nodes.
   * Dual tracks: Blue line for Fast Line, Green line for Slow Line.
   * Animated markers for moving trains between stations.
6. **Live Train Spot (Geographic Corridor Map)**:
   * Real geographic interactive Leaflet map styled in control-room dark/light theme (CartoDB Dark Matter / Positron).
   * Fast Line (`#38bdf8`) and Slow Line (`#10b981`) track polylines with lateral visual offset.
   * Compact train pill cards with train number, speed/status, directional indicator (`▲ DN`, `▼ UP`), and delay badges.
   * Danger pulse animation (`pulse-danger`) for waiting trains and red signals.
   * 4-aspect signal heads with active lamp glow (🔴, 🟡, 🟡🟡, 🟢).
   * Floating blurred control-room legend and sector viewport controls (North, Central, South, Full).

