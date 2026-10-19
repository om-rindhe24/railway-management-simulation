import React, { useState, useEffect, useRef, useMemo, useCallback } from 'react';
import L from 'leaflet';
import 'leaflet/dist/leaflet.css';
import * as turf from '@turf/turf';
import westernLineGeoJson from '../data/westernLineTrack.json';
import { CorridorStateDto, TrainSnapshot, Signal, TrackBlock, Station } from '../types';
import { api } from '../services/api';
import { formatTrainLocation, formatNextStop } from '../utils/trainFormatters';
import './LiveTrainSpot.css';
import {
  Radar,
  Train as TrainIcon,
  Navigation,
  AlertTriangle,
  Clock,
  Play,
  Pause,
  Filter,
  Layers,
  Search,
  Maximize2,
  MapPin,
  Crosshair,
  Info,
  Radio,
  Eye,
  Zap,
  ChevronRight,
  ChevronLeft,
  ChevronUp,
  ChevronDown,
  X,
} from 'lucide-react';

export interface CorridorStationMeta {
  code: string;
  name: string;
  seq: number;
  lat: number;
  lng: number;
  platforms: number;
  isJunction: boolean;
  fastLineSupport: boolean;
  slowLineSupport: boolean;
  terminalFlag: boolean;
  locationKm: number;
}

// 1. SHARED GEOMETRY EXTRACTION FROM westernLineTrack.json
const baseLineFeature = westernLineGeoJson.features.find((f: any) => f.geometry.type === 'LineString');
const geoJsonStations = westernLineGeoJson.features.filter((f: any) => f.geometry.type === 'Point');

const snappedStationsMap: Record<string, { lat: number; lng: number; locationKm: number; name: string }> = {};
geoJsonStations.forEach((f: any) => {
  snappedStationsMap[f.properties.code] = {
    lat: f.geometry.coordinates[1],
    lng: f.geometry.coordinates[0],
    locationKm: f.properties.locationKm,
    name: f.properties.name,
  };
});

export const ORDERED_STATION_CODES = [
  'VR', 'NSP', 'BSR', 'NIG', 'BYR', 'MIRA', 'DIC', 'BVI',
  'KND', 'MLD', 'GMN', 'JOS', 'AND', 'VLP', 'STC', 'KHR',
  'BA', 'MM', 'MRU', 'DDR', 'PR', 'LP', 'MX', 'MMCT'
];

export const MAJOR_STATION_CODES = new Set(['VR', 'BSR', 'BVI', 'AND', 'BA', 'DDR', 'MMCT']);

export const CORRIDOR_GEOGRAPHY: Record<string, CorridorStationMeta> = {
  VR: { code: 'VR', name: 'Virar', seq: 1, lat: snappedStationsMap['VR']?.lat ?? 19.4538, lng: snappedStationsMap['VR']?.lng ?? 72.8119, locationKm: snappedStationsMap['VR']?.locationKm ?? 0.07, platforms: 6, isJunction: true, fastLineSupport: true, slowLineSupport: true, terminalFlag: true },
  NSP: { code: 'NSP', name: 'Nalasopara', seq: 2, lat: snappedStationsMap['NSP']?.lat ?? 19.4182, lng: snappedStationsMap['NSP']?.lng ?? 72.8189, locationKm: snappedStationsMap['NSP']?.locationKm ?? 4.18, platforms: 4, isJunction: false, fastLineSupport: true, slowLineSupport: true, terminalFlag: false },
  BSR: { code: 'BSR', name: 'Vasai Road', seq: 3, lat: snappedStationsMap['BSR']?.lat ?? 19.3803, lng: snappedStationsMap['BSR']?.lng ?? 72.8334, locationKm: snappedStationsMap['BSR']?.locationKm ?? 8.70, platforms: 5, isJunction: true, fastLineSupport: true, slowLineSupport: true, terminalFlag: false },
  NIG: { code: 'NIG', name: 'Naigaon', seq: 4, lat: snappedStationsMap['NIG']?.lat ?? 19.3505, lng: snappedStationsMap['NIG']?.lng ?? 72.8470, locationKm: snappedStationsMap['NIG']?.locationKm ?? 12.33, platforms: 2, isJunction: false, fastLineSupport: false, slowLineSupport: true, terminalFlag: false },
  BYR: { code: 'BYR', name: 'Bhayandar', seq: 5, lat: snappedStationsMap['BYR']?.lat ?? 19.3134, lng: snappedStationsMap['BYR']?.lng ?? 72.8528, locationKm: snappedStationsMap['BYR']?.locationKm ?? 16.55, platforms: 6, isJunction: false, fastLineSupport: true, slowLineSupport: true, terminalFlag: false },
  MIRA: { code: 'MIRA', name: 'Mira Road', seq: 6, lat: snappedStationsMap['MIRA']?.lat ?? 19.2809, lng: snappedStationsMap['MIRA']?.lng ?? 72.8559, locationKm: snappedStationsMap['MIRA']?.locationKm ?? 20.19, platforms: 4, isJunction: false, fastLineSupport: true, slowLineSupport: true, terminalFlag: false },
  DIC: { code: 'DIC', name: 'Dahisar', seq: 7, lat: snappedStationsMap['DIC']?.lat ?? 19.2561, lng: snappedStationsMap['DIC']?.lng ?? 72.8588, locationKm: snappedStationsMap['DIC']?.locationKm ?? 22.97, platforms: 4, isJunction: false, fastLineSupport: false, slowLineSupport: true, terminalFlag: false },
  BVI: { code: 'BVI', name: 'Borivali', seq: 8, lat: snappedStationsMap['BVI']?.lat ?? 19.2294, lng: snappedStationsMap['BVI']?.lng ?? 72.8573, locationKm: snappedStationsMap['BVI']?.locationKm ?? 25.99, platforms: 8, isJunction: true, fastLineSupport: true, slowLineSupport: true, terminalFlag: true },
  KND: { code: 'KND', name: 'Kandivali', seq: 9, lat: snappedStationsMap['KND']?.lat ?? 19.2044, lng: snappedStationsMap['KND']?.lng ?? 72.8525, locationKm: snappedStationsMap['KND']?.locationKm ?? 28.91, platforms: 4, isJunction: false, fastLineSupport: false, slowLineSupport: true, terminalFlag: false },
  MLD: { code: 'MLD', name: 'Malad', seq: 10, lat: snappedStationsMap['MLD']?.lat ?? 19.1868, lng: snappedStationsMap['MLD']?.lng ?? 72.8490, locationKm: snappedStationsMap['MLD']?.locationKm ?? 30.93, platforms: 4, isJunction: false, fastLineSupport: true, slowLineSupport: true, terminalFlag: false },
  GMN: { code: 'GMN', name: 'Goregaon', seq: 11, lat: snappedStationsMap['GMN']?.lat ?? 19.1645, lng: snappedStationsMap['GMN']?.lng ?? 72.8495, locationKm: snappedStationsMap['GMN']?.locationKm ?? 33.42, platforms: 4, isJunction: false, fastLineSupport: true, slowLineSupport: true, terminalFlag: false },
  JOS: { code: 'JOS', name: 'Jogeshwari', seq: 12, lat: snappedStationsMap['JOS']?.lat ?? 19.1352, lng: snappedStationsMap['JOS']?.lng ?? 72.8490, locationKm: snappedStationsMap['JOS']?.locationKm ?? 36.74, platforms: 2, isJunction: false, fastLineSupport: false, slowLineSupport: true, terminalFlag: false },
  AND: { code: 'AND', name: 'Andheri', seq: 13, lat: snappedStationsMap['AND']?.lat ?? 19.1197, lng: snappedStationsMap['AND']?.lng ?? 72.8467, locationKm: snappedStationsMap['AND']?.locationKm ?? 38.51, platforms: 8, isJunction: true, fastLineSupport: true, slowLineSupport: true, terminalFlag: true },
  VLP: { code: 'VLP', name: 'Vile Parle', seq: 14, lat: snappedStationsMap['VLP']?.lat ?? 19.0998, lng: snappedStationsMap['VLP']?.lng ?? 72.8442, locationKm: snappedStationsMap['VLP']?.locationKm ?? 40.78, platforms: 2, isJunction: false, fastLineSupport: false, slowLineSupport: true, terminalFlag: false },
  STC: { code: 'STC', name: 'Santacruz', seq: 15, lat: snappedStationsMap['STC']?.lat ?? 19.0827, lng: snappedStationsMap['STC']?.lng ?? 72.8416, locationKm: snappedStationsMap['STC']?.locationKm ?? 42.70, platforms: 2, isJunction: false, fastLineSupport: false, slowLineSupport: true, terminalFlag: false },
  KHR: { code: 'KHR', name: 'Khar Road', seq: 16, lat: snappedStationsMap['KHR']?.lat ?? 19.0699, lng: snappedStationsMap['KHR']?.lng ?? 72.8400, locationKm: snappedStationsMap['KHR']?.locationKm ?? 44.19, platforms: 2, isJunction: false, fastLineSupport: false, slowLineSupport: true, terminalFlag: false },
  BA: { code: 'BA', name: 'Bandra', seq: 17, lat: snappedStationsMap['BA']?.lat ?? 19.0553, lng: snappedStationsMap['BA']?.lng ?? 72.8407, locationKm: snappedStationsMap['BA']?.locationKm ?? 45.86, platforms: 6, isJunction: true, fastLineSupport: true, slowLineSupport: true, terminalFlag: true },
  MM: { code: 'MM', name: 'Mahim', seq: 18, lat: snappedStationsMap['MM']?.lat ?? 19.0408, lng: snappedStationsMap['MM']?.lng ?? 72.8415, locationKm: snappedStationsMap['MM']?.locationKm ?? 47.48, platforms: 2, isJunction: false, fastLineSupport: false, slowLineSupport: true, terminalFlag: false },
  MRU: { code: 'MRU', name: 'Matunga Road', seq: 19, lat: snappedStationsMap['MRU']?.lat ?? 19.0275, lng: snappedStationsMap['MRU']?.lng ?? 72.8414, locationKm: snappedStationsMap['MRU']?.locationKm ?? 48.98, platforms: 2, isJunction: false, fastLineSupport: false, slowLineSupport: true, terminalFlag: false },
  DDR: { code: 'DDR', name: 'Dadar', seq: 20, lat: snappedStationsMap['DDR']?.lat ?? 19.0185, lng: snappedStationsMap['DDR']?.lng ?? 72.8426, locationKm: snappedStationsMap['DDR']?.locationKm ?? 49.99, platforms: 7, isJunction: true, fastLineSupport: true, slowLineSupport: true, terminalFlag: true },
  PR: { code: 'PR', name: 'Prabhadevi', seq: 21, lat: snappedStationsMap['PR']?.lat ?? 19.0108, lng: snappedStationsMap['PR']?.lng ?? 72.8377, locationKm: snappedStationsMap['PR']?.locationKm ?? 51.01, platforms: 2, isJunction: false, fastLineSupport: false, slowLineSupport: true, terminalFlag: false },
  LP: { code: 'LP', name: 'Lower Parel', seq: 22, lat: snappedStationsMap['LP']?.lat ?? 18.9959, lng: snappedStationsMap['LP']?.lng ?? 72.8305, locationKm: snappedStationsMap['LP']?.locationKm ?? 52.87, platforms: 2, isJunction: false, fastLineSupport: false, slowLineSupport: true, terminalFlag: false },
  MX: { code: 'MX', name: 'Mahalaxmi', seq: 23, lat: snappedStationsMap['MX']?.lat ?? 18.9828, lng: snappedStationsMap['MX']?.lng ?? 72.8243, locationKm: snappedStationsMap['MX']?.locationKm ?? 54.49, platforms: 2, isJunction: false, fastLineSupport: false, slowLineSupport: true, terminalFlag: false },
  MMCT: { code: 'MMCT', name: 'Mumbai Central', seq: 24, lat: snappedStationsMap['MMCT']?.lat ?? 18.9699, lng: snappedStationsMap['MMCT']?.lng ?? 72.8195, locationKm: snappedStationsMap['MMCT']?.locationKm ?? 56.10, platforms: 5, isJunction: true, fastLineSupport: true, slowLineSupport: true, terminalFlag: true },
};

// 2. ONE SHARED GEOMETRY: 4 PARALLEL OFFSET LINES
const lineCoords = (baseLineFeature?.geometry?.coordinates ?? []) as [number, number][];
const baseTrackLine = turf.lineString(lineCoords);
const totalCorridorKm = turf.length(baseTrackLine, { units: 'kilometers' });

// 4 Distinct Line Polylines (Negative = West/UP, Positive = East/DN)
// Offsets tuned so all 4 lines remain visibly separate parallel lines from zoom 14 upward
const fastUpLine: any = turf.lineOffset(baseTrackLine, -0.016, { units: 'kilometers' });
const slowUpLine: any = turf.lineOffset(baseTrackLine, -0.0055, { units: 'kilometers' });
const slowDnLine: any = turf.lineOffset(baseTrackLine, 0.0055, { units: 'kilometers' });
const fastDnLine: any = turf.lineOffset(baseTrackLine, 0.016, { units: 'kilometers' });

function geoJsonToLeafletCoords(line: any): [number, number][] {
  const coords = line.geometry?.coordinates || [];
  return coords.map((c: number[]) => [c[1], c[0]]);
}

const fastUpCoords = geoJsonToLeafletCoords(fastUpLine);
const slowUpCoords = geoJsonToLeafletCoords(slowUpLine);
const slowDnCoords = geoJsonToLeafletCoords(slowDnLine);
const fastDnCoords = geoJsonToLeafletCoords(fastDnLine);

const allStationCoords: [number, number][] = ORDERED_STATION_CODES.map((code) => {
  const geo = CORRIDOR_GEOGRAPHY[code];
  return [geo.lat, geo.lng];
});

interface TrainAnimState {
  currentLat: number;
  currentLng: number;
  currentBearing: number;
  startLat: number;
  startLng: number;
  startBearing: number;
  targetLat: number;
  targetLng: number;
  targetBearing: number;
  startTime: number;
  duration: number;
}

interface LiveTrainSpotProps {
  corridorState: CorridorStateDto | null;
  stations?: Station[];
  onSelectTrain: (train: TrainSnapshot) => void;
  onSelectStation: (code: string) => void;
  selectedStation: string;
  inspectingTrain?: TrainSnapshot | null;
}

export const LiveTrainSpot: React.FC<LiveTrainSpotProps> = ({
  corridorState,
  stations,
  onSelectTrain,
  onSelectStation,
  selectedStation,
  inspectingTrain,
}) => {
  // Layer & Filter Controls
  const [directionFilter, setDirectionFilter] = useState<'ALL' | 'UP' | 'DN'>('ALL');
  const [speedFilter, setSpeedFilter] = useState<'ALL' | 'FAST' | 'SLOW'>('ALL');
  const [selectedSector, setSelectedSector] = useState<'ALL' | 'NORTH' | 'CENTRAL' | 'SOUTH'>('ALL');
  const [layerVisibility, setLayerVisibility] = useState({
    stations: true,
    trains: true,
    signals: true,
    blocks: true,
  });

  const [trainSearchQuery, setTrainSearchQuery] = useState('');
  const [isTrainsPanelCollapsed, setIsTrainsPanelCollapsed] = useState(false);
  const [isLegendExpanded, setIsLegendExpanded] = useState(false);
  const [currentZoom, setCurrentZoom] = useState(11);
  const [hoveredTrain, setHoveredTrain] = useState<TrainSnapshot | null>(null);
  const [localSelectedTrainId, setLocalSelectedTrainId] = useState<number | null>(null);
  const [isFollowSelected, setIsFollowSelected] = useState(false);
  const [isMapReady, setIsMapReady] = useState(false);

  // Auto-collapse trains list if full external inspector drawer is open
  useEffect(() => {
    if (inspectingTrain) {
      setIsTrainsPanelCollapsed(true);
    }
  }, [inspectingTrain]);

  const activeSelectedTrainId = inspectingTrain ? inspectingTrain.trainId : localSelectedTrainId;

  // Leaflet references
  const mapContainerRef = useRef<HTMLDivElement>(null);
  const mapInstanceRef = useRef<L.Map | null>(null);
  const trainMarkersRef = useRef<Map<number, L.Marker>>(new Map());
  const trainAnimRef = useRef<Map<number, TrainAnimState>>(new Map());

  const layersRef = useRef<{
    blocks: L.LayerGroup | null;
    tracks: L.LayerGroup | null;
    stations: L.LayerGroup | null;
    signals: L.LayerGroup | null;
    trains: L.LayerGroup | null;
    aheadHighlight: L.LayerGroup | null;
  }>({
    blocks: null,
    tracks: null,
    stations: null,
    signals: null,
    trains: null,
    aheadHighlight: null,
  });

  // Station metadata lookup
  const getStationMeta = useCallback(
    (code?: string): CorridorStationMeta => {
      if (!code) return CORRIDOR_GEOGRAPHY['VR'];
      return CORRIDOR_GEOGRAPHY[code] || CORRIDOR_GEOGRAPHY['VR'];
    },
    []
  );

  const corridorStations = useMemo(() => {
    return ORDERED_STATION_CODES.map((code) => {
      const base = CORRIDOR_GEOGRAPHY[code];
      const fromBackend = stations?.find((s) => s.stationCode === code);
      return {
        code,
        name: fromBackend?.stationName || base.name,
        seq: fromBackend?.sequenceOrder || base.seq,
        lat: base.lat,
        lng: base.lng,
        locationKm: base.locationKm,
        platforms: fromBackend?.numberOfPlatforms || base.platforms,
        isJunction: base.isJunction,
        fastLineSupport: fromBackend?.fastLineSupport ?? base.fastLineSupport,
        slowLineSupport: fromBackend?.slowLineSupport ?? base.slowLineSupport,
        terminalFlag: fromBackend?.terminalFlag ?? base.terminalFlag,
      };
    });
  }, [stations]);

  // Operational Datasets
  const activeTrains = corridorState?.trains || [];
  const trackBlocks = corridorState?.trackBlocks || [];
  const signals = corridorState?.signals || [];

  // Telemetry KPIs
  const movingTrains = activeTrains.filter((t) => (t.speedKmH ?? 0) > 0);
  const waitingTrains = activeTrains.filter(
    (t) => t.status === 'WAITING' || (t.status as any) === 'WAITING AT STATION' || (t.waitingReason && t.waitingReason.length > 0) || t.signalAspect === 'RED'
  );
  const delayedTrains = activeTrains.filter((t) => (t.delayMinutes ?? 0) > 0);
  const occupiedBlocks = trackBlocks.filter((b) => b.occupied && b.blockType !== 'PLATFORM');

  // Aspect colors & speed limits
  const getAspectColor = (aspect?: string): string => {
    switch (aspect) {
      case 'RED': return '#ef4444';
      case 'YELLOW': return '#f59e0b';
      case 'DOUBLE_YELLOW': return '#eab308';
      case 'GREEN': return '#10b981';
      default: return '#94a3b8';
    }
  };

  const getAspectLimit = (aspect?: string): string => {
    switch (aspect) {
      case 'RED': return '0 km/h';
      case 'YELLOW': return '15 km/h';
      case 'DOUBLE_YELLOW': return '50 km/h';
      case 'GREEN': return '110 km/h';
      default: return '110 km/h';
    }
  };

  const getTrainCatColor = (type?: string): string => {
    switch (type) {
      case 'SPECIAL': return '#ec4899';
      case 'PREMIUM': return '#f59e0b';
      case 'SUPERFAST': return '#a855f7';
      case 'EXPRESS_MAIL': return '#0284c7';
      case 'FREIGHT': return '#f97316';
      default: return '#06b6d4'; // LOCAL
    }
  };

  // Helper to resolve the exact Turf offset line for any train or signal
  const resolveTrackPolyline = useCallback((isFast: boolean, isUp: boolean): any => {
    return isFast ? (isUp ? fastUpLine : fastDnLine) : (isUp ? slowUpLine : slowDnLine);
  }, []);

  // Compute live position, line, and bearing for every train using the shared geometry
  const trainCalculations = useMemo(() => {
    const calcMap = new Map<number, { lat: number; lng: number; bearing: number; isFast: boolean; isUp: boolean; refLine: any }>();

    activeTrains.forEach((train) => {
      const currentBlock = trackBlocks.find((b) => b.blockId === train.currentBlockId);
      const fromMeta = getStationMeta(currentBlock ? currentBlock.fromStationCode : train.currentStationCode);
      const toMeta = getStationMeta(currentBlock ? currentBlock.toStationCode : train.targetStationCode);

      // Line type and direction come strictly from current TrackBlock or train properties
      const isUp = currentBlock
        ? Boolean(currentBlock.direction === 'UP' || currentBlock.lineType?.includes('UP'))
        : (train.direction === 'UP');

      const isFast = currentBlock
        ? Boolean(currentBlock.lineType?.includes('FAST') || currentBlock.blockId?.endsWith('-FU') || currentBlock.blockId?.endsWith('-FD'))
        : Boolean(train.trainCategory === 'FAST_LOCAL' || train.trainType === 'SUPERFAST' || train.trainType === 'PREMIUM');

      const activeLine = resolveTrackPolyline(isFast, isUp);

      let trainKm = fromMeta.locationKm;
      if (train.status !== 'AT_PLATFORM' && !train.currentBlockId?.startsWith('PLAT_') && !train.currentBlockId?.includes('-P')) {
        const progress = Math.min(1.0, Math.max(0.0, (train.progressPercent ?? 0) / 100));
        trainKm = fromMeta.locationKm + (toMeta.locationKm - fromMeta.locationKm) * progress;
      }
      trainKm = Math.min(totalCorridorKm - 0.02, Math.max(0.02, trainKm));

      // Train position along the offset line
      const ptOnLine = turf.along(activeLine, trainKm, { units: 'kilometers' });
      
      // Tangent bearing calculation along track curve
      const stepKm = 0.04;
      const pA = turf.along(activeLine, Math.max(0.01, trainKm - stepKm / 2), { units: 'kilometers' });
      const pB = turf.along(activeLine, Math.min(totalCorridorKm, trainKm + stepKm / 2), { units: 'kilometers' });
      const trackBearing = turf.bearing(pA, pB); // Base Southbound line (~180°)

      // UP = southbound along the line (~180°)
      // DN = northbound (reversed by 180° so arrow points northward in travel direction)
      let bearing = isUp ? trackBearing : (trackBearing + 180) % 360;
      if (bearing < 0) bearing += 360;

      calcMap.set(train.trainId, {
        lat: ptOnLine.geometry.coordinates[1],
        lng: ptOnLine.geometry.coordinates[0],
        bearing,
        isFast,
        isUp,
        refLine: activeLine,
      });
    });

    return calcMap;
  }, [activeTrains, trackBlocks, getStationMeta, resolveTrackPolyline]);

  // Fit corridor helper with padding so middle remains visible
  const handleFitCorridor = useCallback(() => {
    const map = mapInstanceRef.current;
    if (!map) return;
    map.fitBounds(L.latLngBounds(allStationCoords), {
      paddingTopLeft: [70, 310],
      paddingBottomRight: [40, 40],
    });
  }, []);

  // 1. Initialize Map with CARTO dark basemap without labels + CARTO labels overlay (maxZoom 20)
  useEffect(() => {
    if (!mapContainerRef.current || mapInstanceRef.current) return;

    const map = L.map(mapContainerRef.current, {
      center: [19.21, 72.84],
      zoom: 11,
      minZoom: 8,
      maxZoom: 20,
      zoomControl: false,
    });

    L.control.zoom({ position: 'bottomright' }).addTo(map);

    map.on('zoomend', () => {
      setCurrentZoom(map.getZoom());
    });

    // OpenStreetMap standard tiles (Zero API keys, zero watermarks, maxNativeZoom 19, maxZoom 20)
    // Rendered dark, quiet, and low-contrast via CSS filter on .leaflet-tile-pane
    const osmTileLayer = L.tileLayer('https://tile.openstreetmap.org/{z}/{x}/{y}.png', {
      maxZoom: 20,
      maxNativeZoom: 19,
      attribution: '&copy; <a href="https://www.openstreetmap.org/copyright" target="_blank">OpenStreetMap</a> contributors',
    });
    osmTileLayer.addTo(map);

    // Operational Layer Groups
    const blocksGroup = L.layerGroup().addTo(map);
    const tracksGroup = L.layerGroup().addTo(map);
    const aheadHighlightGroup = L.layerGroup().addTo(map);
    const stationsGroup = L.layerGroup().addTo(map);
    const signalsGroup = L.layerGroup().addTo(map);
    const trainsGroup = L.layerGroup().addTo(map);

    layersRef.current = {
      blocks: blocksGroup,
      tracks: tracksGroup,
      aheadHighlight: aheadHighlightGroup,
      stations: stationsGroup,
      signals: signalsGroup,
      trains: trainsGroup,
    };

    mapInstanceRef.current = map;
    (window as any).__leafletMap = map;
    (window as any).__layers = layersRef.current;
    (window as any).__trainMarkers = trainMarkersRef.current;
    setIsMapReady(true);

    // Initial fit
    map.fitBounds(L.latLngBounds(allStationCoords), {
      paddingTopLeft: [70, 310],
      paddingBottomRight: [40, 40],
    });

    setTimeout(() => {
      map.invalidateSize();
    }, 250);

    return () => {
      setIsMapReady(false);
      trainMarkersRef.current.clear();
      trainAnimRef.current.clear();
      map.remove();
      mapInstanceRef.current = null;
    };
  }, []);

  // 2. Render Parallel Tracks
  // Thin lines (~2px). Fast lines and Slow lines in two clearly different colours.
  // FAST lines: Cyan #38bdf8 (UP) and Violet #818cf8 (DN)
  // SLOW lines: Rose #fb7185 (UP) and Orange #fb923c (DN)
  // (Neither shares colors with green/yellow signals)
  useEffect(() => {
    const { tracks } = layersRef.current;
    if (!tracks) return;
    tracks.clearLayers();

    const isFastAllowed = speedFilter !== 'SLOW';
    const isSlowAllowed = speedFilter !== 'FAST';
    const isUpAllowed = directionFilter !== 'DN';
    const isDnAllowed = directionFilter !== 'UP';

    const coreWeight = 2.0;
    const casingWeight = 3.5;

    const renderLineWithCasing = (coords: [number, number][], coreColor: string, tooltipText: string) => {
      // 1px dark casing underneath
      L.polyline(coords, {
        color: '#030712',
        weight: casingWeight,
        opacity: 0.9,
        lineCap: 'round',
        lineJoin: 'round',
      }).addTo(tracks);

      // Core crisp line
      L.polyline(coords, {
        color: coreColor,
        weight: coreWeight,
        opacity: 0.95,
        lineCap: 'round',
        lineJoin: 'round',
      })
        .bindTooltip(tooltipText, { direction: 'top', sticky: true })
        .addTo(tracks);
    };

    if (isFastAllowed) {
      if (isUpAllowed) {
        renderLineWithCasing(fastUpCoords, '#38bdf8', 'FAST UP Line ↓ (Virar ➔ Mumbai Central)');
      }
      if (isDnAllowed) {
        renderLineWithCasing(fastDnCoords, '#818cf8', 'FAST DN Line ↑ (Mumbai Central ➔ Virar)');
      }
    }

    if (isSlowAllowed) {
      if (isUpAllowed) {
        renderLineWithCasing(slowUpCoords, '#fb7185', 'SLOW UP Line ↓ (Virar ➔ Mumbai Central)');
      }
      if (isDnAllowed) {
        renderLineWithCasing(slowDnCoords, '#fb923c', 'SLOW DN Line ↑ (Mumbai Central ➔ Virar)');
      }
    }
  }, [speedFilter, directionFilter, isMapReady]);

  // 3. Highlight Protected Block for Selected Train (Requirement 8)
  useEffect(() => {
    const { aheadHighlight } = layersRef.current;
    if (!aheadHighlight) return;
    aheadHighlight.clearLayers();

    if (!activeSelectedTrainId) return;
    const selected = activeTrains.find((t) => t.trainId === activeSelectedTrainId);
    if (!selected || !selected.currentBlockId) return;

    const currBlock = trackBlocks.find((b) => b.blockId === selected.currentBlockId);
    if (!currBlock) return;

    const fromMeta = getStationMeta(currBlock.fromStationCode);
    const toMeta = getStationMeta(currBlock.toStationCode);
    const isFast = Boolean(currBlock.lineType?.includes('FAST') || currBlock.blockId?.endsWith('-FU') || currBlock.blockId?.endsWith('-FD'));
    const isUp = Boolean(currBlock.direction === 'UP' || currBlock.lineType?.includes('UP'));
    const targetLine = resolveTrackPolyline(isFast, isUp);

    const isPlatform = currBlock.blockType === 'PLATFORM';
    const km1 = isPlatform
      ? Math.max(0.01, fromMeta.locationKm - 0.12)
      : Math.min(fromMeta.locationKm, toMeta.locationKm);
    const km2 = isPlatform
      ? Math.min(totalCorridorKm, fromMeta.locationKm + 0.12)
      : Math.max(fromMeta.locationKm, toMeta.locationKm);

    if (km2 > km1 + 0.04) {
      try {
        const sliced = turf.lineSliceAlong(targetLine, km1, km2, { units: 'kilometers' });
        const blockCoords = geoJsonToLeafletCoords(sliced);

        L.polyline(blockCoords, {
          color: '#38bdf8',
          weight: 4.5,
          opacity: 0.9,
          lineCap: 'round',
          lineJoin: 'round',
        })
          .bindTooltip(`Protected Block: ${currBlock.blockId}`, { direction: 'top', sticky: true })
          .addTo(aheadHighlight);
      } catch (e) {
        // Turf edge slice error handler
      }
    }
  }, [activeSelectedTrainId, activeTrains, trackBlocks, getStationMeta, resolveTrackPolyline, isMapReady]);

  // 4. Render Station Markers: Small Dots, Name Labels ONLY at Zoom 12 and above (Requirement 2)
  useEffect(() => {
    const { stations: stationsGroup } = layersRef.current;
    if (!stationsGroup) return;
    stationsGroup.clearLayers();

    if (!layerVisibility.stations) return;

    corridorStations.forEach((station) => {
      const isMajor = MAJOR_STATION_CODES.has(station.code);
      const isSelected = selectedStation === station.code;

      // Small dots: 5px normal, 7px major, 9px selected
      const dotDiameter = isSelected ? 9 : isMajor ? 7 : 5;
      
      // Requirement 3: Names only at zoom >= 11, all stations at zoom >= 13
      const showStationName = currentZoom >= 13 ? true : (currentZoom >= 11 ? (isMajor || isSelected) : false);

      // Hide station label if a train marker overlaps this station
      const isTrainOverlapping = activeTrains.some((t) => {
        if (t.status === 'AT_PLATFORM' && (t.currentStationCode === station.code || t.currentBlockId?.includes(`-${station.code}-`))) {
          return true;
        }
        const calc = trainCalculations.get(t.trainId);
        if (!calc) return false;
        return Math.abs(calc.lat - station.lat) < 0.0015 && Math.abs(calc.lng - station.lng) < 0.0015;
      });
      const shouldRenderLabel = showStationName && (!isTrainOverlapping || isSelected);

      const stationIcon = L.divIcon({
        className: 'station-dot-marker',
        html: `
          <div style="position: relative; width: ${dotDiameter}px; height: ${dotDiameter}px; cursor: pointer;">
            <div style="
              width: ${dotDiameter}px;
              height: ${dotDiameter}px;
              border-radius: 50%;
              background: ${isSelected ? '#38bdf8' : '#ffffff'};
              border: 1px solid ${isSelected ? '#38bdf8' : '#040711'};
              box-shadow: ${isSelected ? '0 0 8px #38bdf8' : '0 1px 3px rgba(0,0,0,0.8)'};
            "></div>

            ${
              shouldRenderLabel
                ? `<div style="
                    position: absolute;
                    bottom: 100%;
                    left: 50%;
                    transform: translateX(-50%) translateY(-5px);
                    background: rgba(10, 15, 29, 0.88);
                    backdrop-filter: blur(8px);
                    border: 1px solid ${isSelected ? '#38bdf8' : 'rgba(255, 255, 255, 0.15)'};
                    border-radius: 4px;
                    padding: 1px 5px;
                    font-family: var(--font-sans);
                    font-size: ${isSelected ? '10.5px' : '9.5px'};
                    font-weight: 700;
                    color: ${isSelected ? '#38bdf8' : isMajor ? '#ffffff' : '#94a3b8'};
                    white-space: nowrap;
                    line-height: 1.2;
                    pointer-events: none;
                    box-shadow: 0 2px 6px rgba(0, 0, 0, 0.6);
                  ">${station.name}</div>`
                : ''
            }
          </div>
        `,
        iconSize: [dotDiameter, dotDiameter],
        iconAnchor: [dotDiameter / 2, dotDiameter / 2],
      });

      const marker = L.marker([station.lat, station.lng], {
        icon: stationIcon,
        zIndexOffset: isSelected ? 2000 : isMajor ? 300 : 150,
      });

      marker.bindTooltip(
        `<strong>${station.name} (${station.code})</strong><br/><span style="color:#94a3b8;">Platforms: ${station.platforms}</span>`,
        { direction: 'top', sticky: true }
      );

      marker.on('click', () => {
        onSelectStation(station.code);
      });

      marker.addTo(stationsGroup);
    });
  }, [corridorStations, selectedStation, layerVisibility.stations, currentZoom, onSelectStation, isMapReady, activeTrains, trainCalculations]);

  // 5. Render Signals: Offset 6-8px from their track on driver's side (Requirement 7 & 10)
  // Double yellow rendered as two distinct dots.
  useEffect(() => {
    const { signals: signalsGroup } = layersRef.current;
    if (!signalsGroup) return;
    signalsGroup.clearLayers();

    if (!layerVisibility.signals) return;

    const activeSelectedTrain = activeTrains.find((t) => t.trainId === activeSelectedTrainId);

    // Visible from zoom 13 or if controlling the currently selected train
    const visibleSignals = signals.filter((signal) => {
      const isControlling = Boolean(
        activeSelectedTrain && (
          signal.signalId === activeSelectedTrain.nextSignalId ||
          signal.protectedBlockId === activeSelectedTrain.currentBlockId
        )
      );
      if (isControlling) return true;
      if (currentZoom < 13) return false;

      // Filter by UP/DN and Fast/Slow toggles
      const protectedBlock = trackBlocks.find((b) => b.blockId === signal.protectedBlockId);
      const sigIsUp = Boolean(
        signal.direction === 'UP' ||
        signal.lineType?.includes('UP') ||
        protectedBlock?.direction === 'UP' ||
        protectedBlock?.lineType?.includes('UP')
      );
      const sigIsFast = Boolean(
        signal.lineType?.includes('FAST') ||
        protectedBlock?.lineType?.includes('FAST') ||
        signal.signalId?.includes('-FU') ||
        signal.signalId?.includes('-FD')
      );

      if (directionFilter === 'UP' && !sigIsUp) return false;
      if (directionFilter === 'DN' && sigIsUp) return false;
      if (speedFilter === 'FAST' && !sigIsFast) return false;
      if (speedFilter === 'SLOW' && sigIsFast) return false;

      return true;
    });

    visibleSignals.forEach((signal) => {
      const protectedBlock = trackBlocks.find((b) => b.blockId === signal.protectedBlockId);
      const sigIsUp = Boolean(
        signal.direction === 'UP' ||
        signal.lineType?.includes('UP') ||
        protectedBlock?.direction === 'UP' ||
        protectedBlock?.lineType?.includes('UP')
      );
      const sigIsFast = Boolean(
        signal.lineType?.includes('FAST') ||
        protectedBlock?.lineType?.includes('FAST') ||
        signal.signalId?.includes('-FU') ||
        signal.signalId?.includes('-FD')
      );

      // Shared geometry polyline for this signal's track
      const refLine = resolveTrackPolyline(sigIsFast, sigIsUp);

      const isStarter = signal.signalId.endsWith('-STARTER');
      const isHome = !isStarter && (protectedBlock?.blockType === 'PLATFORM' || signal.signalId.includes('-P'));
      const signalRole = isStarter ? 'STARTER' : isHome ? 'HOME' : 'AUTO';

      // Signal location along corridor
      let locKm: number;
      if (protectedBlock) {
        const fromMeta = getStationMeta(protectedBlock.fromStationCode);
        if (isStarter) {
          locKm = sigIsUp ? fromMeta.locationKm + 0.12 : fromMeta.locationKm - 0.12;
        } else if (protectedBlock.blockType === 'PLATFORM') {
          locKm = sigIsUp ? fromMeta.locationKm - 0.12 : fromMeta.locationKm + 0.12;
        } else {
          locKm = sigIsUp ? fromMeta.locationKm + 0.08 : fromMeta.locationKm - 0.08;
        }
      } else {
        const stMeta = getStationMeta(signal.stationCode);
        locKm = sigIsUp ? stMeta.locationKm - 0.1 : stMeta.locationKm + 0.1;
      }
      locKm = Math.min(totalCorridorKm - 0.04, Math.max(0.04, locKm));

      // Calculate track coordinate and tangent bearing
      const ptOnTrack = turf.along(refLine, locKm, { units: 'kilometers' });
      const pA = turf.along(refLine, Math.max(0.01, locKm - 0.03), { units: 'kilometers' });
      const pB = turf.along(refLine, Math.min(totalCorridorKm, locKm + 0.03), { units: 'kilometers' });
      const trackBearing = turf.bearing(pA, pB);

      // Offset ~6-8px (0.0065 km) on driver's side (left of travel direction)
      const driverAngle = sigIsUp ? trackBearing - 90 : trackBearing + 90;
      const signalPt = turf.destination(ptOnTrack, 0.0065, driverAngle, { units: 'kilometers' });

      const sigLat = signalPt.geometry.coordinates[1];
      const sigLng = signalPt.geometry.coordinates[0];

      const aspect = signal.aspect || 'GREEN';
      const isDoubleYellow = aspect === 'DOUBLE_YELLOW';

      const isControllingSelected = Boolean(
        activeSelectedTrain && (
          signal.signalId === activeSelectedTrain.nextSignalId ||
          signal.protectedBlockId === activeSelectedTrain.currentBlockId
        )
      );

      // Compact signal marker with double-yellow represented as two dots
      const signalIcon = L.divIcon({
        className: 'custom-signal-icon',
        html: `
          <div class="signal-marker-container">
            <div class="signal-head-aspect" style="${isControllingSelected ? 'border: 1.5px solid #38bdf8; box-shadow: 0 0 10px #38bdf8;' : ''}">
              ${
                isDoubleYellow
                  ? `<div class="signal-dot yellow"></div><div class="signal-dot yellow"></div>`
                  : aspect === 'RED'
                  ? `<div class="signal-dot red"></div>`
                  : aspect === 'YELLOW'
                  ? `<div class="signal-dot yellow"></div>`
                  : `<div class="signal-dot green"></div>`
              }
            </div>
            <div class="signal-post"></div>
          </div>
        `,
        iconSize: [12, 18],
        iconAnchor: [6, 9],
      });

      const marker = L.marker([sigLat, sigLng], {
        icon: signalIcon,
        zIndexOffset: isControllingSelected ? 2500 : 350,
      });

      marker.bindTooltip(`
        <div style="font-family: var(--font-mono); font-size: 11px;">
          <strong>Signal ${signal.signalId} [${signalRole}]</strong><br/>
          Aspect: <span style="color: ${getAspectColor(aspect)}; font-weight: 700;">${aspect}</span> (${getAspectLimit(aspect)})<br/>
          Line: ${sigIsUp ? 'UP ↓' : 'DN ↑'} ${sigIsFast ? 'Fast' : 'Slow'}
        </div>
      `, { direction: 'top', sticky: true });

      marker.addTo(signalsGroup);
    });
  }, [signals, trackBlocks, layerVisibility.signals, currentZoom, activeSelectedTrainId, activeTrains, directionFilter, speedFilter, getStationMeta, resolveTrackPolyline, isMapReady]);

  // 6. Update Target Positions and Interpolation State
  useEffect(() => {
    const now = performance.now();
    activeTrains.forEach((train) => {
      const calc = trainCalculations.get(train.trainId);
      if (!calc) return;

      const existingAnim = trainAnimRef.current.get(train.trainId);
      if (existingAnim) {
        existingAnim.startLat = existingAnim.currentLat;
        existingAnim.startLng = existingAnim.currentLng;
        existingAnim.startBearing = existingAnim.currentBearing;
        existingAnim.targetLat = calc.lat;
        existingAnim.targetLng = calc.lng;
        existingAnim.targetBearing = calc.bearing;
        existingAnim.startTime = now;
        existingAnim.duration = 1500;
      } else {
        trainAnimRef.current.set(train.trainId, {
          currentLat: calc.lat,
          currentLng: calc.lng,
          currentBearing: calc.bearing,
          startLat: calc.lat,
          startLng: calc.lng,
          startBearing: calc.bearing,
          targetLat: calc.lat,
          targetLng: calc.lng,
          targetBearing: calc.bearing,
          startTime: now,
          duration: 1500,
        });
      }
    });

    // Clean up anim state for departed trains
    const activeIds = new Set(activeTrains.map((t) => t.trainId));
    for (const id of trainAnimRef.current.keys()) {
      if (!activeIds.has(id)) {
        trainAnimRef.current.delete(id);
      }
    }
  }, [activeTrains, trainCalculations]);

  // 7. Render Trains: Visible from Zoom 8 to 20, Updated In-Place (Never clearLayers on tick)
  useEffect(() => {
    const { trains: trainsGroup } = layersRef.current;
    if (!trainsGroup) return;

    if (!layerVisibility.trains) {
      trainsGroup.clearLayers();
      trainMarkersRef.current.clear();
      return;
    }

    const filteredTrains = activeTrains.filter((train) => {
      const calc = trainCalculations.get(train.trainId);
      if (!calc) return false;

      if (directionFilter === 'UP' && !calc.isUp) return false;
      if (directionFilter === 'DN' && calc.isUp) return false;
      if (speedFilter === 'FAST' && !calc.isFast) return false;
      if (speedFilter === 'SLOW' && calc.isFast) return false;

      return true;
    });

    const activeKeys = new Set<number>();

    filteredTrains.forEach((train, idx) => {
      const calc = trainCalculations.get(train.trainId);
      if (!calc) return;

      activeKeys.add(train.trainId);

      const isSelected = activeSelectedTrainId === train.trainId;
      const catColor = getTrainCatColor(train.trainType);
      const isMoving = (train.speedKmH ?? 0) > 0;
      const dirBadge = `${calc.isUp ? 'UP ↓' : 'DN ↑'} ${calc.isFast ? 'Fast' : 'Slow'}`;

      // Train marker sizing scaled to zoom level (Zoom 8 to 20)
      const baseDiameter = currentZoom < 12 ? (isSelected ? 14 : 10) : currentZoom < 15 ? (isSelected ? 18 : 14) : (isSelected ? 22 : 18);
      const arrowSize = currentZoom < 12 ? 6 : currentZoom < 15 ? 8 : 11;

      const trainIcon = L.divIcon({
        className: 'custom-train-marker',
        html: `
          <div style="position: relative; width: ${baseDiameter}px; height: ${baseDiameter}px; transform: translate(-50%, -50%); cursor: pointer;">
            ${isMoving ? `<div class="train-pulse-ring" style="width: ${baseDiameter + 8}px; height: ${baseDiameter + 8}px; border: 1.5px solid ${catColor};"></div>` : ''}
            <div style="
              width: 100%;
              height: 100%;
              border-radius: 50%;
              background: #090d16;
              border: 2px solid ${catColor};
              box-shadow: ${isSelected ? '0 0 12px #38bdf8, 0 0 0 2px #38bdf8' : '0 2px 6px rgba(0,0,0,0.8)'};
              display: flex;
              align-items: center;
              justify-content: center;
            ">
              <svg
                class="train-arrow-svg"
                width="${arrowSize}"
                height="${arrowSize}"
                viewBox="0 0 24 24"
                fill="${catColor}"
                style="transform: rotate(${calc.bearing}deg); display: block;"
              >
                <path d="M12 2L19 21L12 17L5 21L12 2Z" />
              </svg>
            </div>
          </div>
        `,
        iconSize: [baseDiameter, baseDiameter],
        iconAnchor: [baseDiameter / 2, baseDiameter / 2],
      });

      const tooltipContent = `
        <div style="font-family: var(--font-sans); font-size: 11px; line-height: 1.4;">
          <strong>${train.trainNumber} - ${train.trainName}</strong><br/>
          Speed: <strong>${Math.round(train.speedKmH ?? 0)} km/h</strong> | Dir: <strong>${dirBadge}</strong><br/>
          Location: <strong>${formatTrainLocation(train)}</strong><br/>
          Next Stop: <strong>${formatNextStop(train)}</strong>
        </div>
      `;

      // Update existing marker in-place or create new marker
      const existing = trainMarkersRef.current.get(train.trainId);
      if (existing) {
        existing.setIcon(trainIcon);
        existing.setZIndexOffset(isSelected ? 2000 : 500 + idx);
        existing.setTooltipContent(tooltipContent);
        if (!trainsGroup.hasLayer(existing)) {
          existing.addTo(trainsGroup);
        }
      } else {
        const marker = L.marker([calc.lat, calc.lng], {
          icon: trainIcon,
          zIndexOffset: isSelected ? 2000 : 500 + idx,
        });

        marker.bindTooltip(tooltipContent, { direction: 'top', sticky: true });

        marker.on('click', () => {
          setLocalSelectedTrainId(train.trainId);
          onSelectTrain(train);
        });

        marker.addTo(trainsGroup);
        trainMarkersRef.current.set(train.trainId, marker);
      }
    });

    // Remove departed train markers
    for (const [id, m] of trainMarkersRef.current.entries()) {
      if (!activeKeys.has(id)) {
        m.remove();
        trainMarkersRef.current.delete(id);
      }
    }
  }, [activeTrains, directionFilter, speedFilter, currentZoom, layerVisibility.trains, activeSelectedTrainId, trainCalculations, onSelectTrain, isMapReady]);

  // 8. Continuous requestAnimationFrame Interpolation Loop (Smooth 60fps Movement)
  useEffect(() => {
    let animId: number;

    const step = () => {
      const now = performance.now();
      for (const [trainId, anim] of trainAnimRef.current.entries()) {
        const elapsed = now - anim.startTime;
        const progress = Math.min(1.0, elapsed / anim.duration);

        anim.currentLat = anim.startLat + (anim.targetLat - anim.startLat) * progress;
        anim.currentLng = anim.startLng + (anim.targetLng - anim.startLng) * progress;

        // Bearing angle interpolation
        let diff = (anim.targetBearing - anim.startBearing) % 360;
        if (diff > 180) diff -= 360;
        if (diff < -180) diff += 360;
        anim.currentBearing = anim.startBearing + diff * progress;

        const marker = trainMarkersRef.current.get(trainId);
        if (marker) {
          marker.setLatLng([anim.currentLat, anim.currentLng]);
          const arrowEl = marker.getElement()?.querySelector('.train-arrow-svg') as HTMLElement | null;
          if (arrowEl) {
            arrowEl.style.transform = `rotate(${anim.currentBearing}deg)`;
          }
        }

        // Camera follow selected train
        if (isFollowSelected && trainId === activeSelectedTrainId && mapInstanceRef.current) {
          mapInstanceRef.current.panTo([anim.currentLat, anim.currentLng], { animate: false });
        }
      }

      animId = requestAnimationFrame(step);
    };

    animId = requestAnimationFrame(step);
    return () => cancelAnimationFrame(animId);
  }, [isFollowSelected, activeSelectedTrainId]);

  // Sector Viewport Jump
  const handleSectorChange = (sector: 'ALL' | 'NORTH' | 'CENTRAL' | 'SOUTH') => {
    setSelectedSector(sector);
    const map = mapInstanceRef.current;
    if (!map) return;

    if (sector === 'ALL') {
      handleFitCorridor();
    } else if (sector === 'NORTH') {
      const northCoords: [number, number][] = corridorStations.slice(0, 8).map((s) => [s.lat, s.lng]);
      map.flyToBounds(L.latLngBounds(northCoords), { paddingTopLeft: [70, 310], paddingBottomRight: [40, 40], duration: 1.2 });
    } else if (sector === 'CENTRAL') {
      const centralCoords: [number, number][] = corridorStations.slice(7, 17).map((s) => [s.lat, s.lng]);
      map.flyToBounds(L.latLngBounds(centralCoords), { paddingTopLeft: [70, 310], paddingBottomRight: [40, 40], duration: 1.2 });
    } else if (sector === 'SOUTH') {
      const southCoords: [number, number][] = corridorStations.slice(16, 24).map((s) => [s.lat, s.lng]);
      map.flyToBounds(L.latLngBounds(southCoords), { paddingTopLeft: [70, 310], paddingBottomRight: [40, 40], duration: 1.2 });
    }
  };

  // Fly to train from side panel
  const handleTrainRowClick = (train: TrainSnapshot) => {
    setLocalSelectedTrainId(train.trainId);
    onSelectTrain(train);
    const calc = trainCalculations.get(train.trainId);
    if (calc && mapInstanceRef.current) {
      mapInstanceRef.current.flyTo([calc.lat, calc.lng], 15, { duration: 1.2 });
    }
  };

  // Filter trains in floating left panel
  const displayedTrains = useMemo(() => {
    return activeTrains.filter((train) => {
      const calc = trainCalculations.get(train.trainId);
      if (!calc) return false;

      if (directionFilter === 'UP' && !calc.isUp) return false;
      if (directionFilter === 'DN' && calc.isUp) return false;
      if (speedFilter === 'FAST' && !calc.isFast) return false;
      if (speedFilter === 'SLOW' && calc.isFast) return false;

      if (!trainSearchQuery) return true;
      const q = trainSearchQuery.toLowerCase();
      return (
        train.trainNumber.toLowerCase().includes(q) ||
        train.trainName.toLowerCase().includes(q) ||
        (train.currentBlockId && train.currentBlockId.toLowerCase().includes(q))
      );
    });
  }, [activeTrains, trainCalculations, directionFilter, speedFilter, trainSearchQuery]);

  // Selected train controlling signal callout details (Requirement 8)
  const selectedTrainData = useMemo(() => {
    if (!activeSelectedTrainId) return null;
    const train = activeTrains.find((t) => t.trainId === activeSelectedTrainId);
    if (!train) return null;

    const sig = signals.find((s) => s.signalId === train.nextSignalId || s.protectedBlockId === train.currentBlockId);
    const aspect = sig?.aspect || train.signalAspect || 'GREEN';
    const sigId = sig?.signalId || train.nextSignalId || 'SIG-AUTO';
    const maxLimit = getAspectLimit(aspect);

    return {
      train,
      sigId,
      aspect,
      maxLimit,
    };
  }, [activeSelectedTrainId, activeTrains, signals]);

  // Simulation time formatting
  const simTimeDisplay = useMemo(() => {
    if (!corridorState?.simulationTime) return '10:00:00';
    try {
      const dt = new Date(corridorState.simulationTime);
      return dt.toLocaleTimeString('en-US', { hour12: false });
    } catch {
      return '10:00:00';
    }
  }, [corridorState?.simulationTime]);

  const currentMultiplier = corridorState?.speedMultiplier ?? 1;

  return (
    <div className="live-map-container">
      {/* 1. Full-Bleed Map Canvas */}
      <div ref={mapContainerRef} className="map-viewport" />

      {/* 2. TOP: Floating Glass KPI Bar (Centered, Never covers corridor) */}
      <div className="glass-panel top-kpi-bar">
        <div style={{ display: 'flex', alignItems: 'center', gap: '6px', color: '#38bdf8', fontWeight: 800 }}>
          <Radar size={15} />
          <span>OCC LIVE</span>
        </div>

        <div className="kpi-divider" />

        <div className="kpi-item">
          <span className="kpi-label">Fleet</span>
          <span className="kpi-value">{activeTrains.length}</span>
        </div>

        <div className="kpi-divider" />

        <div className="kpi-item">
          <div style={{ width: '6px', height: '6px', borderRadius: '50%', background: '#38bdf8' }} />
          <span className="kpi-label">In Transit</span>
          <span className="kpi-value" style={{ color: '#38bdf8' }}>{movingTrains.length}</span>
        </div>

        <div className="kpi-divider" />

        <div className="kpi-item">
          <div style={{ width: '6px', height: '6px', borderRadius: '50%', background: waitingTrains.length > 0 ? '#ef4444' : '#64748b' }} />
          <span className="kpi-label">Halted</span>
          <span className="kpi-value" style={{ color: waitingTrains.length > 0 ? '#ef4444' : '#94a3b8' }}>
            {waitingTrains.length}
          </span>
        </div>

        <div className="kpi-divider" />

        <div className="kpi-item">
          <div style={{ width: '6px', height: '6px', borderRadius: '50%', background: delayedTrains.length > 0 ? '#fbbf24' : '#64748b' }} />
          <span className="kpi-label">Delayed</span>
          <span className="kpi-value" style={{ color: delayedTrains.length > 0 ? '#fbbf24' : '#94a3b8' }}>
            {delayedTrains.length}
          </span>
        </div>

        <div className="kpi-divider" />

        <div className="kpi-item">
          <span className="kpi-label">Active Blocks</span>
          <span className="kpi-value" style={{ color: '#818cf8' }}>{occupiedBlocks.length}</span>
        </div>

        <div className="kpi-divider" />

        {/* Sector Quick Jump */}
        <div style={{ display: 'flex', alignItems: 'center', gap: '3px' }}>
          {(['ALL', 'NORTH', 'CENTRAL', 'SOUTH'] as const).map((sector) => (
            <button
              key={sector}
              onClick={() => handleSectorChange(sector)}
              className={`pill-btn ${selectedSector === sector ? 'active' : ''}`}
              title={sector === 'ALL' ? 'Full Corridor' : sector === 'NORTH' ? 'North (VR–BVI)' : sector === 'CENTRAL' ? 'Central (BVI–BA)' : 'South (BA–MMCT)'}
            >
              {sector === 'ALL' ? 'ALL' : sector === 'NORTH' ? 'NTH' : sector === 'CENTRAL' ? 'CEN' : 'STH'}
            </button>
          ))}
        </div>

        <button
          onClick={handleFitCorridor}
          className="pill-btn"
          title="Fit Corridor View"
          style={{ display: 'flex', alignItems: 'center', gap: '3px' }}
        >
          <Maximize2 size={11} />
          <span>Fit</span>
        </button>
      </div>

      {/* 3. CALLOUT: Controlling Signal Callout for Selected Train (Requirement 8) */}
      {selectedTrainData && (
        <div className="controlling-signal-callout">
          <div style={{ width: '8px', height: '8px', borderRadius: '50%', background: getAspectColor(selectedTrainData.aspect), boxShadow: `0 0 8px ${getAspectColor(selectedTrainData.aspect)}` }} />
          <span>Controlling signal: {selectedTrainData.sigId} — {selectedTrainData.aspect} — max {selectedTrainData.maxLimit}</span>
          <button
            onClick={() => setLocalSelectedTrainId(null)}
            style={{ background: 'none', border: 'none', color: '#94a3b8', cursor: 'pointer', padding: 0, marginLeft: '6px' }}
          >
            <X size={13} />
          </button>
        </div>
      )}

      {/* 4. LEFT: Floating Glass Trains List (Collapsible, Does Not Cover Middle) */}
      <div className={`glass-panel trains-drawer ${isTrainsPanelCollapsed ? 'collapsed' : ''}`}>
        {isTrainsPanelCollapsed ? (
          <div
            onClick={() => setIsTrainsPanelCollapsed(false)}
            style={{
              width: '100%',
              height: '100%',
              display: 'flex',
              flexDirection: 'column',
              alignItems: 'center',
              justifyContent: 'space-between',
              padding: '16px 0',
              cursor: 'pointer',
            }}
            title="Expand Trains Panel"
          >
            <ChevronRight size={16} style={{ color: '#38bdf8' }} />
            <div
              style={{
                writingMode: 'vertical-rl',
                transform: 'rotate(180deg)',
                fontFamily: 'var(--font-mono)',
                fontSize: '11px',
                fontWeight: 800,
                color: '#e5edf7',
                letterSpacing: '1px',
                display: 'flex',
                alignItems: 'center',
                gap: '8px',
              }}
            >
              <TrainIcon size={14} style={{ color: '#38bdf8' }} />
              <span>TRAINS ({activeTrains.length})</span>
            </div>
            <div style={{ width: '7px', height: '7px', borderRadius: '50%', background: '#38bdf8' }} />
          </div>
        ) : (
          <>
            {/* Header */}
            <div className="trains-drawer-header">
              <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                <TrainIcon size={16} style={{ color: '#38bdf8' }} />
                <div>
                  <div style={{ fontSize: '13px', fontWeight: 800, color: '#ffffff' }}>
                    Live Fleet ({displayedTrains.length}/{activeTrains.length})
                  </div>
                  <div style={{ fontSize: '10.5px', color: '#94a3b8' }}>
                    Western Railway Telemetry
                  </div>
                </div>
              </div>

              <button
                onClick={() => setIsTrainsPanelCollapsed(true)}
                title="Collapse Panel"
                className="pill-btn"
                style={{ padding: '4px' }}
              >
                <ChevronLeft size={14} />
              </button>
            </div>

            {/* Filters Row: Direction (UP / DN) & Speed (Fast / Slow) (Requirement 9) */}
            <div className="trains-filter-row">
              {/* UP / DN Direction Toggle */}
              <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
                <span style={{ fontSize: '10px', color: '#94a3b8', fontWeight: 700, textTransform: 'uppercase' }}>Direction</span>
                <div style={{ display: 'flex', gap: '3px' }}>
                  {(['ALL', 'UP', 'DN'] as const).map((dir) => (
                    <button
                      key={dir}
                      onClick={() => setDirectionFilter(dir)}
                      className={`pill-btn ${directionFilter === dir ? 'active' : ''}`}
                    >
                      {dir === 'ALL' ? 'Both' : dir === 'UP' ? 'UP ↓' : 'DN ↑'}
                    </button>
                  ))}
                </div>
              </div>

              {/* Fast / Slow Speed Toggle */}
              <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
                <span style={{ fontSize: '10px', color: '#94a3b8', fontWeight: 700, textTransform: 'uppercase' }}>Line Class</span>
                <div style={{ display: 'flex', gap: '3px' }}>
                  {(['ALL', 'FAST', 'SLOW'] as const).map((sp) => (
                    <button
                      key={sp}
                      onClick={() => setSpeedFilter(sp)}
                      className={`pill-btn ${speedFilter === sp ? 'active' : ''}`}
                    >
                      {sp === 'ALL' ? 'Both' : sp}
                    </button>
                  ))}
                </div>
              </div>

              {/* Search input */}
              <div
                style={{
                  display: 'flex',
                  alignItems: 'center',
                  gap: '6px',
                  background: 'rgba(0, 0, 0, 0.35)',
                  border: '1px solid rgba(255, 255, 255, 0.10)',
                  borderRadius: '6px',
                  padding: '4px 8px',
                  marginTop: '2px',
                }}
              >
                <Search size={12} style={{ color: '#94a3b8' }} />
                <input
                  type="text"
                  placeholder="Filter train # or block..."
                  value={trainSearchQuery}
                  onChange={(e) => setTrainSearchQuery(e.target.value)}
                  style={{
                    background: 'transparent',
                    border: 'none',
                    outline: 'none',
                    color: '#e5edf7',
                    fontSize: '11px',
                    width: '100%',
                    fontFamily: 'var(--font-sans)',
                  }}
                />
                {trainSearchQuery && (
                  <button
                    onClick={() => setTrainSearchQuery('')}
                    style={{ background: 'none', border: 'none', color: '#94a3b8', cursor: 'pointer', padding: 0 }}
                  >
                    <X size={11} />
                  </button>
                )}
              </div>
            </div>

            {/* Scrollable Train Cards */}
            <div className="trains-list-scroll">
              {displayedTrains.map((train) => {
                const isSelected = activeSelectedTrainId === train.trainId;
                const catColor = getTrainCatColor(train.trainType);
                const calc = trainCalculations.get(train.trainId);
                const dirBadge = `${calc?.isUp ? 'UP ↓' : 'DN ↑'} ${calc?.isFast ? 'Fast' : 'Slow'}`;
                const speed = Math.round(train.speedKmH ?? 0);
                const aspectColor = getAspectColor(train.signalAspect);

                return (
                  <div
                    key={train.trainId}
                    onClick={() => handleTrainRowClick(train)}
                    className={`train-card ${isSelected ? 'selected' : ''}`}
                  >
                    {/* Top Row: Category dot, Train #, Direction, Aspect */}
                    <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: '3px' }}>
                      <div style={{ display: 'flex', alignItems: 'center', gap: '6px' }}>
                        <div style={{ width: '7px', height: '7px', borderRadius: '50%', background: catColor, boxShadow: `0 0 5px ${catColor}` }} />
                        <span style={{ fontFamily: 'var(--font-mono)', fontSize: '12px', fontWeight: 800, color: '#ffffff' }}>
                          {train.trainNumber}
                        </span>
                      </div>

                      <div style={{ display: 'flex', alignItems: 'center', gap: '4px' }}>
                        <span
                          style={{
                            fontSize: '9.5px',
                            fontFamily: 'var(--font-mono)',
                            fontWeight: 700,
                            padding: '1px 4px',
                            borderRadius: '3px',
                            background: calc?.isUp ? 'rgba(56, 189, 248, 0.15)' : 'rgba(129, 140, 248, 0.15)',
                            color: calc?.isUp ? '#38bdf8' : '#818cf8',
                            border: `1px solid ${calc?.isUp ? 'rgba(56, 189, 248, 0.35)' : 'rgba(129, 140, 248, 0.35)'}`,
                          }}
                        >
                          {dirBadge}
                        </span>
                        <div style={{ width: '5px', height: '5px', borderRadius: '50%', background: aspectColor }} />
                      </div>
                    </div>

                    {/* Middle Row: Name and Speed */}
                    <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', fontSize: '11px', color: '#94a3b8' }}>
                      <span style={{ whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis', maxWidth: '160px' }}>
                        {train.trainName}
                      </span>
                      <span style={{ fontFamily: 'var(--font-mono)', color: '#ffffff', fontWeight: 700 }}>
                        {speed} km/h
                      </span>
                    </div>

                    {/* Location */}
                    <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', fontSize: '10.5px', marginTop: '3px' }}>
                      <span style={{ color: '#64748b' }}>Location:</span>
                      <span style={{ fontFamily: 'var(--font-mono)', color: '#e2e8f0', fontWeight: 600 }}>
                        {formatTrainLocation(train)}
                      </span>
                    </div>

                    {/* Next stop */}
                    <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', fontSize: '10.5px', marginTop: '3px' }}>
                      <span style={{ color: '#64748b' }}>Next stop:</span>
                      <span style={{ fontFamily: 'var(--font-mono)', color: '#38bdf8', fontWeight: 700 }}>
                        {formatNextStop(train)}
                      </span>
                    </div>

                    {/* Block Info */}
                    <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', fontSize: '10px', marginTop: '2px', color: '#64748b' }}>
                      <span>Block:</span>
                      <span style={{ fontFamily: 'var(--font-mono)', color: '#94a3b8' }}>{train.currentBlockId || 'N/A'}</span>
                    </div>
                  </div>
                );
              })}
            </div>
          </>
        )}
      </div>

      {/* 5. BOTTOM-LEFT: Floating Glass Legend (Requirement 3) */}
      <div className={`glass-panel legend-panel ${isTrainsPanelCollapsed ? 'docked-left' : ''}`}>
        <div
          onClick={() => setIsLegendExpanded(!isLegendExpanded)}
          style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: '8px', cursor: 'pointer', fontWeight: 800, color: '#38bdf8' }}
        >
          <div style={{ display: 'flex', alignItems: 'center', gap: '4px' }}>
            <Info size={12} />
            <span>Map Legend</span>
          </div>
          {isLegendExpanded ? <ChevronDown size={12} /> : <ChevronUp size={12} />}
        </div>

        {isLegendExpanded && (
          <div style={{ display: 'flex', flexDirection: 'column', gap: '6px', borderTop: '1px solid rgba(255, 255, 255, 0.08)', marginTop: '6px', paddingTop: '6px' }}>
            {/* Tracks */}
            <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
              <div style={{ display: 'flex', alignItems: 'center', gap: '4px' }}>
                <div style={{ width: '12px', height: '2px', background: '#38bdf8' }} />
                <span>Fast UP</span>
              </div>
              <div style={{ display: 'flex', alignItems: 'center', gap: '4px' }}>
                <div style={{ width: '12px', height: '2px', background: '#818cf8' }} />
                <span>Fast DN</span>
              </div>
            </div>
            <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
              <div style={{ display: 'flex', alignItems: 'center', gap: '4px' }}>
                <div style={{ width: '12px', height: '2px', background: '#fb7185' }} />
                <span>Slow UP</span>
              </div>
              <div style={{ display: 'flex', alignItems: 'center', gap: '4px' }}>
                <div style={{ width: '12px', height: '2px', background: '#fb923c' }} />
                <span>Slow DN</span>
              </div>
            </div>

            {/* Signals */}
            <div style={{ display: 'flex', alignItems: 'center', gap: '6px', flexWrap: 'wrap' }}>
              <span style={{ color: '#94a3b8' }}>Signals:</span>
              <span style={{ color: '#10b981' }}>● GRN</span>
              <span style={{ color: '#f59e0b' }}>● YEL</span>
              <span style={{ color: '#eab308' }}>●● 2YEL</span>
              <span style={{ color: '#ef4444' }}>● RED</span>
            </div>

            {/* Fleets */}
            <div style={{ display: 'flex', alignItems: 'center', gap: '6px', flexWrap: 'wrap' }}>
              <span style={{ color: '#94a3b8' }}>Fleet:</span>
              <span style={{ color: '#06b6d4' }}>● Local</span>
              <span style={{ color: '#0284c7' }}>● Express</span>
              <span style={{ color: '#a855f7' }}>● Superfast</span>
              <span style={{ color: '#f59e0b' }}>● Premium</span>
            </div>
          </div>
        )}
      </div>

      {/* 6. BOTTOM-RIGHT: Floating Speed Pill (1x/2x/5x) & Simulation Clock (Requirement 3) */}
      <div className="glass-panel speed-pill-panel">
        <div style={{ display: 'flex', alignItems: 'center', gap: '5px' }}>
          <Clock size={13} style={{ color: '#38bdf8' }} />
          <span style={{ fontFamily: 'var(--font-mono)', fontWeight: 800, fontSize: '12px', color: '#ffffff' }}>
            {simTimeDisplay}
          </span>
        </div>

        <div className="kpi-divider" />

        <div style={{ display: 'flex', alignItems: 'center', gap: '3px' }}>
          <button
            onClick={() => api.startSimulation()}
            title="Start Simulation"
            className="pill-btn"
            style={{ padding: '3px 6px', color: '#10b981' }}
          >
            <Play size={11} />
          </button>
          <button
            onClick={() => api.pauseSimulation()}
            title="Pause Simulation"
            className="pill-btn"
            style={{ padding: '3px 6px', color: '#fbbf24' }}
          >
            <Pause size={11} />
          </button>
        </div>

        <div className="kpi-divider" />

        {/* Speed Pills: 1x / 2x / 5x */}
        <div style={{ display: 'flex', alignItems: 'center', gap: '3px' }}>
          {[1, 2, 5].map((s) => (
            <button
              key={s}
              onClick={() => api.setSpeed(s)}
              className={`pill-btn ${currentMultiplier === s ? 'active' : ''}`}
              style={{ fontFamily: 'var(--font-mono)', padding: '2px 6px' }}
            >
              {s}x
            </button>
          ))}
        </div>

        {/* Follow selected train toggle */}
        {activeSelectedTrainId && (
          <>
            <div className="kpi-divider" />
            <button
              onClick={() => setIsFollowSelected(!isFollowSelected)}
              className={`pill-btn ${isFollowSelected ? 'active' : ''}`}
              style={{ display: 'flex', alignItems: 'center', gap: '3px' }}
            >
              <Crosshair size={11} />
              <span>{isFollowSelected ? 'Lock' : 'Track'}</span>
            </button>
          </>
        )}
      </div>
    </div>
  );
};
