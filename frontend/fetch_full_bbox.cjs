const https = require('https');
const fs = require('fs');

// Query railway=rail in the exact bounding box requested: south=18.96 west=72.78 north=19.47 east=72.90
const overpassQuery = `[out:json][timeout:180];
(
  way["railway"~"rail|subway|light_rail"](18.96,72.78,19.47,72.90);
);
out body;
>;
out skel qt;`;

console.log('Querying Overpass API for all rails in bbox south=18.96 west=72.78 north=19.47 east=72.90...');
const postData = 'data=' + encodeURIComponent(overpassQuery);

function queryServer(hostname) {
  return new Promise((resolve, reject) => {
    const req = https.request({
      hostname,
      path: '/api/interpreter',
      method: 'POST',
      headers: {
        'Content-Type': 'application/x-www-form-urlencoded',
        'Content-Length': Buffer.byteLength(postData),
        'User-Agent': 'RailwayManagementSystem/1.0'
      }
    }, (res) => {
      let data = '';
      res.on('data', chunk => data += chunk);
      res.on('end', () => {
        if (res.statusCode === 200 && data.length > 5000) {
          resolve(data);
        } else {
          reject(new Error(`Status ${res.statusCode}, len ${data.length}`));
        }
      });
    });
    req.on('error', reject);
    req.write(postData);
    req.end();
  });
}

async function run() {
  const servers = [
    'overpass.kumi.systems',
    'overpass-api.de',
    'maps.mail.ru/osm/tools/overpass'
  ];
  for (const s of servers) {
    try {
      console.log(`Trying ${s}...`);
      const data = await queryServer(s);
      fs.writeFileSync('overpass_raw.json', data);
      console.log(`Success with ${s}! Raw size: ${data.length}`);
      return;
    } catch (err) {
      console.error(`Failed ${s}:`, err.message);
    }
  }
}

run();
