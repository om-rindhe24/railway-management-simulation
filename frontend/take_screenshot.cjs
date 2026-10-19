const puppeteer = require('puppeteer-core');
const path = require('path');

async function capture() {
  console.log('Launching browser with puppeteer-core...');
  const browser = await puppeteer.launch({
    executablePath: 'C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe',
    headless: 'new',
    args: ['--no-sandbox', '--disable-setuid-sandbox']
  });

  const page = await browser.newPage();
  await page.setViewport({ width: 1440, height: 900 });

  console.log('Navigating to http://localhost:5173...');
  await page.goto('http://localhost:5173', { waitUntil: 'networkidle2' });

  // Click on Live Train Spot
  console.log('Clicking on Live Train Spot...');
  const clicked = await page.evaluate(() => {
    const navItems = Array.from(document.querySelectorAll('.nav-item'));
    const spotItem = navItems.find(el => el.textContent.includes('Live Train Spot') || el.textContent.includes('Train Spot'));
    if (spotItem) {
      spotItem.click();
      return true;
    }
    return false;
  });

  console.log('Clicked Live Train Spot:', clicked);
  await new Promise(r => setTimeout(r, 4000));

  const outPath1 = path.join(__dirname, 'live_train_spot_overview.png');
  await page.screenshot({ path: outPath1 });
  console.log('Saved overview screenshot:', outPath1);

  // Zoom into Nallasopara
  console.log('Zooming into Nallasopara...');
  await page.evaluate(() => {
    // click NSP station marker or double click near center
    const nspNode = document.querySelector('.snapped-station-node');
    if (nspNode) {
      nspNode.dispatchEvent(new MouseEvent('dblclick', { bubbles: true }));
    }
  });

  await new Promise(r => setTimeout(r, 3000));

  const outPath2 = path.join(__dirname, 'live_train_spot_nallasopara.png');
  await page.screenshot({ path: outPath2 });
  console.log('Saved Nallasopara screenshot:', outPath2);

  await browser.close();
  console.log('Done!');
}

capture().catch(err => {
  console.error('Capture error:', err);
  process.exit(1);
});
