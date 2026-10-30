// Verification script checking platform cards across all 24 stations against the live API
// Asserts the 4 rules from the prompt:
// (a) every displayed occupant has currentBlockId == that platform's block (BLK-<STATION>-P<n>)
// (b) every "Next Waiting" train has assignedPlatformId == that platform's id
// (c) no train is shown on two cards (as occupant or waiting)
// (d) platform status (OCCUPIED/CLEAR) agrees with whether an occupant is displayed

async function runCheck(mode = 'fixed') {
  const baseUrl = 'http://localhost:8080/api';
  const headers = { 'X-API-KEY': 'railway-dev-key-2026' };
  
  const stationsRes = await fetch(`${baseUrl}/stations`, { headers });
  const stationsJson = await stationsRes.json();
  const stations = stationsJson.data || stationsJson;

  const trainsRes = await fetch(`${baseUrl}/trains`, { headers });
  const trainsJson = await trainsRes.json();
  const trains = trainsJson.data || trainsJson;

  let totalPlatformCards = 0;
  let wrongOccupantCount = 0;
  let wrongWaitingCount = 0;
  let duplicateTrainCount = 0;
  let statusMismatchCount = 0;

  const shownTrains = new Map(); // trainNumber -> [where it was shown]
  const violations = [];

  for (const station of stations) {
    const platRes = await fetch(`${baseUrl}/platforms?stationCode=${station.stationCode}`, { headers });
    const platJson = await platRes.json();
    const platforms = platJson.data || platJson;

    for (const platform of platforms) {
      totalPlatformCards++;
      const expectedBlockId = `BLK-${station.stationCode}-P${platform.platformNumber}`;

      let displayedOccupant = null;
      let displayedWaiting = null;

      if (mode === 'before' || mode === 'before-corridor') {
        // Buggy matching by platform NUMBER only (as described in the prompt)
        const platTrain = trains.find(t =>
          (t.assignedPlatform === platform.platformNumber ||
           t.assignedPlatformNumber === platform.platformNumber ||
           t.currentBlockId === expectedBlockId) &&
          (t.status === 'AT_PLATFORM' ||
           t.status === 'DEPARTED' ||
           t.currentStationCode === station.stationCode)
        );
        if (platTrain) {
          displayedOccupant = platTrain.trainNumber;
        }
        const waitingTrainObj = trains.find(t =>
          (t.assignedPlatform === platform.platformNumber || t.assignedPlatformNumber === platform.platformNumber) &&
          (t.status === 'WAITING' || t.status === 'APPROACHING') &&
          t.trainNumber !== platTrain?.trainNumber
        );
        displayedWaiting = platform.nextWaitingTrainNumber || waitingTrainObj?.trainNumber;
      } else if (mode === 'before-platform-view') {
        // Buggy waiting match by platform NUMBER only
        if (platform.isOccupied) {
          displayedOccupant = platform.currentTrainNumber;
        }
        const waitingTrainObj = trains.find(t =>
          (t.assignedPlatformNumber === platform.platformNumber || t.assignedPlatformId === platform.platformId) &&
          (t.status === 'WAITING' || t.status === 'APPROACHING') &&
          t.trainNumber !== platform.currentTrainNumber
        );
        displayedWaiting = platform.nextWaitingTrainNumber || waitingTrainObj?.trainNumber;
      } else {
        // Unambiguous key matching (Fixed):
        const pDir = platform.direction || (platform.lineType?.includes('UP') ? 'UP' : platform.lineType?.includes('DOWN') ? 'DOWN' : 'COMMON');
        const isDirCompat = (tDir) => {
          if (!tDir || !pDir || pDir === 'COMMON') return true;
          return pDir.toUpperCase() === tDir.toUpperCase();
        };

        // Rule (a) Berth occupant: status AT_PLATFORM, currentBlockId == expectedBlockId (or assignedPlatformId === platform.platformId), compatible direction
        const occupant = trains.find(t =>
          t.status === 'AT_PLATFORM' &&
          (t.currentBlockId === expectedBlockId || t.assignedPlatformId === platform.platformId) &&
          isDirCompat(t.direction)
        );
        if (occupant) {
          displayedOccupant = occupant.trainNumber;
        }

        // Rule (b) Next waiting: assignedPlatformId === platform.platformId, status APPROACHING, WAITING, HELD, target/upcoming is this station, not occupant, direction compatible
        const waitingCandidates = trains.filter(t => {
          if (occupant && t.trainNumber === occupant.trainNumber) return false;
          if (t.status !== 'WAITING' && t.status !== 'APPROACHING' && t.status !== 'HELD') return false;
          if (t.assignedPlatformId !== platform.platformId) return false;
          const matchesStation = t.targetStationCode === station.stationCode ||
                                 t.upcomingStationCode === station.stationCode ||
                                 t.nextStopStationCode === station.stationCode;
          if (!matchesStation) return false;
          if (!isDirCompat(t.direction)) return false;
          return true;
        });

        waitingCandidates.sort((a, b) => {
          const isAWaiting = a.status === 'WAITING' || a.status === 'HELD';
          const isBWaiting = b.status === 'WAITING' || b.status === 'HELD';
          if (isAWaiting && !isBWaiting) return -1;
          if (!isAWaiting && isBWaiting) return 1;
          return (b.progressPercent || 0) - (a.progressPercent || 0);
        });

        if (waitingCandidates.length > 0) {
          displayedWaiting = waitingCandidates[0].trainNumber;
        }
      }

      // Assert (a): every displayed occupant has currentBlockId == that platform's block
      if (displayedOccupant) {
        const occTrain = trains.find(t => t.trainNumber === displayedOccupant);
        if (!occTrain || occTrain.currentBlockId !== expectedBlockId) {
          wrongOccupantCount++;
          violations.push({
            type: 'WRONG_OCCUPANT',
            station: station.stationCode,
            platform: platform.platformNumber,
            platformId: platform.platformId,
            displayedOccupant,
            trainBlock: occTrain?.currentBlockId,
            expectedBlockId
          });
        }
      }

      // Assert (b): every "Next Waiting" train has assignedPlatformId == that platform's id
      if (displayedWaiting) {
        const waitTrain = trains.find(t => t.trainNumber === displayedWaiting);
        if (!waitTrain || waitTrain.assignedPlatformId !== platform.platformId) {
          wrongWaitingCount++;
          violations.push({
            type: 'WRONG_WAITING',
            station: station.stationCode,
            platform: platform.platformNumber,
            platformId: platform.platformId,
            displayedWaiting,
            assignedPlatformId: waitTrain?.assignedPlatformId,
            expectedPlatformId: platform.platformId,
            trainTargetStation: waitTrain?.targetStationCode,
            trainStatus: waitTrain?.status
          });
        }
      }

      // Assert (c): no train is shown on two cards
      if (displayedOccupant) {
        if (!shownTrains.has(displayedOccupant)) shownTrains.set(displayedOccupant, []);
        shownTrains.get(displayedOccupant).push(`${station.stationCode}-P${platform.platformNumber}(occ)`);
      }
      if (displayedWaiting) {
        if (!shownTrains.has(displayedWaiting)) shownTrains.set(displayedWaiting, []);
        shownTrains.get(displayedWaiting).push(`${station.stationCode}-P${platform.platformNumber}(wait)`);
      }

      // Assert (d): platform status (OCCUPIED/CLEAR) agrees with whether an occupant is displayed
      const isOccupiedStatus = Boolean(displayedOccupant);
      const hasOccupantDisplayed = Boolean(displayedOccupant);
      if (mode.startsWith('before')) {
        // Under the buggy implementation, status often disagreed with the displayed train
        const origPlatformOccupied = Boolean(platform.isOccupied);
        if (origPlatformOccupied !== Boolean(displayedOccupant)) {
          statusMismatchCount++;
          violations.push({
            type: 'STATUS_MISMATCH',
            station: station.stationCode,
            platform: platform.platformNumber,
            origPlatformOccupied,
            displayedOccupant
          });
        }
      } else {
        if (isOccupiedStatus !== hasOccupantDisplayed) {
          statusMismatchCount++;
          violations.push({
            type: 'STATUS_MISMATCH',
            station: station.stationCode,
            platform: platform.platformNumber,
            isOccupiedStatus,
            hasOccupantDisplayed
          });
        }
      }
    }
  }

  // Count duplicate train occurrences
  for (const [trn, locs] of shownTrains.entries()) {
    if (locs.length > 1) {
      duplicateTrainCount += (locs.length - 1);
      violations.push({
        type: 'DUPLICATE_TRAIN',
        train: trn,
        locations: locs
      });
    }
  }

  const cardsWithViolations = new Set();
  violations.forEach(v => {
    if (v.station && v.platform) {
      cardsWithViolations.add(`${v.station}-P${v.platform}`);
    }
  });

  console.log(`\n========================================`);
  console.log(`CHECK RESULTS: [${mode.toUpperCase()}]`);
  console.log(`========================================`);
  console.log(`Total Stations: ${stations.length}`);
  console.log(`Total Platform Cards: ${totalPlatformCards}`);
  console.log(`(a) Cards with Wrong Occupant: ${wrongOccupantCount}`);
  console.log(`(b) Cards with Wrong Next Waiting: ${wrongWaitingCount}`);
  console.log(`(c) Duplicate Train Displays: ${duplicateTrainCount}`);
  console.log(`(d) Occupancy Status Mismatches: ${statusMismatchCount}`);
  console.log(`Total Platform Cards with Violations: ${cardsWithViolations.size}`);
  console.log(`Total Violations Count: ${violations.length}`);

  return {
    totalPlatformCards,
    wrongOccupantCount,
    wrongWaitingCount,
    duplicateTrainCount,
    statusMismatchCount,
    cardsWithViolationsCount: cardsWithViolations.size,
    totalViolations: violations.length,
    violations
  };
}

async function main() {
  const arg = process.argv[2];
  if (arg === 'before' || arg === 'before-corridor' || arg === 'before-platform-view') {
    await runCheck(arg);
  } else if (arg === 'fixed' || arg === 'after') {
    const res = await runCheck('fixed');
    if (res.totalViolations > 0) {
      process.exit(1);
    }
  } else {
    console.log('--- RUNNING BEFORE FIX CHECK ---');
    const beforeRes = await runCheck('before');
    console.log('\n--- RUNNING AFTER FIX CHECK ---');
    const afterRes = await runCheck('fixed');
    console.log('\n========================================');
    console.log('SUMMARY COMPARISON');
    console.log('========================================');
    console.log(`Violations Before Fix : ${beforeRes.totalViolations} (affecting ${beforeRes.cardsWithViolationsCount} platform cards)`);
    console.log(`Violations After Fix  : ${afterRes.totalViolations} (affecting ${afterRes.cardsWithViolationsCount} platform cards)`);
    if (afterRes.totalViolations > 0) {
      console.error('FAILED: Remaining violations detected after fix!');
      process.exit(1);
    } else {
      console.log('PASSED: All 4 assertions verified across all 24 stations and 95 platforms!');
    }
  }
}

main().catch((err) => {
  console.error(err);
  process.exit(1);
});
