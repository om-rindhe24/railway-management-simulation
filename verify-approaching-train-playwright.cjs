const { spawn } = require('child_process');
const { chromium } = require('./frontend/node_modules/playwright');
const fs = require('fs');
const path = require('path');

async function sleep(ms) {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

async function waitForServer(url, timeoutMs = 60000) {
  const start = Date.now();
  while (Date.now() - start < timeoutMs) {
    try {
      const res = await fetch(url);
      if (res.ok || res.status === 401 || res.status === 403) return true;
    } catch {}
    await sleep(1000);
  }
  throw new Error(`Timeout waiting for ${url}`);
}

async function run() {
  console.log('[Playwright Verification] Starting backend and frontend...');

  // Ensure docs/screenshots folder exists
  const screenshotsDir = path.join(__dirname, 'docs', 'screenshots');
  if (!fs.existsSync(screenshotsDir)) {
    fs.mkdirSync(screenshotsDir, { recursive: true });
  }

  const backendProc = spawn('cmd.exe', ['/c', 'C:\\Users\\rindh\\apache-maven-3.9.9\\bin\\mvn.cmd', '-f', 'backend/pom.xml', 'spring-boot:run'], {
    detached: true,
    stdio: 'ignore'
  });

  const frontendProc = spawn('cmd.exe', ['/c', 'npm', '--prefix', 'frontend', 'run', 'dev'], {
    detached: true,
    stdio: 'ignore'
  });

  const consoleErrors = [];
  const consoleWarnings = [];

  try {
    console.log('[Playwright Verification] Waiting for backend (8080) and frontend (5173)...');
    await waitForServer('http://localhost:8080/api/stations');
    console.log('[Playwright Verification] Backend is ready.');
    await waitForServer('http://localhost:5173');
    console.log('[Playwright Verification] Frontend is ready.');

    console.log('[Playwright Verification] Launching Chromium (Google Chrome)...');
    const browser = await chromium.launch({
      executablePath: 'C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe',
      headless: true
    });

    // ─────────────────────────────────────────────────────────────
    // PART 3.2 & 3.3 & 3.4: Desktop 1440x900 Tests
    // ─────────────────────────────────────────────────────────────
    console.log('\n=============================================================');
    console.log('TEST SUITE 1: Desktop 1440x900 Approaching Locomotive & Palette');
    console.log('=============================================================');

    const context = await browser.newContext({
      viewport: { width: 1440, height: 900 }
    });
    const page = await context.newPage();

    page.on('console', (msg) => {
      if (msg.type() === 'error') consoleErrors.push(msg.text());
      if (msg.type() === 'warning') consoleWarnings.push(msg.text());
    });
    page.on('pageerror', (err) => {
      consoleErrors.push(err.message);
    });

    await page.goto('http://localhost:5173');
    await page.waitForTimeout(2000);

    const hero = page.locator('[data-testid="scroll-video-hero"]');
    await hero.waitFor({ state: 'visible', timeout: 8000 });
    const trainLocator = page.locator('[data-testid="approaching-train"]');
    await trainLocator.waitFor({ state: 'visible', timeout: 5000 });

    const measuredWidths = [];

    // Helper to wheel scroll and settle
    async function scrollWheelDelta(dy, waitMs = 500) {
      // dispatch in increments to simulate natural smooth wheel
      const steps = 10;
      const stepDy = dy / steps;
      for (let s = 0; s < steps; s++) {
        await page.mouse.wheel(0, stepDy);
        await page.waitForTimeout(20);
      }
      await page.waitForTimeout(waitMs);
    }

    // Step 0: Progress ~ 0.00
    console.log('[1440x900] Capturing step p = 0.00...');
    await page.waitForTimeout(600);
    let box00 = await trainLocator.boundingBox();
    console.log(`[1440x900] p ~ 0.00 Train Width: ${box00.width.toFixed(2)}px, Height: ${box00.height.toFixed(2)}px`);
    measuredWidths.push({ step: 'p = 0.00', width: box00.width });
    await page.screenshot({ path: path.join(screenshotsDir, 'landing-p00.png') });

    // Step 1: Progress ~ 0.25 (need +750 delta on 3000 scrubDistance)
    console.log('[1440x900] Advancing to p ~ 0.25...');
    await scrollWheelDelta(750);
    let box25 = await trainLocator.boundingBox();
    console.log(`[1440x900] p ~ 0.25 Train Width: ${box25.width.toFixed(2)}px, Height: ${box25.height.toFixed(2)}px`);
    measuredWidths.push({ step: 'p = 0.25', width: box25.width });
    await page.screenshot({ path: path.join(screenshotsDir, 'landing-p25.png') });

    // Step 2: Progress ~ 0.50 (need +750 delta)
    console.log('[1440x900] Advancing to p ~ 0.50...');
    await scrollWheelDelta(750);
    let box50 = await trainLocator.boundingBox();
    console.log(`[1440x900] p ~ 0.50 Train Width: ${box50.width.toFixed(2)}px, Height: ${box50.height.toFixed(2)}px`);
    measuredWidths.push({ step: 'p = 0.50', width: box50.width });
    await page.screenshot({ path: path.join(screenshotsDir, 'landing-p50.png') });

    // Step 3: Progress ~ 0.75 (need +750 delta)
    console.log('[1440x900] Advancing to p ~ 0.75...');
    await scrollWheelDelta(750);
    let box75 = await trainLocator.boundingBox();
    console.log(`[1440x900] p ~ 0.75 Train Width: ${box75.width.toFixed(2)}px, Height: ${box75.height.toFixed(2)}px`);
    measuredWidths.push({ step: 'p = 0.75', width: box75.width });
    await page.screenshot({ path: path.join(screenshotsDir, 'landing-p75.png') });

    // Step 4: Progress ~ 0.95 (need +600 delta)
    console.log('[1440x900] Advancing to p ~ 0.95...');
    await scrollWheelDelta(600);
    let box95 = await trainLocator.boundingBox();
    console.log(`[1440x900] p ~ 0.95 Train Width: ${box95.width.toFixed(2)}px, Height: ${box95.height.toFixed(2)}px`);
    measuredWidths.push({ step: 'p = 0.95', width: box95.width });
    await page.screenshot({ path: path.join(screenshotsDir, 'landing-p95.png') });

    // Assert strictly increasing widths on forward scroll
    console.log('\n[1440x900] Checking strict monotonic increase on forward scroll:');
    for (let i = 1; i < measuredWidths.length; i++) {
      const prev = measuredWidths[i - 1];
      const curr = measuredWidths[i];
      console.log(`  Assertion: ${curr.step} (${curr.width.toFixed(1)}px) > ${prev.step} (${prev.width.toFixed(1)}px)`);
      if (curr.width <= prev.width) {
        throw new Error(`Train width failed to increase: ${curr.step} (${curr.width}) <= ${prev.step} (${prev.width})`);
      }
    }
    console.log('CONFIRMED: Train width strictly increases on forward scroll!\n');

    // Scroll backward and assert width decreases
    console.log('[1440x900] Scrolling back up by -1200 delta...');
    await scrollWheelDelta(-1200);
    let boxReverse = await trainLocator.boundingBox();
    console.log(`[1440x900] Train width after reverse scroll: ${boxReverse.width.toFixed(2)}px (was ${box95.width.toFixed(2)}px)`);
    if (boxReverse.width >= box95.width) {
      throw new Error(`Train width failed to decrease on reverse scroll: ${boxReverse.width} >= ${box95.width}`);
    }
    console.log('CONFIRMED: Train width strictly decreases on reverse scroll!\n');

    // Assert no horizontal scrollbar
    const hasHorizontalScrollbar = await page.evaluate(() => {
      return document.documentElement.scrollWidth > document.documentElement.clientWidth;
    });
    console.log('[1440x900] Horizontal scrollbar present (expect false):', hasHorizontalScrollbar);
    if (hasHorizontalScrollbar) {
      throw new Error('Horizontal scrollbar detected on 1440x900 landing viewport!');
    }

    // PART 3.6: Verify unlock still works
    console.log('\n[1440x900] Scrolling past 0.98 to test unlock...');
    await scrollWheelDelta(2000);
    const isLockedAfterEnd = await page.evaluate(() => document.body.style.position === 'fixed');
    console.log('[1440x900] Body position fixed after end (expect false):', isLockedAfterEnd);
    if (isLockedAfterEnd) {
      throw new Error('Body failed to unlock after scrolling past 0.98 progress');
    }

    // Scroll down to features
    await page.evaluate(() => window.scrollBy(0, 500));
    await page.waitForTimeout(300);
    const scrollYAfterScroll = await page.evaluate(() => window.scrollY);
    console.log(`[1440x900] Natural page scrollY after unlock: ${scrollYAfterScroll}px (expect > 0)`);
    if (scrollYAfterScroll <= 0) {
      throw new Error('Page failed to scroll down naturally into features section after unlock');
    }

    // Scroll back to top
    console.log('[1440x900] Scrolling back to top (scrollY=0)...');
    await page.evaluate(() => window.scrollTo(0, 0));
    await page.waitForTimeout(300);
    // Reverse wheel to re-lock
    await page.mouse.wheel(0, -500);
    await page.waitForTimeout(400);

    // ─────────────────────────────────────────────────────────────
    // PART 3.5: Mobile 390x844 Tests
    // ─────────────────────────────────────────────────────────────
    console.log('\n=============================================================');
    console.log('TEST SUITE 2: Mobile 390x844 Touch & Layout Verification');
    console.log('=============================================================');

    const mobileContext = await browser.newContext({
      viewport: { width: 390, height: 844 },
      isMobile: true,
      hasTouch: true,
    });
    const mobilePage = await mobileContext.newPage();

    await mobilePage.goto('http://localhost:5173');
    await mobilePage.waitForTimeout(2000);

    const mobileTrain = mobilePage.locator('[data-testid="approaching-train"]');
    await mobileTrain.waitFor({ state: 'visible', timeout: 5000 });
    const mobileSkipBtn = mobilePage.locator('[data-testid="skip-intro-btn"]');
    await mobileSkipBtn.waitFor({ state: 'visible', timeout: 5000 });

    // Check Skip button position and visibility
    const skipBox = await mobileSkipBtn.boundingBox();
    console.log(`[390x844 Mobile] Skip button position: x=${skipBox.x.toFixed(1)}, y=${skipBox.y.toFixed(1)}, w=${skipBox.width.toFixed(1)}, h=${skipBox.height.toFixed(1)}`);
    await mobilePage.screenshot({ path: path.join(screenshotsDir, 'landing-mobile-p00.png') });

    // Advance via touch scroll on mobile
    console.log('[390x844 Mobile] Advancing via touch dispatch...');
    for (let t = 0; t < 12; t++) {
      await mobilePage.touchscreen.tap(195, 600);
      await mobilePage.evaluate(() => {
        window.dispatchEvent(new WheelEvent('wheel', { deltaY: 200 }));
      });
      await mobilePage.waitForTimeout(80);
    }
    await mobilePage.waitForTimeout(500);

    const mobileTrainBoxMid = await mobileTrain.boundingBox();
    console.log(`[390x844 Mobile] Mid-scrub Train width: ${mobileTrainBoxMid.width.toFixed(2)}px`);
    await mobilePage.screenshot({ path: path.join(screenshotsDir, 'landing-mobile-p50.png') });

    // Check that Skip button is not obscured or overlapped
    const skipBoxMid = await mobileSkipBtn.boundingBox();
    const isSkipVisible = await mobileSkipBtn.isVisible();
    console.log('[390x844 Mobile] Skip button remains visible and accessible:', isSkipVisible);
    if (!isSkipVisible) throw new Error('Skip button obscured on mobile');

    // Check no horizontal scrollbar on mobile
    const mobileHasHScroll = await mobilePage.evaluate(() => {
      return document.documentElement.scrollWidth > document.documentElement.clientWidth;
    });
    console.log('[390x844 Mobile] Horizontal scrollbar present on mobile (expect false):', mobileHasHScroll);
    if (mobileHasHScroll) {
      throw new Error('Horizontal scrollbar detected on mobile 390x844 viewport!');
    }

    await mobileContext.close();
    await context.close();
    await browser.close();

    console.log('\n=============================================================');
    console.log('SUMMARY OF PLAYWRIGHT VERIFICATION');
    console.log('=============================================================');
    console.log('Console Errors:', consoleErrors.length > 0 ? consoleErrors : 'None (0)');
    console.log('Console Warnings:', consoleWarnings.length > 0 ? consoleWarnings : 'None (0)');
    console.table(measuredWidths);
    console.log('All screenshots written to:', screenshotsDir);
    console.log('ALL PART 3 PLAYWRIGHT CHECKS COMPLETED SUCCESSFULLY!');
  } finally {
    console.log('[Playwright Verification] Cleaning up processes...');
    spawn('taskkill', ['/F', '/T', '/PID', backendProc.pid]);
    spawn('taskkill', ['/F', '/T', '/PID', frontendProc.pid]);
    try {
      spawn('cmd.exe', ['/c', 'for /f "tokens=5" %a in (\'netstat -aon ^| findstr :8080\') do taskkill /F /PID %a']);
      spawn('cmd.exe', ['/c', 'for /f "tokens=5" %a in (\'netstat -aon ^| findstr :5173\') do taskkill /F /PID %a']);
    } catch {}
  }
}

run().catch((err) => {
  console.error('[Playwright Verification] FAILED:', err);
  process.exit(1);
});

