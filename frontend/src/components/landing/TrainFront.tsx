import React from 'react';

export interface TrainFrontProps {
  /** Headlight glow and beam intensity (0 to 1) */
  lightIntensity?: number;
  className?: string;
  style?: React.CSSProperties;
}

/**
 * Front view of Mumbai Western Railway EMU suburban locomotive.
 * Clean, flat-modern vector rendering adhering to the signal palette tokens.
 */
export const TrainFront: React.FC<TrainFrontProps> = ({
  lightIntensity = 1,
  className,
  style,
}) => {
  const intensity = Math.max(0, Math.min(1, lightIntensity));

  return (
    <svg
      viewBox="0 0 400 360"
      fill="none"
      xmlns="http://www.w3.org/2000/svg"
      className={className}
      style={{
        width: '100%',
        height: '100%',
        display: 'block',
        overflow: 'visible',
        ...style,
      }}
    >
      <defs>
        {/* Headlight radial glow gradients */}
        <radialGradient id="headlightGlowL" cx="50%" cy="50%" r="50%">
          <stop offset="0%" stopColor="var(--rf-amber, #FFB020)" stopOpacity={0.9 * intensity} />
          <stop offset="35%" stopColor="var(--rf-amber, #FFB020)" stopOpacity={0.45 * intensity} />
          <stop offset="70%" stopColor="var(--rf-amber, #FFB020)" stopOpacity={0.15 * intensity} />
          <stop offset="100%" stopColor="var(--rf-amber, #FFB020)" stopOpacity="0" />
        </radialGradient>
        <radialGradient id="headlightGlowR" cx="50%" cy="50%" r="50%">
          <stop offset="0%" stopColor="var(--rf-amber, #FFB020)" stopOpacity={0.9 * intensity} />
          <stop offset="35%" stopColor="var(--rf-amber, #FFB020)" stopOpacity={0.45 * intensity} />
          <stop offset="70%" stopColor="var(--rf-amber, #FFB020)" stopOpacity={0.15 * intensity} />
          <stop offset="100%" stopColor="var(--rf-amber, #FFB020)" stopOpacity="0" />
        </radialGradient>

        {/* Subtle cab side bevel shader */}
        <linearGradient id="cabBodyGradient" x1="0" y1="0" x2="1" y2="0">
          <stop offset="0%" stopColor="#D5CDC0" />
          <stop offset="12%" stopColor="var(--rf-train-body, #EDE6D6)" />
          <stop offset="88%" stopColor="var(--rf-train-body, #EDE6D6)" />
          <stop offset="100%" stopColor="#D5CDC0" />
        </linearGradient>

        {/* Windscreen dark glass tint */}
        <linearGradient id="windscreenGlass" x1="0" y1="0" x2="0" y2="1">
          <stop offset="0%" stopColor="var(--rf-bg, #0B0C10)" />
          <stop offset="60%" stopColor="var(--rf-bg-2, #14161D)" />
          <stop offset="100%" stopColor="var(--rf-bg, #0B0C10)" />
        </linearGradient>

        {/* Cowcatcher grill pattern */}
        <linearGradient id="skirtGrad" x1="0" y1="0" x2="0" y2="1">
          <stop offset="0%" stopColor="var(--rf-bg-2, #14161D)" />
          <stop offset="100%" stopColor="var(--rf-bg, #0B0C10)" />
        </linearGradient>
      </defs>

      {/* ── 1. ROOF & PANTOGRAPH MOUNT ── */}
      <path
        d="M 68 85 Q 200 48 332 85 L 340 98 Q 200 62 60 98 Z"
        fill="var(--rf-line, #2A2D38)"
      />
      {/* Roof cowl top line */}
      <path
        d="M 100 55 L 140 42 L 260 42 L 300 55"
        stroke="var(--rf-muted, #9A9CA8)"
        strokeWidth="2.5"
        strokeLinecap="round"
      />
      <rect x="175" y="36" width="50" height="7" rx="2" fill="var(--rf-line, #2A2D38)" />

      {/* ── 2. MAIN CAB BODY (WARM OFF-WHITE) ── */}
      <path
        d="M 60 92 Q 200 60 340 92 L 358 245 L 352 288 L 48 288 L 42 245 Z"
        fill="url(#cabBodyGradient)"
        stroke="var(--rf-line, #2A2D38)"
        strokeWidth="2"
      />

      {/* ── 3. DESTINATION DISPLAY BOX (VIRAR) ── */}
      <rect
        x="132"
        y="68"
        width="136"
        height="26"
        rx="4"
        fill="var(--rf-bg, #0B0C10)"
        stroke="var(--rf-amber, #FFB020)"
        strokeWidth="1.5"
      />
      <text
        x="200"
        y="86"
        textAnchor="middle"
        fill="var(--rf-amber, #FFB020)"
        fontFamily="monospace"
        fontSize="12.5"
        fontWeight="800"
        letterSpacing="2.5"
      >
        VIRAR
      </text>

      {/* ── 4. WINDSCREEN & CAB GLASS ── */}
      {/* Rubber gasket / outer frame */}
      <rect x="56" y="100" width="138" height="102" rx="7" fill="var(--rf-line, #2A2D38)" />
      <rect x="206" y="100" width="138" height="102" rx="7" fill="var(--rf-line, #2A2D38)" />

      {/* Left Pane */}
      <rect x="60" y="104" width="130" height="94" rx="5" fill="url(#windscreenGlass)" />
      {/* Right Pane */}
      <rect x="210" y="104" width="130" height="94" rx="5" fill="url(#windscreenGlass)" />

      {/* Glass reflections */}
      <polygon points="68,107 100,107 80,192 68,192" fill="rgba(245, 241, 232, 0.08)" />
      <polygon points="218,107 250,107 230,192 218,192" fill="rgba(245, 241, 232, 0.08)" />

      {/* Center pillar separator */}
      <rect x="194" y="98" width="12" height="108" fill="var(--rf-line, #2A2D38)" />

      {/* Windscreen Wipers */}
      <line x1="88" y1="195" x2="135" y2="148" stroke="var(--rf-muted, #9A9CA8)" strokeWidth="2.5" strokeLinecap="round" />
      <line x1="238" y1="195" x2="285" y2="148" stroke="var(--rf-muted, #9A9CA8)" strokeWidth="2.5" strokeLinecap="round" />

      {/* ── 5. WESTERN RAILWAY RED STRIPE ── */}
      <path
        d="M 44 220 L 356 220 L 353 268 L 47 268 Z"
        fill="var(--rf-red, #E5383B)"
        stroke="var(--rf-line, #2A2D38)"
        strokeWidth="1.5"
      />
      {/* Decorative center crest line */}
      <line x1="150" y1="244" x2="250" y2="244" stroke="var(--rf-text, #F5F1E8)" strokeWidth="3" strokeLinecap="round" opacity="0.9" />
      <circle cx="200" cy="244" r="7" fill="var(--rf-amber, #FFB020)" stroke="var(--rf-bg, #0B0C10)" strokeWidth="2" />

      {/* Upper Marker / Classification Lights */}
      <circle cx="84" cy="208" r="4.5" fill="var(--rf-red, #E5383B)" stroke="var(--rf-bg, #0B0C10)" strokeWidth="1.5" />
      <circle cx="316" cy="208" r="4.5" fill="var(--rf-red, #E5383B)" stroke="var(--rf-bg, #0B0C10)" strokeWidth="1.5" />

      {/* ── 6. HEADLIGHTS (AMBER) WITH DYNAMIC GLOW ── */}
      {/* Left Headlight */}
      {/* Soft Radial Glow halo */}
      <circle className="train-headlight-glow" cx="84" cy="244" r="54" fill="url(#headlightGlowL)" />
      {/* Housing bezel */}
      <circle cx="84" cy="244" r="17" fill="var(--rf-bg-2, #14161D)" stroke="var(--rf-line, #2A2D38)" strokeWidth="2" />
      <circle cx="84" cy="244" r="13" fill="var(--rf-amber, #FFB020)" />
      <circle cx="84" cy="244" r="6" fill="var(--rf-text, #F5F1E8)" opacity={0.8 + 0.2 * intensity} />

      {/* Right Headlight */}
      {/* Soft Radial Glow halo */}
      <circle className="train-headlight-glow" cx="316" cy="244" r="54" fill="url(#headlightGlowR)" />
      {/* Housing bezel */}
      <circle cx="316" cy="244" r="17" fill="var(--rf-bg-2, #14161D)" stroke="var(--rf-line, #2A2D38)" strokeWidth="2" />
      <circle cx="316" cy="244" r="13" fill="var(--rf-amber, #FFB020)" />
      <circle cx="316" cy="244" r="6" fill="var(--rf-text, #F5F1E8)" opacity={0.8 + 0.2 * intensity} />

      {/* ── 7. LOWER CHASSIS & SKIRT ── */}
      <path
        d="M 48 288 L 352 288 L 344 322 L 56 322 Z"
        fill="url(#skirtGrad)"
        stroke="var(--rf-line, #2A2D38)"
        strokeWidth="2"
      />

      {/* Side buffers */}
      {/* Left buffer */}
      <rect x="64" y="302" width="22" height="18" rx="3" fill="var(--rf-line, #2A2D38)" />
      <ellipse cx="75" cy="311" rx="14" ry="9" fill="#1C1E26" stroke="var(--rf-muted, #9A9CA8)" strokeWidth="1.5" />
      {/* Right buffer */}
      <rect x="314" y="302" width="22" height="18" rx="3" fill="var(--rf-line, #2A2D38)" />
      <ellipse cx="325" cy="311" rx="14" ry="9" fill="#1C1E26" stroke="var(--rf-muted, #9A9CA8)" strokeWidth="1.5" />

      {/* Central CBC Coupler */}
      <rect x="180" y="306" width="40" height="24" rx="4" fill="#0E1015" stroke="var(--rf-line, #2A2D38)" strokeWidth="2" />
      <circle cx="200" cy="318" r="6" fill="var(--rf-line, #2A2D38)" />
      <path d="M 194 322 L 206 322 L 200 334 Z" fill="var(--rf-muted, #9A9CA8)" />

      {/* Cowcatcher / Cattle Guard Slats */}
      <path
        d="M 68 322 L 180 348 L 220 348 L 332 322 L 324 354 L 214 354 L 186 354 L 76 354 Z"
        fill="var(--rf-bg, #0B0C10)"
        stroke="var(--rf-line, #2A2D38)"
        strokeWidth="1.5"
      />
      {/* Guard Vertical Slats */}
      <line x1="105" y1="328" x2="110" y2="352" stroke="var(--rf-line, #2A2D38)" strokeWidth="2" />
      <line x1="140" y1="335" x2="145" y2="352" stroke="var(--rf-line, #2A2D38)" strokeWidth="2" />
      <line x1="260" y1="335" x2="255" y2="352" stroke="var(--rf-line, #2A2D38)" strokeWidth="2" />
      <line x1="295" y1="328" x2="290" y2="352" stroke="var(--rf-line, #2A2D38)" strokeWidth="2" />
    </svg>
  );
};

export default TrainFront;
