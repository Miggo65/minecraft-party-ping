import { readFile } from 'node:fs/promises';
import path from 'node:path';

const STATS_FILE = process.env.STATS_FILE || path.join(process.cwd(), 'stats', 'usage-stats.json');

async function main() {
  let parsed;
  try {
    const raw = await readFile(STATS_FILE, 'utf8');
    parsed = JSON.parse(raw);
  } catch {
    parsed = {
      currentActiveUsers: 0,
      peakConcurrentUsers: 0,
      uniqueUserCount: 0,
      updatedAt: null
    };
  }

  const current = Number.isFinite(parsed?.currentActiveUsers) ? Math.max(0, Math.trunc(parsed.currentActiveUsers)) : 0;
  const peak = Number.isFinite(parsed?.peakConcurrentUsers) ? Math.max(0, Math.trunc(parsed.peakConcurrentUsers)) : 0;
  const unique = Number.isFinite(parsed?.uniqueUserCount)
    ? Math.max(0, Math.trunc(parsed.uniqueUserCount))
    : Array.isArray(parsed?.uniqueUsers)
      ? parsed.uniqueUsers.length
      : 0;

  console.log('Minecraft Ping Relay - Nutzung');
  console.log('--------------------------------');
  console.log(`Aktuell verbunden: ${current}`);
  console.log(`Peak gleichzeitig: ${peak}`);
  console.log(`Verschiedene Nutzer insgesamt: ${unique}`);
  if (parsed?.updatedAt) {
    console.log(`Letztes Update: ${parsed.updatedAt}`);
  } else {
    console.log(`Stats-Datei noch nicht vorhanden: ${STATS_FILE}`);
  }
}

main();
