import fs from 'node:fs';
import path from 'node:path';
import { parse as parseYaml } from 'yaml';
import { z } from 'zod';
import { paths } from './config.js';

/**
 * The curated knowledge layer: the part of this dataset that nobody publishes
 * machine-readably, so we own it.
 *
 * YAML rather than JSON on purpose. Every row here is a claim about a game that someone
 * (me) has to justify, and YAML lets the citation sit as a comment next to the claim it
 * justifies. A one-row correction is a one-line diff. Both properties are lost the
 * moment this becomes a binary or a spreadsheet.
 *
 * Note how small this layer is compared with what docs/00-big-picture.md assumed:
 * upstream already answers HOME transfer legality (storableIn / transferOnlyIn) and
 * per-game obtainability (obtainableIn), so neither is curated here.
 */

const gameSchema = z.object({
  id: z.string(),
  name: z.string(),
  sortOrder: z.number().int(),
});

const shinyLockSchema = z.object({
  variantId: z.string(),
  gameId: z.string(),
  reason: z.string(),
  source: z.string().url().optional(),
});

const encounterMethodSchema = z.object({
  id: z.string(),
  name: z.string(),
  description: z.string(),
});

const encounterSchema = z.object({
  variantId: z.string(),
  methodId: z.string(),
  location: z.string().optional(),
  prerequisite: z.string().optional(),
  notes: z.string().optional(),
  source: z.string().url().optional(),
});

const oddsModifierSchema = z
  .object({
    gameId: z.string(),
    methodId: z.string(),
    id: z.string(),
    label: z.string(),
    rollsAdded: z.number().int().optional(),
    denominator: z.number().int().optional(),
    notes: z.string().optional(),
  })
  .refine((m) => m.rollsAdded !== undefined || m.denominator !== undefined, {
    message: 'an odds modifier must set either rollsAdded or denominator',
  });

const spriteOverrideSchema = z.object({
  variantId: z.string(),
  file: z.string().regex(/^[a-z0-9/-]+\.png$/, 'a path under PokeAPI other/home/, ending .png'),
});

const sharedSpriteSchema = z.object({
  reason: z.string(),
  variants: z.array(z.string()).min(2),
});

export const curatedSchemas = {
  games: z.object({ games: z.array(gameSchema) }),
  shinyLocks: z.object({ locks: z.array(shinyLockSchema) }),
  encounterMethods: z.object({ methods: z.array(encounterMethodSchema) }),
  encounters: z.object({ gameId: z.string(), encounters: z.array(encounterSchema) }),
  oddsModifiers: z.object({ modifiers: z.array(oddsModifierSchema) }),
  sprites: z
    .object({ overrides: z.array(spriteOverrideSchema), shared: z.array(sharedSpriteSchema) })
    .superRefine((s, ctx) => {
      for (const [kind, ids] of [
        ['overrides', s.overrides.map((o) => o.variantId)],
        ['shared', s.shared.flatMap((g) => g.variants)],
      ] as const) {
        const once = new Set<string>();
        for (const id of ids) {
          if (once.has(id)) {
            ctx.addIssue({ code: 'custom', message: id + ' appears twice in ' + kind });
          }
          once.add(id);
        }
      }
    }),
};

export type CuratedGame = z.infer<typeof gameSchema>;
export type CuratedShinyLock = z.infer<typeof shinyLockSchema>;
export type CuratedEncounterMethod = z.infer<typeof encounterMethodSchema>;
export type CuratedEncounter = z.infer<typeof encounterSchema> & { gameId: string };
export type CuratedOddsModifier = z.infer<typeof oddsModifierSchema>;
export type CuratedSprites = z.infer<typeof curatedSchemas.sprites>;

export interface CuratedLayer {
  games: CuratedGame[];
  shinyLocks: CuratedShinyLock[];
  encounterMethods: CuratedEncounterMethod[];
  encounters: CuratedEncounter[];
  oddsModifiers: CuratedOddsModifier[];
  sprites: CuratedSprites;
}

function readYaml<T>(file: string, schema: z.ZodType<T>): T {
  const full = path.join(paths.curated, file);
  const raw = parseYaml(fs.readFileSync(full, 'utf8'));
  const result = schema.safeParse(raw);
  if (!result.success) {
    const issues = result.error.issues
      .map((i) => `  ${file}#/${i.path.join('/')}: ${i.message}`)
      .join('\n');
    throw new Error(`Curated layer failed validation:\n${issues}`);
  }
  return result.data;
}

export function loadCuratedLayer(): CuratedLayer {
  const games = readYaml('games.yaml', curatedSchemas.games).games;
  const shinyLocks = readYaml('shiny-locks.yaml', curatedSchemas.shinyLocks).locks;
  const encounterMethods = readYaml(
    'encounter-methods.yaml',
    curatedSchemas.encounterMethods,
  ).methods;
  const oddsModifiers = readYaml('odds-modifiers.yaml', curatedSchemas.oddsModifiers).modifiers;

  const sprites = readYaml('sprites.yaml', curatedSchemas.sprites);

  const encounterDir = path.join(paths.curated, 'encounters');
  const encounters: CuratedEncounter[] = [];
  if (fs.existsSync(encounterDir)) {
    for (const file of fs.readdirSync(encounterDir).sort()) {
      if (!file.endsWith('.yaml')) continue;
      const parsed = readYaml(
        path.join('encounters', file),
        curatedSchemas.encounters,
      );
      for (const e of parsed.encounters) encounters.push({ ...e, gameId: parsed.gameId });
    }
  }

  return { games, shinyLocks, encounterMethods, encounters, oddsModifiers, sprites };
}
