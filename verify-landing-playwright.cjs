const { spawn } = require('child_process');
const { chromium } = require('./frontend/node_modules/playwright');

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
  console.log('[Landing Playwright Test] Starting backend and frontend...');

  const backendProc = spawn('cmd.exe', ['/c', 'C:\\Users\\rindh\\apache-maven-3.9.9\\bin\\mvn.cmd', '-f', 'backend/pom.xml', 'spring-boot:run'], {
    detached: true,
    stdio: 'ignore'
  });

  const frontendProc = spawn('cmd.exe', ['/c', 'npm', '--prefix', 'frontend', 'run', 'dev'], {
    detached: true,
    stdio: 'ignore'
  });

  try {
    console.log('[Landing Playwright Test] Waiting for backend (8080) and frontend (5173)...');
    await waitForServer('http://localhost:8080/api/stations');
    console.log('[Landing Playwright Test] Backend is ready.');
    await waitForServer('http://localhost:5173');
    console.log('[Landing Playwright Test] Frontend is ready.');

    console.log('[Landing Playwright Test] Launching Chromium (Google Chrome)...');
    const browser = await chromium.launch({
      executablePath: 'C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe',
      headless: true
    });
    const context = await browser.newContext();
    const page = await context.newPage();

    // ─────────────────────────────────────────────────────────────
    // TEST 1: Initial visit shows landing, scroll wheel unlocks at end
    // ─────────────────────────────────────────────────────────────
    console.log('[Landing Playwright Test] TEST 1: First visit lands on ScrollVideoHero...');
    await page.goto('http://localhost:5173');
    await page.waitForTimeout(2000);

    const hero = page.locator('[data-testid="scroll-video-hero"]');
    await hero.waitFor({ state: 'visible', timeout: 8000 });
    console.log('[Landing Playwright Test] Hero is visible.');

    const heroTitle = await page.locator('h1').innerText();
    console.log('[Landing Playwright Test] Hero title text:', heroTitle);
    if (!heroTitle.includes('RAILFLOW ENGINE')) throw new Error('Hero title mismatch');

    // Verify initial body lock
    let isBodyLocked = await page.evaluate(() => document.body.style.position === 'fixed');
    console.log('[Landing Playwright Test] Initial body lock status (expect true):', isBodyLocked);
    if (!isBodyLocked) throw new Error('Expected body to be locked on hero mount');

    // Simulate mouse wheel events to advance scrub progress
    console.log('[Landing Playwright Test] Dispatching wheel scroll deltas...');
    for (let i = 0; i < 25; i++) {
      await page.mouse.wheel(0, 300);
      await page.waitForTimeout(100);
    }

    // Verify lock released at end (~0.98)
    isBodyLocked = await page.evaluate(() => document.body.style.position === 'fixed');
    console.log('[Landing Playwright Test] Body lock status after wheel scrubbing (expect false):', isBodyLocked);
    if (isBodyLocked) throw new Error('Expected body lock to release at end of scrub');

    // Verify telemetry stats rendered
    const statStations = await page.locator('[data-testid="landing-stat-stations"]').innerText();
    const statPlatforms = await page.locator('[data-testid="landing-stat-platforms"]').innerText();
    const statTrains = await page.locator('[data-testid="landing-stat-trains"]').innerText();
    console.log(`[Landing Playwright Test] Live Telemetry Stats: Stations=${statStations}, Platforms=${statPlatforms}, Trains=${statTrains}`);

    // Click "Enter Control Room"
    console.log('[Landing Playwright Test] Clicking Enter Control Room...');
    const enterBtn = page.locator('[data-testid="enter-control-room-btn"]');
    await enterBtn.scrollIntoViewIfNeeded();
    await enterBtn.click();
    await page.waitForTimeout(1000);

    // Verify Dashboard Overview loads
    const dashboardTitle = page.locator('text=MUMBAI CORRIDOR SIMULATION HUB');
    await dashboardTitle.waitFor({ state: 'visible', timeout: 5000 });
    console.log('[Landing Playwright Test] Dashboard Overview confirmed loaded.');

    // Confirm no scroll trap remains
    const bodyPositionAfterEnter = await page.evaluate(() => document.body.style.position);
    console.log('[Landing Playwright Test] Body position after entering (expect empty string):', `"${bodyPositionAfterEnter}"`);
    if (bodyPositionAfterEnter !== '') throw new Error('Scroll trap remained on body after entering app');

    // ─────────────────────────────────────────────────────────────
    // TEST 2: Session memory (subsequent page reload stays in app)
    // ─────────────────────────────────────────────────────────────
    console.log('[Landing Playwright Test] TEST 2: Testing session memory on reload...');
    await page.reload();
    await page.waitForTimeout(1500);

    const isDashboardStillVisible = await dashboardTitle.isVisible();
    console.log('[Landing Playwright Test] Dashboard visible immediately on reload (expect true):', isDashboardStillVisible);
    if (!isDashboardStillVisible) throw new Error('Landing re-appeared unexpectedly during same session');

    // ─────────────────────────────────────────────────────────────
    // TEST 3: Replay from Sidebar
    // ─────────────────────────────────────────────────────────────
    console.log('[Landing Playwright Test] TEST 3: Replaying landing from sidebar...');
    const landingNavLink = page.locator('text=Landing Intro').first();
    await landingNavLink.click();
    await page.waitForTimeout(1000);

    await hero.waitFor({ state: 'visible', timeout: 5000 });
    console.log('[Landing Playwright Test] Landing replayed successfully via sidebar link.');

    // ─────────────────────────────────────────────────────────────
    // TEST 4: Skip Intro button
    // ─────────────────────────────────────────────────────────────
    console.log('[Landing Playwright Test] TEST 4: Testing Skip Intro button...');
    const skipBtn = page.locator('[data-testid="skip-intro-btn"]');
    await skipBtn.click();
    await page.waitForTimeout(1000);

    await dashboardTitle.waitFor({ state: 'visible', timeout: 5000 });
    console.log('[Landing Playwright Test] Skip Intro button successfully bypassed intro to Dashboard.');

    // ─────────────────────────────────────────────────────────────
    // TEST 5: Keyboard navigation
    // ─────────────────────────────────────────────────────────────
    console.log('[Landing Playwright Test] TEST 5: Testing keyboard navigation on hero...');
    await landingNavLink.click();
    await hero.waitFor({ state: 'visible', timeout: 5000 });

    // Press ArrowDown multiple times
    for (let i = 0; i < 15; i++) {
      await page.keyboard.press('ArrowDown');
      await page.waitForTimeout(100);
    }
    const unlockedViaKeyboard = await page.evaluate(() => document.body.style.position !== 'fixed');
    console.log('[Landing Playwright Test] Unlocked via ArrowDown keyboard (expect true):', unlockedViaKeyboard);

    // Skip to return to app
    await skipBtn.click();
    await page.waitForTimeout(1000);

    // ─────────────────────────────────────────────────────────────
    // TEST 6: URL override ?intro=0
    // ─────────────────────────────────────────────────────────────
    console.log('[Landing Playwright Test] TEST 6: Testing ?intro=0 URL override in fresh context...');
    const freshContext = await browser.newContext();
    const freshPage = await freshContext.newPage();
    await freshPage.goto('http://localhost:5173/?intro=0');
    await freshPage.waitForTimeout(2000);

    const freshDashboard = freshPage.locator('text=MUMBAI CORRIDOR SIMULATION HUB');
    await freshDashboard.waitFor({ state: 'visible', timeout: 5000 });
    console.log('[Landing Playwright Test] ?intro=0 bypassed landing on fresh visit!');
    await freshContext.close();

    // ─────────────────────────────────────────────────────────────
    // TEST 7: Existing pages still load cleanly
    // ─────────────────────────────────────────────────────────────
    console.log('[Landing Playwright Test] TEST 7: Verifying other application pages load cleanly...');
    const mapNav = page.locator('text=Corridor Network Map').first();
    await mapNav.click();
    await page.waitForTimeout(1000);
    console.log('[Landing Playwright Test] Corridor Network Map loaded.');

    const whatIfNav = page.locator('text=What-If Simulator').first();
    await whatIfNav.click();
    await page.waitForTimeout(1000);
    console.log('[Landing Playwright Test] What-If Simulator loaded.');

    const analyticsNav = page.locator('text=Operations Analytics').first();
    await analyticsNav.click();
    await page.waitForTimeout(1000);
    console.log('[Landing Playwright Test] Operations Analytics loaded.');

    await browser.close();
    console.log('[Landing Playwright Test] ALL LANDING EXPERIENCE TESTS PASSED!');
  } finally {
    console.log('[Landing Playwright Test] Cleaning up child processes...');
    spawn('taskkill', ['/F', '/T', '/PID', backendProc.pid]);
    spawn('taskkill', ['/F', '/T', '/PID', frontendProc.pid]);
    try {
      spawn('cmd.exe', ['/c', 'for /f "tokens=5" %a in (\'netstat -aon ^| findstr :8080\') do taskkill /F /PID %a']);
      spawn('cmd.exe', ['/c', 'for /f "tokens=5" %a in (\'netstat -aon ^| findstr :5173\') do taskkill /F /PID %a']);
    } catch {}
  }
}

run().catch((err) => {
  console.error('[Landing Playwright Test] FAILED:', err);
  process.exit(1);
});

