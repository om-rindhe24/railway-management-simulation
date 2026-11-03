// ─────────────────────────────────────────────────────────────
// RAILFLOW ENGINE — Scroll-Scrubbed Video Hero & Landing Page
//
// Original component author: guglielmogiannattasio (via 21st.dev)
// Licence: Check license terms on the component's 21st.dev page.
//
// Western Railway Corridor Simulator Adaptations:
// 1. Release body lock when progress reaches ~0.98 to avoid permanent scroll trapping;
//    re-locks if user scrolls back into hero; cleans up lock on unmount.
// 2. Full keyboard support (ArrowDown/PageDown/Space to scrub forward, ArrowUp/PageUp backward).
// 3. Visible, accessible "Skip intro" button top-right.
// 4. prefers-reduced-motion immediately shows final frame & CTA without scroll trap.
// 5. Local video (/media/hero-train.mp4) with procedural animated SVG railway fallback.
// 6. Railway design tokens (dark navy #060913, cyan accent #22d3ee) and corridor telemetry.
// 7. Post-hero section featuring 3 core algorithm cards, live API stats, and Control Room CTA.
// 8. Tab visibility pausing and mobile-adapted scrub distances.
// ─────────────────────────────────────────────────────────────

import React, { useEffect, useRef, useState, useMemo, useCallback } from 'react';
import { api } from '../../services/api';
import {
  ArrowRight,
  Zap,
  Layers,
  Activity,
  Shuffle,
  ChevronDown,
  Train as TrainIcon,
  CheckCircle2,
} from 'lucide-react';

export interface ScrollVideoHeroProps {
  onEnterApp: () => void;
  stationsCount?: number;
  platformsCount?: number;
  trainsCount?: number;
  videoSrc?: string;
  title?: string;
  scrollHint?: string;
  tagline?: string;
  subtagline?: string;
}

function clamp(v: number, min: number, max: number) {
  return Math.min(max, Math.max(min, v));
}

export const ScrollVideoHero: React.FC<ScrollVideoHeroProps> = ({
  onEnterApp,
  stationsCount,
  platformsCount,
  trainsCount,
  videoSrc = '/media/hero-train.mp4',
  title = 'RAILFLOW ENGINE',
  scrollHint = 'SCROLL TO ENTER',
  tagline = 'Every platform. Every signal. One live corridor.',
  subtagline = 'Virar → Mumbai Central · 24 stations · fast & slow lines',
}) => {
  const containerRef = useRef<HTMLDivElement>(null);
  const heroSectionRef = useRef<HTMLDivElement>(null);
  const videoRef = useRef<HTMLVideoElement>(null);
  const titleRef = useRef<HTMLDivElement>(null);
  const hintRef = useRef<HTMLDivElement>(null);
  const taglineRef = useRef<HTMLDivElement>(null);
  const subtaglineRef = useRef<HTMLDivElement>(null);
  const progressBarRef = useRef<HTMLDivElement>(null);
  const featuresSectionRef = useRef<HTMLDivElement>(null);

  const [videoLoaded, setVideoLoaded] = useState(false);
  const [videoError, setVideoError] = useState(false);
  const [progressVal, setProgressVal] = useState(0);
  const [telemetry, setTelemetry] = useState<{ stations?: number; platforms?: number; trains?: number }>({});

  useEffect(() => {
    let isMounted = true;
    Promise.allSettled([
      api.getStations(),
      api.getPlatforms(),
      api.getTrains(),
    ]).then(([stnRes, platRes, trainRes]) => {
      if (!isMounted) return;
      setTelemetry({
        stations: stnRes.status === 'fulfilled' ? stnRes.value.length : undefined,
        platforms: platRes.status === 'fulfilled' ? platRes.value.length : undefined,
        trains: trainRes.status === 'fulfilled' ? trainRes.value.length : undefined,
      });
    }).catch(() => {});
    return () => { isMounted = false; };
  }, []);

  const displayStations = stationsCount !== undefined && stationsCount > 0 ? stationsCount : telemetry.stations;
  const displayPlatforms = platformsCount !== undefined && platformsCount > 0 ? platformsCount : telemetry.platforms;
  const displayTrains = trainsCount !== undefined && trainsCount > 0 ? trainsCount : telemetry.trains;

  // Responsive scrub distance: shorter on mobile
  const scrubDistance = useMemo(() => {
    if (typeof window === 'undefined') return 3000;
    return window.innerWidth < 768 ? 1600 : 3000;
  }, []);

  // Lock management
  const lockedRef = useRef(false);
  const lockedScrollYRef = useRef(0);

  const releaseLock = useCallback(() => {
    if (!lockedRef.current || typeof document === 'undefined') return;
    lockedRef.current = false;
    const y = lockedScrollYRef.current;
    const b = document.body.style;
    b.position = '';
    b.top = '';
    b.left = '';
    b.right = '';
    b.width = '';
    b.height = '';
    b.overscrollBehavior = '';
    window.scrollTo(0, y);
  }, []);

  const engageLock = useCallback(() => {
    if (lockedRef.current || typeof document === 'undefined') return;
    lockedRef.current = true;
    lockedScrollYRef.current = window.scrollY;
    const b = document.body.style;
    b.position = 'fixed';
    b.top = `-${lockedScrollYRef.current}px`;
    b.left = '0';
    b.right = '0';
    b.width = '100%';
    b.height = '100%';
    b.overscrollBehavior = 'none';
  }, []);

  // Enter app handler
  const handleEnter = useCallback(() => {
    releaseLock();
    onEnterApp();
  }, [releaseLock, onEnterApp]);

  useEffect(() => {
    const video = videoRef.current;
    const heroSection = heroSectionRef.current;
    if (!heroSection) return;

    const reduceMotion =
      typeof window !== 'undefined' &&
      window.matchMedia?.('(prefers-reduced-motion: reduce)').matches;

    // Reduced motion shortcut
    if (reduceMotion) {
      setProgressVal(1);
      releaseLock();
      return;
    }

    let duration = 0;
    let rafId = 0;
    let targetProgress = 0;
    let currentProgress = 0;
    let hasStartedScrolling = false;
    let isSeeking = false;
    let pendingTime: number | null = null;
    let touchStartY = 0;
    let isTabVisible = true;

    if (video) {
      const onLoadedData = () => {
        duration = video.duration || 0;
        setVideoLoaded(true);
      };
      const onVideoErr = () => {
        setVideoError(true);
      };
      video.addEventListener('loadeddata', onLoadedData);
      video.addEventListener('error', onVideoErr);

      // Silent play kickstart for browser autoplay policies
      const kickstart = () => {
        const p = video.play();
        if (p && typeof p.then === 'function') {
          p.then(() => video.pause()).catch(() => setVideoError(true));
        } else {
          video.pause();
        }
      };
      kickstart();

      const onSeeked = () => {
        isSeeking = false;
        if (pendingTime !== null) {
          const t = pendingTime;
          pendingTime = null;
          isSeeking = true;
          video.currentTime = t;
        }
      };
      video.addEventListener('seeked', onSeeked);
    }

    function seekTo(t: number) {
      if (!video) return;
      if (isSeeking) {
        pendingTime = t;
        return;
      }
      isSeeking = true;
      video.currentTime = t;
    }

    // Start locked initially
    engageLock();

    function addDelta(deltaY: number) {
      const next = clamp(targetProgress + deltaY / scrubDistance, 0, 1);
      targetProgress = next;
      if (targetProgress > 0.001) hasStartedScrolling = true;

      // Unlock at ~0.98 progress so user can naturally scroll into features section
      if (targetProgress >= 0.98) {
        releaseLock();
      } else if (targetProgress < 0.95 && window.scrollY <= 10) {
        // Re-lock when scrolling back up into hero
        engageLock();
      }
      return true;
    }

    const onWheel = (e: WheelEvent) => {
      // If locked, wheel drives progress
      if (lockedRef.current) {
        addDelta(e.deltaY);
        e.preventDefault();
      } else if (window.scrollY <= 5 && e.deltaY < 0 && targetProgress >= 0.95) {
        // Scrolling up at the top of the page re-engages hero scrub
        engageLock();
        addDelta(e.deltaY);
        e.preventDefault();
      }
    };

    const onTouchStart = (e: TouchEvent) => {
      touchStartY = e.touches[0]?.clientY ?? 0;
    };
    const onTouchMove = (e: TouchEvent) => {
      const y = e.touches[0]?.clientY ?? touchStartY;
      const deltaY = touchStartY - y;
      touchStartY = y;
      if (lockedRef.current) {
        addDelta(deltaY);
        e.preventDefault();
      } else if (window.scrollY <= 5 && deltaY < 0 && targetProgress >= 0.95) {
        engageLock();
        addDelta(deltaY);
        e.preventDefault();
      }
    };

    // Keyboard support for accessibility
    const onKeyDown = (e: KeyboardEvent) => {
      // Ignore if focus is in an input or button
      if (['INPUT', 'SELECT', 'TEXTAREA'].includes((e.target as HTMLElement)?.tagName)) {
        return;
      }

      if (['ArrowDown', 'PageDown', ' '].includes(e.key)) {
        if (lockedRef.current) {
          e.preventDefault();
          addDelta(scrubDistance * 0.15);
          if (targetProgress >= 0.98) {
            releaseLock();
            featuresSectionRef.current?.scrollIntoView({ behavior: 'smooth' });
          }
        }
      } else if (['ArrowUp', 'PageUp'].includes(e.key)) {
        if (window.scrollY <= 10) {
          if (!lockedRef.current && targetProgress >= 0.95) {
            engageLock();
          }
          if (lockedRef.current) {
            e.preventDefault();
            addDelta(-scrubDistance * 0.15);
          }
        }
      }
    };

    // Pause animation work when tab is hidden
    const onVisibilityChange = () => {
      isTabVisible = !document.hidden;
    };

    window.addEventListener('wheel', onWheel, { passive: false });
    window.addEventListener('touchstart', onTouchStart, { passive: true });
    window.addEventListener('touchmove', onTouchMove, { passive: false });
    window.addEventListener('keydown', onKeyDown);
    document.addEventListener('visibilitychange', onVisibilityChange);

    function frame() {
      if (isTabVisible) {
        currentProgress += (targetProgress - currentProgress) * 0.18;
        setProgressVal(currentProgress);

        if (duration > 0 && video) {
          seekTo(currentProgress * duration);
        }

        if (videoRef.current) {
          const scale = 1 + currentProgress * 0.08;
          videoRef.current.style.transform = `scale(${scale})`;
        }

        // Title transitions: fades and blurs out during first 35% of scrub
        if (titleRef.current) {
          const t = 1 - clamp(currentProgress / 0.35, 0, 1);
          titleRef.current.style.opacity = String(t);
          titleRef.current.style.transform = `translateY(${(1 - t) * -28}px) scale(${0.96 + t * 0.04})`;
          titleRef.current.style.filter = `blur(${(1 - t) * 12}px)`;
        }

        if (hintRef.current) {
          hintRef.current.style.opacity = hasStartedScrolling ? '0' : '1';
        }

        // Tagline & subline transitions: reveals near 80% to 100%
        if (taglineRef.current) {
          const t = clamp((currentProgress - 0.72) / 0.22, 0, 1);
          taglineRef.current.style.opacity = String(t);
          taglineRef.current.style.transform = `translateY(${(1 - t) * 22}px) scale(${0.96 + t * 0.04})`;
          taglineRef.current.style.filter = `blur(${(1 - t) * 8}px)`;
        }

        if (subtaglineRef.current) {
          const t = clamp((currentProgress - 0.82) / 0.18, 0, 1);
          subtaglineRef.current.style.opacity = String(t);
          subtaglineRef.current.style.transform = `translateY(${(1 - t) * 16}px)`;
        }

        if (progressBarRef.current) {
          progressBarRef.current.style.transform = `scaleX(${currentProgress})`;
        }
      }

      rafId = requestAnimationFrame(frame);
    }

    rafId = requestAnimationFrame(frame);

    return () => {
      window.removeEventListener('wheel', onWheel);
      window.removeEventListener('touchstart', onTouchStart);
      window.removeEventListener('touchmove', onTouchMove);
      window.removeEventListener('keydown', onKeyDown);
      document.removeEventListener('visibilitychange', onVisibilityChange);
      cancelAnimationFrame(rafId);
      releaseLock();
    };
  }, [scrubDistance, engageLock, releaseLock]);

  // Fallback SVG track animated parameters
  const trackOffset = (progressVal * 400) % 80;
  const signalGlowOpacity = 0.5 + Math.sin(progressVal * Math.PI) * 0.5;

  return (
    <div
      ref={containerRef}
      style={{
        width: '100%',
        minHeight: '100vh',
        background: '#060913',
        color: 'var(--text-main, #f8fafc)',
        position: 'relative',
        overflowX: 'hidden',
      }}
    >
      {/* Skip Intro Button Top-Right */}
      <div
        style={{
          position: 'fixed',
          top: '20px',
          right: '24px',
          zIndex: 100,
        }}
      >
        <button
          type="button"
          data-testid="skip-intro-btn"
          onClick={handleEnter}
          style={{
            background: 'rgba(15, 23, 42, 0.75)',
            backdropFilter: 'blur(12px)',
            border: '1px solid rgba(255, 255, 255, 0.2)',
            color: '#f8fafc',
            padding: '8px 16px',
            borderRadius: '9999px',
            fontSize: '0.82rem',
            fontWeight: 700,
            cursor: 'pointer',
            display: 'flex',
            alignItems: 'center',
            gap: '8px',
            boxShadow: '0 4px 15px rgba(0,0,0,0.4)',
            transition: 'all 0.2s ease',
          }}
          onMouseEnter={(e) => {
            e.currentTarget.style.borderColor = 'var(--color-primary, #22d3ee)';
            e.currentTarget.style.color = 'var(--color-primary, #22d3ee)';
          }}
          onMouseLeave={(e) => {
            e.currentTarget.style.borderColor = 'rgba(255, 255, 255, 0.2)';
            e.currentTarget.style.color = '#f8fafc';
          }}
        >
          <span>Skip Intro</span>
          <ArrowRight size={14} />
        </button>
      </div>

      {/* 100dvh Locked Scroll-Scrub Hero Section */}
      <div
        ref={heroSectionRef}
        data-testid="scroll-video-hero"
        style={{
          position: 'relative',
          height: '100dvh',
          width: '100%',
          overflow: 'hidden',
          background: '#060913',
          touchAction: 'none',
        }}
      >
        {/* Real Video Asset or Fallback SVG */}
        {!videoError ? (
          <video
            ref={videoRef}
            src={videoSrc}
            muted
            playsInline
            preload="auto"
            onError={() => setVideoError(true)}
            style={{
              position: 'absolute',
              inset: 0,
              width: '100%',
              height: '100%',
              objectFit: 'cover',
              opacity: videoLoaded ? 0.9 : 0,
              transformOrigin: 'center center',
              willChange: 'transform',
              transition: 'opacity 0.6s ease',
              touchAction: 'none',
              pointerEvents: 'none',
            }}
          />
        ) : null}

        {/* Fallback Animated SVG Railway Line Canvas */}
        {(videoError || !videoLoaded) && (
          <div
            data-testid="hero-svg-fallback"
            style={{
              position: 'absolute',
              inset: 0,
              width: '100%',
              height: '100%',
              background: 'radial-gradient(circle at 50% 40%, #0d1a33 0%, #060913 80%)',
              pointerEvents: 'none',
            }}
          >
            <svg
              width="100%"
              height="100%"
              preserveAspectRatio="none"
              style={{ position: 'absolute', inset: 0 }}
            >
              <defs>
                <linearGradient id="railGlow" x1="0" y1="0" x2="0" y2="1">
                  <stop offset="0%" stopColor="#22d3ee" stopOpacity="0.1" />
                  <stop offset="60%" stopColor="#22d3ee" stopOpacity="0.8" />
                  <stop offset="100%" stopColor="#38bdf8" stopOpacity="1" />
                </linearGradient>
                <linearGradient id="trackBed" x1="0" y1="0" x2="0" y2="1">
                  <stop offset="0%" stopColor="rgba(34,211,238,0.02)" />
                  <stop offset="100%" stopColor="rgba(34,211,238,0.12)" />
                </linearGradient>
              </defs>

              {/* Ballast / Trackbed Perspective Triangle */}
              <polygon points="46% 40%, 54% 40%, 85% 100%, 15% 100%" fill="url(#trackBed)" />

              {/* Converging Rails */}
              <line x1="48%" y1="40%" x2="25%" y2="100%" stroke="url(#railGlow)" strokeWidth="3" />
              <line x1="49.5%" y1="40%" x2="42%" y2="100%" stroke="#22d3ee" strokeWidth="2" strokeDasharray="16 12" strokeDashoffset={-trackOffset} />
              <line x1="50.5%" y1="40%" x2="58%" y2="100%" stroke="#22d3ee" strokeWidth="2" strokeDasharray="16 12" strokeDashoffset={-trackOffset} />
              <line x1="52%" y1="40%" x2="75%" y2="100%" stroke="url(#railGlow)" strokeWidth="3" />

              {/* Dynamic Perspective Sleepers / Ties */}
              {[...Array(14)].map((_, i) => {
                const ratio = Math.pow((i + 1) / 14, 2.2);
                const y = 40 + ratio * 60;
                const halfWidth = 4 + ratio * 32;
                return (
                  <line
                    key={i}
                    x1={`${50 - halfWidth}%`}
                    y1={`${y}%`}
                    x2={`${50 + halfWidth}%`}
                    y2={`${y}%`}
                    stroke="rgba(255, 255, 255, 0.15)"
                    strokeWidth={1 + ratio * 4}
                  />
                );
              })}

              {/* Overhead Signalling Light Gantry */}
              <circle cx="46%" cy="38%" r="4" fill="#10b981" filter="drop-shadow(0 0 8px #10b981)" opacity={signalGlowOpacity} />
              <circle cx="54%" cy="38%" r="4" fill="#22d3ee" filter="drop-shadow(0 0 8px #22d3ee)" opacity={signalGlowOpacity} />
            </svg>
          </div>
        )}

        {/* Ambient Dark Gradient Overlays */}
        <div
          style={{
            position: 'absolute',
            inset: 0,
            background:
              'linear-gradient(180deg, rgba(6,9,19,0.7) 0%, rgba(6,9,19,0.1) 40%, rgba(6,9,19,0.5) 80%, rgba(6,9,19,0.95) 100%)',
            pointerEvents: 'none',
          }}
        />

        {/* Hero Title */}
        <div
          ref={titleRef}
          style={{
            position: 'absolute',
            inset: 0,
            display: 'flex',
            flexDirection: 'column',
            alignItems: 'center',
            justifyContent: 'center',
            padding: '0 6%',
            textAlign: 'center',
            pointerEvents: 'none',
          }}
        >
          <div
            style={{
              display: 'inline-flex',
              alignItems: 'center',
              gap: '8px',
              padding: '6px 14px',
              borderRadius: '9999px',
              background: 'rgba(34, 211, 238, 0.12)',
              border: '1px solid rgba(34, 211, 238, 0.35)',
              color: 'var(--color-primary, #22d3ee)',
              fontSize: '0.8rem',
              fontWeight: 700,
              letterSpacing: '0.12em',
              textTransform: 'uppercase',
              marginBottom: '16px',
            }}
          >
            <TrainIcon size={14} />
            <span>Western Railway Corridor Dispatch</span>
          </div>

          <h1
            style={{
              fontFamily: 'var(--font-sans)',
              fontWeight: 800,
              fontSize: 'clamp(36px, 8vw, 108px)',
              lineHeight: 0.95,
              letterSpacing: '-0.03em',
              color: '#f8fafc',
              textShadow: '0 4px 40px rgba(0,0,0,0.8), 0 0 50px rgba(34,211,238,0.3)',
              margin: 0,
            }}
          >
            {title}
          </h1>

          <p
            style={{
              marginTop: '16px',
              color: '#94a3b8',
              fontFamily: 'var(--font-mono)',
              fontSize: 'clamp(12px, 1.6vw, 16px)',
              letterSpacing: '0.04em',
            }}
          >
            {subtagline}
          </p>
        </div>

        {/* Hero Tagline revealed as scrub finishes */}
        <div
          ref={taglineRef}
          style={{
            position: 'absolute',
            inset: 0,
            display: 'flex',
            flexDirection: 'column',
            alignItems: 'center',
            justifyContent: 'center',
            padding: '0 8%',
            textAlign: 'center',
            opacity: 0,
            pointerEvents: 'none',
          }}
        >
          <div
            style={{
              fontFamily: 'var(--font-sans)',
              fontWeight: 800,
              fontSize: 'clamp(24px, 4.4vw, 52px)',
              lineHeight: 1.15,
              letterSpacing: '-0.02em',
              color: '#ffffff',
              textShadow: '0 4px 30px rgba(0,0,0,0.7), 0 0 40px rgba(34,211,238,0.4)',
              maxWidth: '850px',
            }}
          >
            {tagline}
          </div>

          <div
            ref={subtaglineRef}
            style={{
              marginTop: '16px',
              color: 'var(--color-primary, #22d3ee)',
              fontSize: 'clamp(13px, 1.8vw, 18px)',
              fontWeight: 600,
              opacity: 0,
            }}
          >
            Scroll down or click below to enter the live operation control room
          </div>
        </div>

        {/* Scroll Hint */}
        <div
          ref={hintRef}
          style={{
            position: 'absolute',
            left: '50%',
            bottom: 'clamp(24px, 6vh, 48px)',
            transform: 'translateX(-50%)',
            display: 'flex',
            flexDirection: 'column',
            alignItems: 'center',
            gap: 6,
            color: 'rgba(240,244,248,0.75)',
            fontFamily: 'var(--font-mono)',
            fontSize: 'clamp(10px, 1.2vw, 12px)',
            fontWeight: 600,
            letterSpacing: '0.25em',
            transition: 'opacity 0.4s ease',
            pointerEvents: 'none',
          }}
        >
          <span>{scrollHint}</span>
          <ChevronDown
            size={18}
            style={{
              color: 'var(--color-primary, #22d3ee)',
              animation: 'metro-bounce 1.6s ease-in-out infinite',
            }}
          />
          <style>{`
            @keyframes metro-bounce {
              0%, 100% { transform: translateY(0); opacity: 0.5; }
              50% { transform: translateY(6px); opacity: 1; }
            }
          `}</style>
        </div>

        {/* Scrub Progress Bar */}
        <div
          style={{
            position: 'absolute',
            left: 0,
            right: 0,
            bottom: 0,
            height: 3,
            background: 'rgba(255,255,255,0.1)',
          }}
        >
          <div
            ref={progressBarRef}
            style={{
              height: '100%',
              width: '100%',
              background: 'linear-gradient(90deg, #22d3ee, #38bdf8)',
              transform: 'scaleX(0)',
              transformOrigin: 'left center',
              boxShadow: '0 0 10px rgba(34,211,238,0.8)',
            }}
          />
        </div>
      </div>

      {/* Post-Hero Feature Cards, Telemetry Stats & Control Room CTA */}
      <div
        ref={featuresSectionRef}
        data-testid="landing-features-section"
        style={{
          maxWidth: '1200px',
          margin: '0 auto',
          padding: '60px 24px 100px 24px',
          position: 'relative',
          zIndex: 10,
        }}
      >
        {/* Live Corridor Telemetry Banner */}
        <div
          style={{
            display: 'grid',
            gridTemplateColumns: 'repeat(auto-fit, minmax(240px, 1fr))',
            gap: '16px',
            marginBottom: '48px',
          }}
        >
          <div
            className="surface-card"
            style={{
              padding: '24px',
              textAlign: 'center',
              background: 'rgba(15, 23, 42, 0.65)',
              border: '1px solid rgba(255, 255, 255, 0.1)',
              borderRadius: 'var(--radius-md, 12px)',
            }}
          >
            <div style={{ fontSize: '0.75rem', fontWeight: 700, color: '#94a3b8', textTransform: 'uppercase', letterSpacing: '0.05em' }}>
              Corridor Stations
            </div>
            <div
              data-testid="landing-stat-stations"
              style={{
                fontSize: '2.4rem',
                fontWeight: 800,
                color: 'var(--color-primary, #22d3ee)',
                margin: '8px 0 4px 0',
                fontFamily: 'var(--font-mono)',
              }}
            >
              {displayStations !== undefined && displayStations > 0 ? displayStations : '—'}
            </div>
            <div style={{ fontSize: '0.78rem', color: '#64748b' }}>Virar to Mumbai Central</div>
          </div>

          <div
            className="surface-card"
            style={{
              padding: '24px',
              textAlign: 'center',
              background: 'rgba(15, 23, 42, 0.65)',
              border: '1px solid rgba(255, 255, 255, 0.1)',
              borderRadius: 'var(--radius-md, 12px)',
            }}
          >
            <div style={{ fontSize: '0.75rem', fontWeight: 700, color: '#94a3b8', textTransform: 'uppercase', letterSpacing: '0.05em' }}>
              Corridor Platforms
            </div>
            <div
              data-testid="landing-stat-platforms"
              style={{
                fontSize: '2.4rem',
                fontWeight: 800,
                color: '#38bdf8',
                margin: '8px 0 4px 0',
                fontFamily: 'var(--font-mono)',
              }}
            >
              {displayPlatforms !== undefined && displayPlatforms > 0 ? displayPlatforms : '—'}
            </div>
            <div style={{ fontSize: '0.78rem', color: '#64748b' }}>Simulated Platform Network</div>
          </div>

          <div
            className="surface-card"
            style={{
              padding: '24px',
              textAlign: 'center',
              background: 'rgba(15, 23, 42, 0.65)',
              border: '1px solid rgba(255, 255, 255, 0.1)',
              borderRadius: 'var(--radius-md, 12px)',
            }}
          >
            <div style={{ fontSize: '0.75rem', fontWeight: 700, color: '#94a3b8', textTransform: 'uppercase', letterSpacing: '0.05em' }}>
              Active Fleet Services
            </div>
            <div
              data-testid="landing-stat-trains"
              style={{
                fontSize: '2.4rem',
                fontWeight: 800,
                color: '#10b981',
                margin: '8px 0 4px 0',
                fontFamily: 'var(--font-mono)',
              }}
            >
              {displayTrains !== undefined && displayTrains > 0 ? displayTrains : '—'}
            </div>
            <div style={{ fontSize: '0.78rem', color: '#64748b' }}>Real-time Simulated Traffic</div>
          </div>
        </div>

        {/* Three Core Feature Cards */}
        <div
          style={{
            display: 'grid',
            gridTemplateColumns: 'repeat(auto-fit, minmax(320px, 1fr))',
            gap: '24px',
            marginBottom: '54px',
          }}
        >
          <div
            className="surface-card"
            style={{
              padding: '28px',
              background: 'rgba(15, 23, 42, 0.6)',
              border: '1px solid rgba(255, 255, 255, 0.12)',
              borderRadius: 'var(--radius-md, 12px)',
              display: 'flex',
              flexDirection: 'column',
              gap: '12px',
            }}
          >
            <div
              style={{
                width: '44px',
                height: '44px',
                borderRadius: '10px',
                background: 'rgba(34, 211, 238, 0.15)',
                color: 'var(--color-primary, #22d3ee)',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
              }}
            >
              <Layers size={22} />
            </div>
            <h3 style={{ fontSize: '1.2rem', fontWeight: 700, color: '#f8fafc', margin: 0 }}>
              Smart Platform Allocation
            </h3>
            <p style={{ fontSize: '0.85rem', color: '#94a3b8', lineHeight: 1.6, margin: 0 }}>
              Binary min-heap priority queue and multi-criteria scoring algorithm optimizing dwell times, platform lengths, and line compatibility without gridlock.
            </p>
          </div>

          <div
            className="surface-card"
            style={{
              padding: '28px',
              background: 'rgba(15, 23, 42, 0.6)',
              border: '1px solid rgba(255, 255, 255, 0.12)',
              borderRadius: 'var(--radius-md, 12px)',
              display: 'flex',
              flexDirection: 'column',
              gap: '12px',
            }}
          >
            <div
              style={{
                width: '44px',
                height: '44px',
                borderRadius: '10px',
                background: 'rgba(16, 185, 129, 0.15)',
                color: '#10b981',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
              }}
            >
              <Activity size={22} />
            </div>
            <h3 style={{ fontSize: '1.2rem', fontWeight: 700, color: '#f8fafc', margin: 0 }}>
              4-Aspect Automatic Signalling
            </h3>
            <p style={{ fontSize: '0.85rem', color: '#94a3b8', lineHeight: 1.6, margin: 0 }}>
              Physics-based deceleration curves, automated track block interlocking, and absolute zero-tolerance red signal stop compliance across 5x and 10x speeds.
            </p>
          </div>

          <div
            className="surface-card"
            style={{
              padding: '28px',
              background: 'rgba(15, 23, 42, 0.6)',
              border: '1px solid rgba(255, 255, 255, 0.12)',
              borderRadius: 'var(--radius-md, 12px)',
              display: 'flex',
              flexDirection: 'column',
              gap: '12px',
            }}
          >
            <div
              style={{
                width: '44px',
                height: '44px',
                borderRadius: '10px',
                background: 'rgba(168, 85, 247, 0.15)',
                color: '#a855f7',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
              }}
            >
              <Shuffle size={22} />
            </div>
            <h3 style={{ fontSize: '1.2rem', fontWeight: 700, color: '#f8fafc', margin: 0 }}>
              Dynamic Reallocation Cascade
            </h3>
            <p style={{ fontSize: '0.85rem', color: '#94a3b8', lineHeight: 1.6, margin: 0 }}>
              Instant ripple-effect rescheduling and automated conflict resolution whenever emergency track closures or unexpected delays occur.
            </p>
          </div>
        </div>

        {/* Primary Call to Action Button */}
        <div style={{ textAlign: 'center', marginTop: '30px' }}>
          <button
            type="button"
            data-testid="enter-control-room-btn"
            onClick={handleEnter}
            style={{
              background: 'linear-gradient(135deg, #22d3ee 0%, #0284c7 100%)',
              color: '#060913',
              border: 'none',
              padding: '16px 38px',
              borderRadius: '9999px',
              fontSize: '1.05rem',
              fontWeight: 800,
              cursor: 'pointer',
              display: 'inline-flex',
              alignItems: 'center',
              gap: '10px',
              boxShadow: '0 0 25px rgba(34, 211, 238, 0.4), 0 4px 15px rgba(0,0,0,0.3)',
              transition: 'all 0.2s ease',
            }}
            onMouseEnter={(e) => {
              e.currentTarget.style.transform = 'translateY(-2px)';
              e.currentTarget.style.boxShadow = '0 0 35px rgba(34, 211, 238, 0.6), 0 6px 20px rgba(0,0,0,0.4)';
            }}
            onMouseLeave={(e) => {
              e.currentTarget.style.transform = 'translateY(0)';
              e.currentTarget.style.boxShadow = '0 0 25px rgba(34, 211, 238, 0.4), 0 4px 15px rgba(0,0,0,0.3)';
            }}
          >
            <Zap size={20} fill="#060913" />
            <span>Enter Control Room</span>
            <ArrowRight size={20} />
          </button>
        </div>
      </div>
    </div>
  );
};

export default ScrollVideoHero;
