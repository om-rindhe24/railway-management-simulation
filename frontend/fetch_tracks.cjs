const https = require('https');
const fs = require('fs');

const overpassQuery = `[out:json][timeout:90];
(
  way["railway"="rail"]["operator"~"Western",i](18.96,72.80,19.47,72.86);
  way["railway"="rail"](18.96,72.81,19.47,72.85);
);
out body;
>;
out skel qt;`;

console.log('Querying Overpass API...');
const postData = 'data=' + encodeURIComponent(overpassQuery);

const options = {
  hostname: 'overpass-api.de',
  path: '/api/interpreter',
  method: 'POST',
  headers: {
    'Content-Type': 'application/x-www-form-urlencoded',
    'Content-Length': Buffer.byteLength(postData),
    'User-Agent': 'RailwayManagementSystem/1.0'
  }
};

const req = https.request(options, (res) => {
  let data = '';
  res.on('data', chunk => data += chunk);
  res.on('end', () => {
    console.log('Response status:', res.statusCode, 'Data length:', data.length);
    if (res.statusCode === 200 && data.length > 500) {
      fs.writeFileSync('overpass_raw.json', data);
      console.log('Saved overpass_raw.json successfully!');
    } else {
      console.log('Trying mirror kumi.systems...');
      tryMirror();
    }
  });
});

req.on('error', err => {
  console.error('Error fetching overpass primary, trying mirror...', err.message);
  tryMirror();
});

req.write(postData);
req.end();

function tryMirror() {
  const mirrorOptions = {
    hostname: 'overpass.kumi.systems',
    path: '/api/interpreter',
    method: 'POST',
    headers: {
      'Content-Type': 'application/x-www-form-urlencoded',
      'Content-Length': Buffer.byteLength(postData),
      'User-Agent': 'RailwayManagementSystem/1.0'
    }
  };
  const mreq = https.request(mirrorOptions, (res) => {
    let data = '';
    res.on('data', chunk => data += chunk);
    res.on('end', () => {
      console.log('Mirror response status:', res.statusCode, 'Data length:', data.length);
      if (res.statusCode === 200 && data.length > 500) {
        fs.writeFileSync('overpass_raw.json', data);
        console.log('Saved overpass_raw.json successfully via mirror!');
      } else {
        console.error('Mirror also failed:', res.statusCode);
      }
    });
  });
  mreq.on('error', err => console.error('Mirror error:', err));
  mreq.write(postData);
  mreq.end();
}
