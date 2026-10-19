const fs = require('fs');
const turf = require('@turf/turf');

// 1. Read Overpass raw data
const rawData = JSON.parse(fs.readFileSync('overpass_raw.json', 'utf8'));

// Extract nodes into map
const nodes = new Map();
for (const el of rawData.elements) {
  if (el.type === 'node') {
    nodes.set(el.id, [el.lon, el.lat]); // GeoJSON is [lng, lat]
  }
}

// Extract ways
const ways = [];
for (const el of rawData.elements) {
  if (el.type === 'way' && el.nodes && el.nodes.length >= 2) {
    const coords = [];
    for (const nid of el.nodes) {
      const pt = nodes.get(nid);
      if (pt) coords.push(pt);
    }
    if (coords.length >= 2) {
      ways.push({
        id: el.id,
        tags: el.tags || {},
        coords: coords
      });
    }
  }
}

console.log(`Loaded ${ways.length} railway ways.`);

// 24 Station coordinates (VR -> MMCT)
const stationsData = [
  { code: "VR", name: "Virar", lat: 19.4538, lng: 72.8113 },
  { code: "NSP", name: "Nalasopara", lat: 19.4182, lng: 72.8188 },
  { code: "BSR", name: "Vasai Road", lat: 19.3804, lng: 72.8335 },
  { code: "NIG", name: "Naigaon", lat: 19.3518, lng: 72.8486 },
  { code: "BYR", name: "Bhayandar", lat: 19.3134, lng: 72.8530 },
  { code: "MIRA", name: "Mira Road", lat: 19.2810, lng: 72.8566 },
  { code: "DIC", name: "Dahisar", lat: 19.2562, lng: 72.8596 },
  { code: "BVI", name: "Borivali", lat: 19.2294, lng: 72.8573 },
  { code: "KND", name: "Kandivali", lat: 19.2045, lng: 72.8523 },
  { code: "MLD", name: "Malad", lat: 19.1868, lng: 72.8486 },
  { code: "GMN", name: "Goregaon", lat: 19.1643, lng: 72.8488 },
  { code: "JOS", name: "Jogeshwari", lat: 19.1352, lng: 72.8493 },
  { code: "AND", name: "Andheri", lat: 19.1197, lng: 72.8468 },
  { code: "VLP", name: "Vile Parle", lat: 19.0998, lng: 72.8441 },
  { code: "STC", name: "Santacruz", lat: 19.0827, lng: 72.8417 },
  { code: "KHR", name: "Khar Road", lat: 19.0699, lng: 72.8398 },
  { code: "BA", name: "Bandra", lat: 19.0553, lng: 72.8406 },
  { code: "MM", name: "Mahim", lat: 19.0409, lng: 72.8436 },
  { code: "MRU", name: "Matunga Road", lat: 19.0278, lng: 72.8437 },
  { code: "DDR", name: "Dadar", lat: 19.0185, lng: 72.8427 },
  { code: "PR", name: "Prabhadevi", lat: 19.0118, lng: 72.8358 },
  { code: "LP", name: "Lower Parel", lat: 18.9957, lng: 72.8310 },
  { code: "MX", name: "Mahalaxmi", lat: 18.9828, lng: 72.8242 },
  { code: "MMCT", name: "Mumbai Central", lat: 18.9696, lng: 72.8194 }
];

function distKm(p1, p2) {
  return turf.distance(turf.point(p1), turf.point(p2), { units: 'kilometers' });
}

// Filter ways along the western railway corridor (longitudes 72.80 to 72.87)
const westernWays = ways.filter(w => {
  // reject harbor line branches east of 72.85 below 19.05
  // check coords
  const avgLon = w.coords.reduce((a, c) => a + c[0], 0) / w.coords.length;
  const avgLat = w.coords.reduce((a, c) => a + c[1], 0) / w.coords.length;
  if (avgLon > 72.865 && avgLat < 19.2) return false;
  return avgLon >= 72.80 && avgLon <= 72.87 && avgLat >= 18.96 && avgLat <= 19.46;
});

console.log(`Filtered ${westernWays.length} western railway ways.`);

// Build graph with a 35m tolerance so switches and track gaps connect
const ptList = [];
function getOrAddPt(pt) {
  for (let i = 0; i < ptList.length; i++) {
    if (distKm(pt, ptList[i]) < 0.035) { // 35 meters
      return i;
    }
  }
  ptList.push(pt);
  return ptList.length - 1;
}

const graph = new Map();
function addEdge(k1, k2, cost) {
  if (!graph.has(k1)) graph.set(k1, []);
  graph.get(k1).push({ to: k2, cost });
  if (!graph.has(k2)) graph.set(k2, []);
  graph.get(k2).push({ to: k1, cost });
}

for (const w of westernWays) {
  for (let i = 0; i < w.coords.length - 1; i++) {
    const i1 = getOrAddPt(w.coords[i]);
    const i2 = getOrAddPt(w.coords[i + 1]);
    if (i1 !== i2) {
      addEdge(i1, i2, distKm(w.coords[i], w.coords[i + 1]));
    }
  }
}

console.log(`Graph has ${ptList.length} nodes and ${graph.size} active vertices.`);

// Find closest vertex for each station
const stationNodes = stationsData.map(st => {
  let bestIdx = -1;
  let bestDist = Infinity;
  for (let i = 0; i < ptList.length; i++) {
    const d = distKm([st.lng, st.lat], ptList[i]);
    if (d < bestDist) {
      bestDist = d;
      bestIdx = i;
    }
  }
  return { ...st, nodeIdx: bestIdx, distMeters: (bestDist * 1000).toFixed(0) };
});

function dijkstra(startIdx, endIdx) {
  const dist = new Map();
  const prev = new Map();
  const pq = [{ node: startIdx, d: 0 }];
  dist.set(startIdx, 0);

  while (pq.length > 0) {
    pq.sort((a, b) => a.d - b.d);
    const { node: u, d: uDist } = pq.shift();
    if (u === endIdx) break;
    if (uDist > (dist.get(u) || Infinity)) continue;

    for (const edge of (graph.get(u) || [])) {
      const v = edge.to;
      const alt = uDist + edge.cost;
      if (alt < (dist.get(v) || Infinity)) {
        dist.set(v, alt);
        prev.set(v, u);
        pq.push({ node: v, d: alt });
      }
    }
  }

  if (!prev.has(endIdx) && startIdx !== endIdx) return null;
  const path = [];
  let curr = endIdx;
  while (curr !== undefined) {
    path.unshift(curr);
    if (curr === startIdx) break;
    curr = prev.get(curr);
  }
  return path;
}

const fullPathNodes = [];
let failures = 0;
for (let i = 0; i < stationNodes.length - 1; i++) {
  const s1 = stationNodes[i];
  const s2 = stationNodes[i + 1];
  const p = dijkstra(s1.nodeIdx, s2.nodeIdx);
  if (!p) {
    console.warn(`Could not connect ${s1.code} to ${s2.code} via graph; falling back to direct line`);
    failures++;
    if (i === 0) fullPathNodes.push(s1.nodeIdx);
    fullPathNodes.push(s2.nodeIdx);
  } else {
    if (fullPathNodes.length === 0) {
      fullPathNodes.push(...p);
    } else {
      fullPathNodes.push(...p.slice(1));
    }
  }
}

console.log(`Path reconstruction completed with ${failures} segment fallbacks. Total path nodes: ${fullPathNodes.length}`);

// Generate clean coordinates list
const coords = fullPathNodes.map(idx => ptList[idx]);
const cleanCoords = [coords[0]];
for (let i = 1; i < coords.length; i++) {
  if (distKm(coords[i], cleanCoords[cleanCoords.length - 1]) > 0.003) { // 3m min spacing
    cleanCoords.push(coords[i]);
  }
}

const trackLine = turf.lineString(cleanCoords);
const totalDistanceKm = turf.length(trackLine, { units: 'kilometers' });
console.log(`Total real railway corridor track length: ${totalDistanceKm.toFixed(2)} km across ${cleanCoords.length} points.`);

// Snap stations
const snappedStations = stationsData.map(st => {
  const pt = turf.point([st.lng, st.lat]);
  const snapped = turf.nearestPointOnLine(trackLine, pt);
  return {
    code: st.code,
    name: st.name,
    original: [st.lng, st.lat],
    snapped: snapped.geometry.coordinates,
    locationKm: snapped.properties.location,
    distFromOriginalMeters: (turf.distance(pt, snapped, { units: 'kilometers' }) * 1000).toFixed(1)
  };
});

// Build GeoJSON output
const geojson = {
  type: "FeatureCollection",
  properties: {
    corridor: "Western Railway",
    section: "Virar (VR) to Mumbai Central (MMCT)",
    totalDistanceKm: totalDistanceKm
  },
  features: [
    {
      type: "Feature",
      properties: {
        id: "MAIN_WESTERN_LINE",
        name: "Western Railway Main Line (VR -> MMCT)",
        totalKm: totalDistanceKm
      },
      geometry: {
        type: "LineString",
        coordinates: cleanCoords
      }
    },
    ...snappedStations.map(st => ({
      type: "Feature",
      properties: {
        code: st.code,
        name: st.name,
        locationKm: st.locationKm
      },
      geometry: {
        type: "Point",
        coordinates: st.snapped
      }
    }))
  ]
};

fs.writeFileSync('src/data/westernLineTrack.geojson', JSON.stringify(geojson, null, 2));
console.log('Successfully wrote src/data/westernLineTrack.geojson with full real track geometry!');
