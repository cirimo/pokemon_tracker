import { buildReference } from './build-reference.js';
import { buildSprites } from './build-sprites.js';
import { coverageOf, printCoverage } from './coverage.js';
import { reportAndExit, validate } from './validate.js';

/**
 * CLI. Runs on demand, never from a Gradle build -- the outputs are checked in.
 *
 *   npm run build:dataset          build reference.db, then validate it
 *   npm run build:sprites          download and encode the shiny set (needs network)
 *   npm run validate               validate the checked-in asset
 *   npm run coverage -- la         how much of one game is curated; --list names the rest
 *   npm run build:sprites -- --limit 8    a small placeholder set, for the skeleton
 */
async function main(): Promise<void> {
  const [command, ...rest] = process.argv.slice(2);
  const limitArg = rest.indexOf('--limit');
  const limit = limitArg >= 0 ? Number(rest[limitArg + 1]) : undefined;

  switch (command) {
    case 'build': {
      buildReference();
      reportAndExit(validate({ requireSprites: false }));
      return;
    }
    case 'sprites': {
      const result = await buildSprites({ limit });
      process.stdout.write(
        'sprites: ' + result.written + ' written, ' + result.skipped + ' already present, ' +
          (result.totalBytes / 1024 / 1024).toFixed(1) + ' MB total\n',
      );
      return;
    }
    case 'validate': {
      reportAndExit(validate({ requireSprites: rest.includes('--sprites') }));
      return;
    }
    case 'coverage': {
      const gameId = rest.find((a) => !a.startsWith('--'));
      if (!gameId) throw new Error('usage: npm run coverage -- <gameId> [--list]');
      printCoverage(coverageOf(gameId), { list: rest.includes('--list') });
      return;
    }
    default: {
      process.stderr.write('usage: tsx src/index.ts <build|sprites|validate|coverage>\n');
      process.exitCode = 2;
    }
  }
}

main().catch((error: unknown) => {
  process.stderr.write(String(error instanceof Error ? error.stack : error) + '\n');
  process.exitCode = 1;
});
