const { spawn } = require('child_process');
const { chromium } = require('./frontend/node_modules/playwright');
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
    } catch {
      // wait
    }
    await sleep(1000);
  }
  throw new Error(`Timeout waiting for ${url}`);
}

async function run() {
  console.log('[Playwright Test] Starting backend and frontend...');
  
  // Start backend
  const backendProc = spawn('cmd.exe', ['/c', 'C:\\Users\\rindh\\apache-maven-3.9.9\\bin\\mvn.cmd', '-f', 'backend/pom.xml', 'spring-boot:run'], {
    detached: true,
    stdio: 'ignore'
  });

  // Start frontend
  const frontendProc = spawn('cmd.exe', ['/c', 'npm', '--prefix', 'frontend', 'run', 'dev'], {
    detached: true,
    stdio: 'ignore'
  });

  try {
    console.log('[Playwright Test] Waiting for backend (8080) and frontend (5173)...');
    await waitForServer('http://localhost:8080/api/stations');
    console.log('[Playwright Test] Backend is ready.');
    await waitForServer('http://localhost:5173');
    console.log('[Playwright Test] Frontend is ready.');

    // Launch Playwright browser
    console.log('[Playwright Test] Launching Chromium (Google Chrome)...');
    const browser = await chromium.launch({
      executablePath: 'C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe',
      headless: true
    });
    const context = await browser.newContext();
    const page = await context.newPage();

    console.log('[Playwright Test] Navigating to http://localhost:5173...');
    await page.goto('http://localhost:5173');
    await page.waitForTimeout(3000);

    // Switch to What-If Operations Impact Studio tab in navigation
    console.log('[Playwright Test] Navigating to What-If Simulator...');
    const whatIfNav = page.locator('text=What-If Simulator').first();
    if (await whatIfNav.isVisible()) {
      await whatIfNav.click();
    } else {
      const altNav = page.locator('button:has-text("What-If")').first();
      await altNav.click();
    }
    await page.waitForTimeout(1000);

    // 1. Check TrainCombobox trigger
    console.log('[Playwright Test] Verifying combobox trigger...');
    const trigger = page.locator('[data-testid="train-combobox-trigger"]');
    await trigger.waitFor({ state: 'visible', timeout: 10000 });
    const initialText = await trigger.innerText();
    console.log('[Playwright Test] Initial combobox trigger text:', initialText);

    // Click trigger to open dropdown
    await trigger.click();
    await page.waitForTimeout(500);

    const dropdown = page.locator('[data-testid="train-combobox-dropdown"]');
    await dropdown.waitFor({ state: 'visible', timeout: 5000 });

    // 2. Test search in Combobox
    console.log('[Playwright Test] Searching for SIM-LOC-901...');
    const searchInput = page.locator('[data-testid="train-combobox-search"]');
    await searchInput.fill('SIM-LOC-901');
    await page.waitForTimeout(500);

    const option = page.locator('[data-testid="train-combobox-option"]').first();
    await option.waitFor({ state: 'visible', timeout: 5000 });
    const optionText = await option.innerText();
    console.log('[Playwright Test] Found option label:', optionText);

    // Verify unambiguous label requirements:
    // "SIM-LOC-901 · Fast Local · UP · now between VR and NSP · next BVI · platform BVI-P5" or "no platform yet"
    if (!optionText.includes('SIM-LOC-901')) throw new Error('Option missing train number');
    if (!optionText.includes('Local') && !optionText.includes('Express')) throw new Error('Option missing category');
    if (!optionText.includes('UP') && !optionText.includes('DOWN')) throw new Error('Option missing direction');
    if (!optionText.includes('next')) throw new Error('Option missing next stop');
    if (!optionText.includes('platform') && !optionText.includes('no platform yet')) throw new Error('Option missing platform');

    console.log('[Playwright Test] Option label format is unambiguous and valid!');

    // Click to select the train
    await option.click();
    await page.waitForTimeout(500);

    // 3. Run What-If Scenario (Isolated Copy)
    console.log('[Playwright Test] Running What-If Delay Scenario on isolated copy...');
    const evaluateBtn = page.locator('[data-testid="btn-evaluate-scenario"]');
    await evaluateBtn.click();

    // Verify isolated preview appears
    const previewBadge = page.locator('[data-testid="preview-mode-badge"]');
    await previewBadge.waitFor({ state: 'visible', timeout: 10000 });
    console.log('[Playwright Test] Isolated preview badge visible.');

    const kpiAffected = await page.locator('[data-testid="kpi-affected"]').innerText();
    console.log('[Playwright Test] Affected trains in isolated preview:', kpiAffected);

    const diffRows = page.locator('[data-testid="what-if-diff-row"]');
    const rowCount = await diffRows.count();
    console.log('[Playwright Test] Before vs After diff rows displayed:', rowCount);
    if (rowCount === 0) throw new Error('Expected at least 1 diff row in isolated preview');

    // 4. Verify live simulation trains are UNCHANGED
    console.log('[Playwright Test] Checking live GET /api/trains to confirm live sim is untouched...');
    const trainsRes = await fetch('http://localhost:8080/api/trains');
    const trainsJson = await trainsRes.json();
    const liveTargetTrain = trainsJson.data.find(t => t.trainNumber === 'SIM-LOC-901');
    console.log('[Playwright Test] Live target train delay before apply:', liveTargetTrain.delayMinutes);
    if (liveTargetTrain.delayMinutes !== 0) {
      throw new Error(`Expected live delay to be 0, but was ${liveTargetTrain.delayMinutes}`);
    }
    console.log('[Playwright Test] CONFIRMED: Live simulation data is completely unchanged during isolated preview!');

    // 5. Click "Apply to Live Simulation"
    console.log('[Playwright Test] Applying scenario to live simulation...');
    const applyBtn = page.locator('[data-testid="btn-apply-to-live"]');
    await applyBtn.click();

    const successBanner = page.locator('[data-testid="what-if-success-banner"]');
    await successBanner.waitFor({ state: 'visible', timeout: 10000 });
    console.log('[Playwright Test] Live apply success banner displayed:', await successBanner.innerText());

    // 6. Verify live simulation now reflects the delay in GET /api/trains
    console.log('[Playwright Test] Checking live GET /api/trains after applying...');
    const trainsResAfter = await fetch('http://localhost:8080/api/trains');
    const trainsJsonAfter = await trainsResAfter.json();
    const liveTargetTrainAfter = trainsJsonAfter.data.find(t => t.trainNumber === 'SIM-LOC-901');
    console.log('[Playwright Test] Live target train delay after apply:', liveTargetTrainAfter.delayMinutes);
    if (liveTargetTrainAfter.delayMinutes !== 20) {
      throw new Error(`Expected live delay to be 20, but was ${liveTargetTrainAfter.delayMinutes}`);
    }
    console.log('[Playwright Test] CONFIRMED: Live simulation updated correctly to 20m delay!');

    await browser.close();
    console.log('[Playwright Test] ALL PLAYWRIGHT CHECKS PASSED SUCCESSFULLY!');
  } finally {
    // Kill child processes
    console.log('[Playwright Test] Cleaning up child processes...');
    spawn('taskkill', ['/F', '/T', '/PID', backendProc.pid]);
    spawn('taskkill', ['/F', '/T', '/PID', frontendProc.pid]);
    // Also kill java / node if lingering on 8080 / 5173
    try {
      const p8080 = spawn('cmd.exe', ['/c', 'for /f "tokens=5" %a in (\'netstat -aon ^| findstr :8080\') do taskkill /F /PID %a']);
      const p5173 = spawn('cmd.exe', ['/c', 'for /f "tokens=5" %a in (\'netstat -aon ^| findstr :5173\') do taskkill /F /PID %a']);
    } catch {}
  }
}

run().catch((err) => {
  console.error('[Playwright Test] FAILED:', err);
  process.exit(1);
});

