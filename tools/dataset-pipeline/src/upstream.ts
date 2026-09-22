import { execFileSync } from 'node:child_process';
import fs from 'node:fs';
import path from 'node:path';
import { z } from 'zod';
import { paths, UPSTREAM } from './config.js';

/**
 * The pinned PokePC dataset checkout.
 *
 * We shallow-clone the tag rather than depend on the npm package so the pipeline has
 * the raw `data/` tree, which is what we actually read, and so the exact provenance is
 * a git sha we can print in the manifest.
 */
export function ensureUpstream(): string {
  const dir = paths.upstream;
  const stamp = path.join(dir, '.tag');
  if (fs.existsSync(stamp) && fs.readFileSync(stamp, 'utf8').trim() === UPSTREAM.tag) {
    return dir;
  }
  fs.rmSync(dir, { recursive: true, force: true });
  fs.mkdirSync(path.dirname(dir), { recursive: true });
  execFileSync(
    'git',
    ['clone', '--depth', '1', '--branch', UPSTREAM.tag, '--quiet', UPSTREAM.repo, dir],
    { stdio: 'inherit' },
  );
  fs.writeFileSync(stamp, `${UPSTREAM.tag}\n`);
  return dir;
}

export function upstreamSha(): string {
  return execFileSync('git', ['-C', paths.upstream, 'rev-parse', 'HEAD'], {
    encoding: 'utf8',
  }).trim();
}

/**
 * Our reading of the upstream shape.
 *
 * Deliberately permissive via .passthrough(): we assert only the fields we consume, so
 * an upstream addition does not break the build but an upstream REMOVAL does. That is
 * the correct asymmetry for a source we do not control.
 */
export const upstreamPokemonSchema = z
  .object({
    id: z.string(),
    nid: z.string(),
    dexNum: z.number().int(),
    formId: z.string().nullish(),
    type1: z.string(),
    type2: z.string().nullish(),
    region: z.string().nullish(),
    gen: z.number().int(),
    isDefault: z.boolean(),
    isForm: z.boolean(),
    isCosmeticForm: z.boolean(),
    isFemaleForm: z.boolean(),
    isRegional: z.boolean(),
    isBattleOnlyForm: z.boolean(),
    isLegendary: z.boolean(),
    isMythical: z.boolean(),
    isBaby: z.boolean(),
    isUltraBeast: z.boolean(),
    isParadox: z.boolean(),
    shinyReleased: z.boolean(),
    baseSpecies: z.string().nullish(),
    evolvesFrom: z.string().nullish(),
    evoFromLevel: z.number().nullish(),
    obtainableIn: z.array(z.string()).default([]),
    eventOnlyIn: z.array(z.string()).default([]),
    storableIn: z.array(z.string()).default([]),
    transferOnlyIn: z.array(z.string()).default([]),
    names: z.record(z.string()).default({}),
    speciesNames: z.record(z.string()).optional(),
    formNames: z.record(z.string()).optional(),
    refs: z
      .object({
        pkApiId: z.string().nullish(),
        pkApiFormId: z.string().nullish(),
        pkApiFormSlug: z.string().nullish(),
      })
      .passthrough(),
  })
  .passthrough();

export type UpstreamPokemon = z.infer<typeof upstreamPokemonSchema>;

export const upstreamPresetSchema = z
  .object({
    schemaVersion: z.number().int(),
    id: z.string(),
    gameSet: z.string(),
    name: z.string(),
    description: z.string(),
    source: z.object({ version: z.number().int() }).passthrough(),
    boxes: z.array(
      z.object({
        name: z.string(),
        /** A slot is a variant id or a hole. Holes are real: grouped-balanced has ten. */
        slots: z.array(z.string().nullable()),
      }),
    ),
  })
  .passthrough();

export type UpstreamPreset = z.infer<typeof upstreamPresetSchema>;

export const upstreamGameSchema = z
  .object({
    id: z.string(),
    name: z.string(),
    gen: z.number().int(),
    gameSet: z.string().nullish(),
    releaseDate: z.string().nullish(),
    region: z.string().nullish(),
    originMark: z.string().nullish(),
    features: z.object({ shiny: z.boolean().optional() }).passthrough().optional(),
  })
  .passthrough();

export type UpstreamGame = z.infer<typeof upstreamGameSchema>;

function readJson(file: string): unknown {
  return JSON.parse(fs.readFileSync(file, 'utf8'));
}

export function readPreset(): UpstreamPreset {
  const file = path.join(
    paths.upstream,
    'data/boxpresets/modern/home/grouped-balanced.json',
  );
  return upstreamPresetSchema.parse(readJson(file));
}

export function readPokemon(id: string): UpstreamPokemon {
  const file = path.join(paths.upstream, 'data/pokemon', `${id}.json`);
  if (!fs.existsSync(file)) {
    throw new Error(`Preset references "${id}" but upstream has no data/pokemon/${id}.json`);
  }
  return upstreamPokemonSchema.parse(readJson(file));
}

export function readGame(id: string): UpstreamGame {
  const file = path.join(paths.upstream, 'data/games', `${id}.json`);
  if (!fs.existsSync(file)) throw new Error(`No upstream game data/games/${id}.json`);
  return upstreamGameSchema.parse(readJson(file));
}
