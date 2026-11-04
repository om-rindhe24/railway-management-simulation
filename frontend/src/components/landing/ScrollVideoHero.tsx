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
// 3. Visible, accessible "Skip intro" button top-right with high contrast.
// 4. prefers-reduced-motion immediately shows static scene at p=0.5 with no scrub trap.
// 5. Approaching locomotive (TrainFront) tied directly to scroll progress p in fallback mode.
// 6. Railway signal design palette (--rf-bg, --rf-amber, --rf-red, --rf-green, --rf-line, --rf-text).
// 7. Post-hero section featuring 3 core algorithm cards, live API telemetry, and Control Room CTA.
// 8. Tab visibility pausing and mobile-adapted scrub distances.
// ─────────────────────────────────────────────────────────────

import React, { useEffect, useRef, useState, useMemo, useCallback } from 'react';
import './landing.css';
import { TrainFront } from './TrainFront';
import { api } from '../../services/api';
import {
  ArrowRight,
  Zap,
  Layers,
  Activity,
  Shuffle,
  ChevronDown,
  Train as TrainIcon,
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

  // Train and track animation refs
  const trainContainerRef = useRef<HTMLDivElement>(null);
  const headlightWashRef = useRef<HTMLDivElement>(null);
  const headlightConeRef = useRef<SVGPolygonElement>(null);
  const sleepersGroupRef = useRef<SVGGElement>(null);
  const signalsGroupRef = useRef<SVGGElement>(null);

  const [videoLoaded, setVideoLoaded] = useState(false);
  const [videoError, setVideoError] = useState(false);
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

    let duration = 0;
    let rafId = 0;
    let targetProgress = 0;
    let currentProgress = 0;
    let hasStartedScrolling = false;
    let isSeeking = false;
    let pendingTime: number | null = null;
    let touchStartY = 0;
    let isTabVisible = true;

    // Static reduced motion setup
    if (reduceMotion) {
      targetProgress = 0.5;
      currentProgress = 0.5;
      releaseLock();
      if (titleRef.current) titleRef.current.style.opacity = '0';
      if (taglineRef.current) {
        taglineRef.current.style.opacity = '1';
        taglineRef.current.style.transform = 'translateY(0)';
      }
      if (headlightWashRef.current) headlightWashRef.current.style.opacity = '0';

      // Render static train at 40% into scene
      if (trainContainerRef.current) {
        const vw = (window.innerWidth || 1200) / 100;
        const H = heroSection.clientHeight || window.innerHeight || 800;
        const trainWidthPx = 6 * vw * Math.pow(22, 0.5);
        const trainHeightPx = trainWidthPx * 0.9;
        const pPos = 0.5 / 0.9;
        const baseY = H * (0.38 + 0.62 * Math.pow(pPos, 2.0));
        const trainTopPx = baseY - trainHeightPx;

        trainContainerRef.current.style.width = `${trainWidthPx}px`;
        trainContainerRef.current.style.height = `${trainHeightPx}px`;
        trainContainerRef.current.style.transform = `translate(-50%, ${trainTopPx}px)`;
      }
      return;
    }

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

      // Unlock at ~0.98 progress so user continues normally into features section
      if (targetProgress >= 0.98) {
        releaseLock();
      } else if (targetProgress < 0.95 && window.scrollY <= 10) {
        // Re-lock when scrolling back up into hero
        engageLock();
      }
      return true;
    }

    const onWheel = (e: WheelEvent) => {
      if (lockedRef.current) {
        addDelta(e.deltaY);
        e.preventDefault();
      } else if (window.scrollY <= 5 && e.deltaY < 0 && targetProgress >= 0.95) {
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

        if (duration > 0 && video) {
          seekTo(currentProgress * duration);
        }

        if (videoRef.current) {
          const scale = 1 + currentProgress * 0.08;
          videoRef.current.style.transform = `scale(${scale})`;
        }

        // 1. Title transitions: fades and blurs out during first 35% of scrub (p 0.00 - 0.35)
        if (titleRef.current) {
          const t = 1 - clamp(currentProgress / 0.35, 0, 1);
          titleRef.current.style.opacity = String(t);
          titleRef.current.style.transform = `translateY(${(1 - t) * -28}px) scale(${0.96 + t * 0.04})`;
          titleRef.current.style.filter = `blur(${(1 - t) * 12}px)`;
        }

        if (hintRef.current) {
          hintRef.current.style.opacity = hasStartedScrolling ? '0' : '1';
        }

        // 2. Tagline transitions: readable in upper third above train (p 0.60 - 0.88)
        if (taglineRef.current) {
          let tagOp = 0;
          if (currentProgress >= 0.60 && currentProgress <= 0.92) {
            if (currentProgress < 0.72) {
              tagOp = (currentProgress - 0.60) / 0.12;
            } else if (currentProgress <= 0.86) {
              tagOp = 1;
            } else {
              tagOp = 1 - (currentProgress - 0.86) / 0.06;
            }
          }
          taglineRef.current.style.opacity = String(clamp(tagOp, 0, 1));
          taglineRef.current.style.transform = `translate(-50%, ${(1 - clamp(tagOp, 0, 1)) * 18}px)`;
        }

        // 3. Headlight wash transition: fills screen at p 0.90..0.98, smoothly fades after unlock
        if (headlightWashRef.current) {
          let washOp = 0;
          if (currentProgress >= 0.90 && currentProgress < 0.98) {
            washOp = ((currentProgress - 0.90) / 0.08) * 0.9;
          } else if (currentProgress >= 0.98) {
            washOp = Math.max(0, 0.9 - ((currentProgress - 0.98) / 0.02) * 0.9);
          }
          headlightWashRef.current.style.opacity = String(clamp(washOp, 0, 0.9));
        }

        // 4. Scrub Progress Bar
        if (progressBarRef.current) {
          progressBarRef.current.style.transform = `scaleX(${currentProgress})`;
        }

        // 5. Approaching Train & Track Geometry (Fallback mode)
        const H = heroSectionRef.current?.clientHeight || window.innerHeight || 800;
        const W = heroSectionRef.current?.clientWidth || window.innerWidth || 1200;
        const vw = W / 100;

        // Exponential train width: 6vw * (22 ^ p)
        const trainWidthPx = 6 * vw * Math.pow(22, currentProgress);
        const trainHeightPx = trainWidthPx * 0.9; // 360/400 aspect ratio

        // Base Y follows perspective from horizon (38%) to bottom of screen at p ~ 0.9
        const pPos = clamp(currentProgress / 0.9, 0, 1);
        const baseY = H * (0.38 + 0.62 * Math.pow(pPos, 2.0));

        // Subtle vertical shake (1px) after p > 0.5 to suggest motion
        const shakeY = (!reduceMotion && currentProgress > 0.5)
          ? Math.sin(Date.now() * 0.04) * 1.5 * ((currentProgress - 0.5) / 0.5)
          : 0;

        const trainTopPx = baseY - trainHeightPx + shakeY;

        if (trainContainerRef.current) {
          trainContainerRef.current.style.width = `${trainWidthPx}px`;
          trainContainerRef.current.style.height = `${trainHeightPx}px`;
          trainContainerRef.current.style.transform = `translate(-50%, ${trainTopPx}px)`;

          // Headlight glow intensity: clamp(p / 0.6, 0, 1)
          const lightIntensity = clamp(currentProgress / 0.6, 0, 1);
          const glows = trainContainerRef.current.querySelectorAll('.train-headlight-glow');
          glows.forEach((g) => g.setAttribute('opacity', String(lightIntensity)));
        }

        // Soft amber headlight cone projected on track
        if (headlightConeRef.current) {
          const lightIntensity = clamp(currentProgress / 0.6, 0, 1);
          headlightConeRef.current.setAttribute('opacity', String(lightIntensity * 0.5));

          const halfWPercent = (trainWidthPx / (2 * W)) * 100 * 0.58;
          const xL = (50 - halfWPercent) * 10;
          const xR = (50 + halfWPercent) * 10;
          const yHead = Math.max(0, ((trainTopPx + trainHeightPx * 0.68) / H) * 100) * 10;

          headlightConeRef.current.setAttribute(
            'points',
            `${xL.toFixed(1)},${yHead.toFixed(1)} ${xR.toFixed(1)},${yHead.toFixed(1)} 940,1000 60,1000`
          );
        }

        // 14 Moving Sleepers
        if (sleepersGroupRef.current) {
          const lines = sleepersGroupRef.current.children;
          for (let i = 0; i < 14; i++) {
            const line = lines[i] as SVGLineElement;
            if (!line) continue;
            const t = (((i / 14 + currentProgress * 1.5) % 1) + 1) % 1;
            const y = (38 + (100 - 38) * Math.pow(t, 2.2)) * 10;
            const w = (2 + 66 * Math.pow(t, 2.2)) * 10;
            line.setAttribute('x1', String(500 - w / 2));
            line.setAttribute('x2', String(500 + w / 2));
            line.setAttribute('y1', String(y));
            line.setAttribute('y2', String(y));
            line.setAttribute('stroke-width', String(1 + t * 4.5));
            line.setAttribute('opacity', String(0.12 + t * 0.88));
          }
        }

        // Approaching Signal Posts (Left Red, Right Green)
        if (signalsGroupRef.current) {
          const tSig = clamp(0.18 + currentProgress * 1.15, 0, 1.4);
          if (tSig < 1.05) {
            const y = (38 + (100 - 38) * Math.pow(tSig, 2.2)) * 10;
            const spread = (2 + 38 * Math.pow(tSig, 2.0)) * 10;
            const scale = Math.pow(tSig, 1.8);
            const postHeight = (1.2 + 7.5 * scale) * 10;
            const lampR = 3.5 + 5.5 * scale;
            signalsGroupRef.current.style.opacity = String(clamp((1.05 - tSig) / 0.15, 0, 1));

            const leftPost = signalsGroupRef.current.querySelector('#signalLeft');
            if (leftPost) {
              leftPost.setAttribute('x1', String(500 - spread));
              leftPost.setAttribute('x2', String(500 - spread));
              leftPost.setAttribute('y1', String(y));
              leftPost.setAttribute('y2', String(y - postHeight));
              leftPost.setAttribute('stroke-width', String(1.5 + scale * 3));
            }
            const leftLamp = signalsGroupRef.current.querySelector('#signalLeftLamp');
            if (leftLamp) {
              leftLamp.setAttribute('cx', String(500 - spread));
              leftLamp.setAttribute('cy', String(y - postHeight));
              leftLamp.setAttribute('r', String(lampR));
            }

            const rightPost = signalsGroupRef.current.querySelector('#signalRight');
            if (rightPost) {
              rightPost.setAttribute('x1', String(500 + spread));
              rightPost.setAttribute('x2', String(500 + spread));
              rightPost.setAttribute('y1', String(y));
              rightPost.setAttribute('y2', String(y - postHeight));
              rightPost.setAttribute('stroke-width', String(1.5 + scale * 3));
            }
            const rightLamp = signalsGroupRef.current.querySelector('#signalRightLamp');
            if (rightLamp) {
              rightLamp.setAttribute('cx', String(500 + spread));
              rightLamp.setAttribute('cy', String(y - postHeight));
              rightLamp.setAttribute('r', String(lampR));
            }
          } else {
            signalsGroupRef.current.style.opacity = '0';
          }
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

  return (
    <div
      ref={containerRef}
      className="landing-root"
      style={{
        width: '100%',
        minHeight: '100vh',
        backgroundColor: 'var(--rf-bg, #0B0C10)',
        color: 'var(--rf-text, #F5F1E8)',
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
          className="landing-btn-secondary"
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
          maxWidth: '100vw',
          overflow: 'hidden',
          backgroundColor: 'var(--rf-bg, #0B0C10)',
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

        {/* Fallback Animated Railway Track & Approaching Locomotive Scene */}
        {(videoError || !videoLoaded) && (
          <div
            data-testid="hero-svg-fallback"
            style={{
              position: 'absolute',
              inset: 0,
              width: '100%',
              height: '100%',
              backgroundColor: 'var(--rf-bg, #0B0C10)',
              background: 'radial-gradient(circle at 50% 38%, #161922 0%, var(--rf-bg, #0B0C10) 80%)',
              pointerEvents: 'none',
              overflow: 'hidden',
            }}
          >
            {/* SVG Trackbed, Moving Sleepers, Converging Rails, and Signals */}
            <svg
              viewBox="0 0 1000 1000"
              preserveAspectRatio="none"
              style={{ position: 'absolute', inset: 0, width: '100%', height: '100%', zIndex: 10 }}
            >
              <defs>
                <linearGradient id="trackBedGrad" x1="0" y1="0" x2="0" y2="1">
                  <stop offset="0%" stopColor="var(--rf-line, #2A2D38)" stopOpacity="0.08" />
                  <stop offset="100%" stopColor="var(--rf-line, #2A2D38)" stopOpacity="0.35" />
                </linearGradient>

                <linearGradient id="headlightConeGrad" x1="0" y1="0" x2="0" y2="1">
                  <stop offset="0%" stopColor="var(--rf-amber, #FFB020)" stopOpacity="0.45" />
                  <stop offset="70%" stopColor="var(--rf-amber, #FFB020)" stopOpacity="0.12" />
                  <stop offset="100%" stopColor="var(--rf-amber, #FFB020)" stopOpacity="0" />
                </linearGradient>

                <linearGradient id="railHighlightGrad" x1="0" y1="0" x2="0" y2="1">
                  <stop offset="0%" stopColor="var(--rf-amber, #FFB020)" stopOpacity="0.2" />
                  <stop offset="50%" stopColor="var(--rf-amber, #FFB020)" stopOpacity="0.6" />
                  <stop offset="100%" stopColor="var(--rf-text, #F5F1E8)" stopOpacity="0.8" />
                </linearGradient>
              </defs>

              {/* Ballast Trackbed Perspective Triangle */}
              <polygon points="480,380 520,380 880,1000 120,1000" fill="url(#trackBedGrad)" />

              {/* Moving Headlight Cone on Track */}
              <polygon
                ref={headlightConeRef}
                points="490,380 510,380 940,1000 60,1000"
                fill="url(#headlightConeGrad)"
                opacity="0"
              />

              {/* 14 Moving Sleepers (Cross Ties) */}
              <g ref={sleepersGroupRef} stroke="var(--rf-line, #2A2D38)">
                {[...Array(14)].map((_, i) => (
                  <line key={i} x1="490" y1="380" x2="510" y2="380" strokeLinecap="round" />
                ))}
              </g>

              {/* Converging Rails */}
              {/* Outer Rail Lines */}
              <line x1="485" y1="380" x2="160" y2="1000" stroke="var(--rf-line, #2A2D38)" strokeWidth="4" />
              <line x1="515" y1="380" x2="840" y2="1000" stroke="var(--rf-line, #2A2D38)" strokeWidth="4" />
              {/* Inner Rail Amber Highlights */}
              <line x1="487" y1="380" x2="165" y2="1000" stroke="url(#railHighlightGrad)" strokeWidth="1.5" />
              <line x1="513" y1="380" x2="835" y2="1000" stroke="url(#railHighlightGrad)" strokeWidth="1.5" />

              {/* Perspective Approaching Signal Posts (Left Red, Right Green) */}
              <g ref={signalsGroupRef}>
                {/* Left Post & Red Lamp */}
                <line id="signalLeft" x1="460" y1="380" x2="460" y2="320" stroke="var(--rf-line, #2A2D38)" strokeWidth="2" strokeLinecap="round" />
                <circle id="signalLeftLamp" cx="460" cy="320" r="3.5" fill="var(--rf-red, #E5383B)" filter="drop-shadow(0 0 8px var(--rf-red, #E5383B))" />

                {/* Right Post & Green Lamp */}
                <line id="signalRight" x1="540" y1="380" x2="540" y2="320" stroke="var(--rf-line, #2A2D38)" strokeWidth="2" strokeLinecap="round" />
                <circle id="signalRightLamp" cx="540" cy="320" r="3.5" fill="var(--rf-green, #2BD576)" filter="drop-shadow(0 0 8px var(--rf-green, #2BD576))" />
              </g>
            </svg>

            {/* Approaching Mumbai EMU Locomotive (TrainFront) */}
            <div
              ref={trainContainerRef}
              data-testid="approaching-train"
              style={{
                position: 'absolute',
                left: '50%',
                top: '0',
                transform: 'translate(-50%, 0)',
                pointerEvents: 'none',
                zIndex: 15,
                willChange: 'transform, width, height',
              }}
            >
              <TrainFront />
            </div>
          </div>
        )}

        {/* Ambient Dark Gradient Overlays */}
        <div
          style={{
            position: 'absolute',
            inset: 0,
            background:
              'linear-gradient(180deg, rgba(11,12,16,0.7) 0%, rgba(11,12,16,0.1) 40%, rgba(11,12,16,0.5) 80%, rgba(11,12,16,0.95) 100%)',
            pointerEvents: 'none',
            zIndex: 18,
          }}
        />

        {/* Hero Title (p 0.00 – 0.35) */}
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
            zIndex: 20,
          }}
        >
          {/* Badge: Amber Outline & Text */}
          <div className="landing-badge">
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
              color: 'var(--rf-text, #F5F1E8)',
              textShadow: '0 4px 40px rgba(0,0,0,0.85), 0 0 45px var(--rf-amber-glow)',
              margin: 0,
            }}
          >
            {title}
          </h1>

          <p
            ref={subtaglineRef}
            style={{
              marginTop: '16px',
              color: 'var(--rf-muted, #9A9CA8)',
              fontFamily: 'var(--font-mono)',
              fontSize: 'clamp(12px, 1.6vw, 16px)',
              letterSpacing: '0.04em',
            }}
          >
            {subtagline}
          </p>
        </div>

        {/* Tagline revealed in upper third above train (p 0.60 – 0.88) */}
        <div
          ref={taglineRef}
          style={{
            position: 'absolute',
            top: '16%',
            left: '50%',
            transform: 'translateX(-50%)',
            width: '90%',
            maxWidth: '900px',
            textAlign: 'center',
            opacity: 0,
            pointerEvents: 'none',
            zIndex: 22,
          }}
        >
          <div
            style={{
              fontFamily: 'var(--font-sans)',
              fontWeight: 800,
              fontSize: 'clamp(24px, 4.4vw, 50px)',
              lineHeight: 1.15,
              letterSpacing: '-0.02em',
              color: 'var(--rf-text, #F5F1E8)',
              textShadow: '0 4px 30px rgba(0,0,0,0.9), 0 0 35px var(--rf-amber-glow)',
            }}
          >
            {tagline}
          </div>
          <div
            style={{
              marginTop: '12px',
              color: 'var(--rf-amber, #FFB020)',
              fontFamily: 'var(--font-mono)',
              fontSize: 'clamp(12px, 1.4vw, 15px)',
              fontWeight: 600,
              letterSpacing: '0.05em',
            }}
          >
            Scroll to enter or use Skip Intro
          </div>
        </div>

        {/* Fullscreen Amber-White Headlight Wash Overlay (p 0.90 – 0.98) */}
        <div
          ref={headlightWashRef}
          className="headlight-wash-overlay"
          style={{ opacity: 0 }}
        />

        {/* Scroll Hint (Bottom Center) */}
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
            color: 'var(--rf-muted, #9A9CA8)',
            fontFamily: 'var(--font-mono)',
            fontSize: 'clamp(10px, 1.2vw, 12px)',
            fontWeight: 600,
            letterSpacing: '0.25em',
            transition: 'opacity 0.4s ease',
            pointerEvents: 'none',
            zIndex: 21,
          }}
        >
          <span>{scrollHint}</span>
          <ChevronDown
            size={18}
            style={{
              color: 'var(--rf-amber, #FFB020)',
              animation: 'rf-metro-bounce 1.6s ease-in-out infinite',
            }}
          />
          <style>{`
            @keyframes rf-metro-bounce {
              0%, 100% { transform: translateY(0); opacity: 0.5; }
              50% { transform: translateY(6px); opacity: 1; }
            }
          `}</style>
        </div>

        {/* Amber Scrub Progress Bar */}
        <div
          style={{
            position: 'absolute',
            left: 0,
            right: 0,
            bottom: 0,
            height: 3,
            backgroundColor: 'var(--rf-line, #2A2D38)',
            zIndex: 26,
          }}
        >
          <div
            ref={progressBarRef}
            style={{
              height: '100%',
              width: '100%',
              background: 'linear-gradient(90deg, var(--rf-amber, #FFB020), #FFF5D6)',
              transform: 'scaleX(0)',
              transformOrigin: 'left center',
              boxShadow: '0 0 10px var(--rf-amber, #FFB020)',
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
          zIndex: 30,
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
          {/* Stat 1: Stations */}
          <div className="landing-card" style={{ padding: '24px', textAlign: 'center' }}>
            <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'center', gap: '8px', marginBottom: '4px' }}>
              <span className="signal-dot-amber" />
              <span style={{ fontSize: '0.75rem', fontWeight: 700, color: 'var(--rf-muted, #9A9CA8)', textTransform: 'uppercase', letterSpacing: '0.05em' }}>
                Corridor Stations
              </span>
            </div>
            <div
              data-testid="landing-stat-stations"
              style={{
                fontSize: '2.4rem',
                fontWeight: 800,
                color: 'var(--rf-text, #F5F1E8)',
                margin: '8px 0 4px 0',
                fontFamily: 'var(--font-mono)',
              }}
            >
              {displayStations !== undefined && displayStations > 0 ? displayStations : '—'}
            </div>
            <div style={{ fontSize: '0.78rem', color: 'var(--rf-muted, #9A9CA8)' }}>Virar to Mumbai Central</div>
          </div>

          {/* Stat 2: Platforms */}
          <div className="landing-card" style={{ padding: '24px', textAlign: 'center' }}>
            <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'center', gap: '8px', marginBottom: '4px' }}>
              <span className="signal-dot-green" />
              <span style={{ fontSize: '0.75rem', fontWeight: 700, color: 'var(--rf-muted, #9A9CA8)', textTransform: 'uppercase', letterSpacing: '0.05em' }}>
                Corridor Platforms
              </span>
            </div>
            <div
              data-testid="landing-stat-platforms"
              style={{
                fontSize: '2.4rem',
                fontWeight: 800,
                color: 'var(--rf-text, #F5F1E8)',
                margin: '8px 0 4px 0',
                fontFamily: 'var(--font-mono)',
              }}
            >
              {displayPlatforms !== undefined && displayPlatforms > 0 ? displayPlatforms : '—'}
            </div>
            <div style={{ fontSize: '0.78rem', color: 'var(--rf-muted, #9A9CA8)' }}>Simulated Platform Network</div>
          </div>

          {/* Stat 3: Active Fleet */}
          <div className="landing-card" style={{ padding: '24px', textAlign: 'center' }}>
            <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'center', gap: '8px', marginBottom: '4px' }}>
              <span className="signal-dot-green" />
              <span style={{ fontSize: '0.75rem', fontWeight: 700, color: 'var(--rf-muted, #9A9CA8)', textTransform: 'uppercase', letterSpacing: '0.05em' }}>
                Active Fleet Services
              </span>
            </div>
            <div
              data-testid="landing-stat-trains"
              style={{
                fontSize: '2.4rem',
                fontWeight: 800,
                color: 'var(--rf-amber, #FFB020)',
                margin: '8px 0 4px 0',
                fontFamily: 'var(--font-mono)',
              }}
            >
              {displayTrains !== undefined && displayTrains > 0 ? displayTrains : '—'}
            </div>
            <div style={{ fontSize: '0.78rem', color: 'var(--rf-muted, #9A9CA8)' }}>Real-time Simulated Traffic</div>
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
          {/* Feature 1: Allocation */}
          <div
            className="landing-card"
            style={{
              padding: '28px',
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
                background: 'var(--rf-amber-subtle)',
                color: 'var(--rf-amber, #FFB020)',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
              }}
            >
              <Layers size={22} />
            </div>
            <h3 style={{ fontSize: '1.2rem', fontWeight: 700, color: 'var(--rf-text, #F5F1E8)', margin: 0 }}>
              Smart Platform Allocation
            </h3>
            <p style={{ fontSize: '0.85rem', color: 'var(--rf-muted, #9A9CA8)', lineHeight: 1.6, margin: 0 }}>
              Binary min-heap priority queue and multi-criteria scoring algorithm optimizing dwell times, platform lengths, and line compatibility without gridlock.
            </p>
          </div>

          {/* Feature 2: Signalling */}
          <div
            className="landing-card"
            style={{
              padding: '28px',
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
                background: 'rgba(43, 213, 118, 0.12)',
                color: 'var(--rf-green, #2BD576)',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
              }}
            >
              <Activity size={22} />
            </div>
            <h3 style={{ fontSize: '1.2rem', fontWeight: 700, color: 'var(--rf-text, #F5F1E8)', margin: 0 }}>
              4-Aspect Automatic Signalling
            </h3>
            <p style={{ fontSize: '0.85rem', color: 'var(--rf-muted, #9A9CA8)', lineHeight: 1.6, margin: 0 }}>
              Physics-based deceleration curves, automated track block interlocking, and absolute zero-tolerance red signal stop compliance across 5x and 10x speeds.
            </p>
          </div>

          {/* Feature 3: Dynamic Reallocation */}
          <div
            className="landing-card"
            style={{
              padding: '28px',
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
                background: 'rgba(229, 56, 59, 0.12)',
                color: 'var(--rf-red, #E5383B)',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
              }}
            >
              <Shuffle size={22} />
            </div>
            <h3 style={{ fontSize: '1.2rem', fontWeight: 700, color: 'var(--rf-text, #F5F1E8)', margin: 0 }}>
              Dynamic Reallocation Cascade
            </h3>
            <p style={{ fontSize: '0.85rem', color: 'var(--rf-muted, #9A9CA8)', lineHeight: 1.6, margin: 0 }}>
              Instant ripple-effect rescheduling and automated conflict resolution whenever emergency track closures or unexpected delays occur.
            </p>
          </div>
        </div>

        {/* Primary Call to Action Button (Solid Amber with Dark Text) */}
        <div style={{ textAlign: 'center', marginTop: '30px' }}>
          <button
            type="button"
            data-testid="enter-control-room-btn"
            onClick={handleEnter}
            className="landing-btn-primary"
          >
            <Zap size={20} fill="var(--rf-bg, #0B0C10)" />
            <span>Enter Control Room</span>
            <ArrowRight size={20} />
          </button>
        </div>
      </div>
    </div>
  );
};

export default ScrollVideoHero;
