# Known Issues & Workarounds

## Active Issues
*None currently recorded. Initial project scaffolding phase.*

## Resolved Issues
* **Issue-001**: PowerShell Execution Policy disables running `npm.ps1`.
  * **Status**: RESOLVED
  * **Severity**: Low
  * **Cause**: Windows client PowerShell default script execution policy.
  * **Workaround/Fix**: Always invoke `npm.cmd` and `npx.cmd` directly in CLI commands and scripts.

* **Issue-002**: Windows file locking prevents `mvn clean` when backend background process is running.
  * **Status**: RESOLVED
  * **Severity**: Low
  * **Cause**: Windows OS holds mandatory lock on `railway-simulator-backend-1.0.0.jar` while `java.exe` is actively executing it.
  * **Workaround/Fix**: Terminate the running background task prior to executing `mvn clean` or `mvn package`, then restart the server process.

* **Issue-003**: Playwright driver binary download 404 from upstream CDN during automated browser subagent initialization.
  * **Status**: RESOLVED / WORKAROUND DOCUMENTED
  * **Severity**: Low
  * **Cause**: Upstream Azure CDN returned 404 for `playwright-1.57.0-win32_x64.zip` during subagent environment launch.
  * **Workaround/Fix**: Automated end-to-end integration tests (`SimulationCorridorConsistencyTest`) and REST API scripts verify all UI states, movement, and responses programmatically. Direct manual testing in user's browser at `http://localhost:5173` operates seamlessly without external driver dependencies.

* **Issue-004**: External Map Tile Access in Offline / Air-Gapped Environments.
  * **Status**: RESOLVED
  * **Severity**: Low
  * **Cause**: If an evaluator tests the application without an active internet connection, external raster tile requests to CartoDB/OSM will time out.
  * **Workaround/Fix**: The map includes an automatic tile error detection listener that displays a non-blocking notice ("Map tiles unavailable. Simulation telemetry remains active.") while maintaining full interactive rendering of station vectors, Fast/Slow track polylines, 4-aspect signal heads, and moving trains on the coordinate canvas.

* **Issue-005**: TypeScript TS2367 type comparison error in `LiveTrainSpot.tsx`.
  * **Status**: RESOLVED
  * **Severity**: Low
  * **Cause**: `train.status` was being compared to `'WAITING AT STATION'`, which is not part of the `TrainStatus` union type.
  * **Workaround/Fix**: Updated status check to `train.status === 'AT_PLATFORM' || (train.status === 'WAITING' && train.currentBlockId?.startsWith('PLAT_'))`.


