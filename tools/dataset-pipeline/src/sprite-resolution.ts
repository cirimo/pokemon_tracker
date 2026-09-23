/**
 * Which PokeAPI file is a variant's sprite, and which variants may share one.
 *
 * Pure functions, kept apart from the fetching in build-sprites.ts so the order can be
 * tested without a network.
 *
 * THE LESSON (docs/adr/0005-sprites.md, amended 2026-09-23): the first version of this
 * resolved by refs.pkApiId, measured 1387/1387 resolved, and shipped 364 sprites that
 * were copies of another. For a cosmetic form the pokemon id IS the base species, so it
 * always resolves -- to the wrong picture. Coverage said nothing about correctness. The
 * `sprite-shared` validator is what measures correctness now; this order is what makes
 * it pass.
 */

/** Paths are relative to PokeAPI's sprites/pokemon/other/home/. */
export type SpriteRule = 'override' | 'female' | 'form' | 'pokemon';

export interface SpriteCandidate {
  rule: SpriteRule;
  path: string;
}

export interface SpriteVariant {
  id: string;
  dexNum: number;
  isDefault: boolean;
  isFemaleForm: boolean;
  isRegional: boolean;
  shinyReleased: boolean;
  baseSpecies?: string | null;
  refs: { pkApiId?: string | null; pkApiFormSlug?: string | null };
}

export interface SpriteOverride {
  variantId: string;
  file: string;
}

/**
 * The candidates for one variant, most specific first. The first that exists wins.
 *
 *  1. A curated override. It is the ONLY way to reach a non-shiny file, and only for a
 *     variant whose shiny was never released -- the fallback is explicit, never silent.
 *  2. shiny/female/<pokemonId>.png for a gender form. The female art is its own file
 *     under the male's pokemon id.
 *  3. shiny/<dex>-<form>.png, the form's own art, named after the PokeAPI form slug
 *     with the species prefix removed (alcremie-ruby-swirl-star-sweet ->
 *     869-ruby-swirl-star-sweet). Regional forms have their own pokemon id and no such
 *     file, so they skip this step.
 *  4. shiny/<pokemonId>.png. Correct for species, regional forms and forms with their
 *     own pokemon id (Meowstic-F, the Hisuian forms). For a cosmetic form whose steps 2
 *     and 3 found nothing, this is the base species' picture -- which is exactly what
 *     `sprite-shared` then refuses.
 *
 * refs.pkApiFormId is deliberately absent. It is a pokemon-FORM id, and the sprite
 * repository is keyed by pokemon id: form 10196 is Original Cap Pikachu, pokemon 10196 is
 * Gigantamax Charizard. Trying it "as a fallback" is how the cap Pikachu shipped with
 * other Pokemon's sprites.
 */
export function spriteCandidates(v: SpriteVariant, override?: string): SpriteCandidate[] {
  if (override) {
    if (!override.startsWith('shiny/') && v.shinyReleased) {
      throw new Error(
        v.id + ': override ' + override + ' is not shiny art, but a shiny ' + v.id +
          ' was released. Only a variant with no shiny may fall back to normal colours.',
      );
    }
    return [{ rule: 'override', path: override }];
  }

  const candidates: SpriteCandidate[] = [];
  const pokemonId = v.refs.pkApiId;
  if (v.isFemaleForm && pokemonId) {
    candidates.push({ rule: 'female', path: 'shiny/female/' + pokemonId + '.png' });
  }
  const form = formSuffix(v);
  if (form) candidates.push({ rule: 'form', path: 'shiny/' + v.dexNum + '-' + form + '.png' });
  if (pokemonId) candidates.push({ rule: 'pokemon', path: 'shiny/' + pokemonId + '.png' });
  return candidates;
}

function formSuffix(v: SpriteVariant): string | null {
  const slug = v.refs.pkApiFormSlug;
  if (!slug || v.isRegional) return null;
  const species = v.baseSpecies ?? v.id;
  return slug.startsWith(species + '-') ? slug.slice(species.length + 1) : null;
}

export interface SharedSpriteFinding {
  variants: string[];
  detail: string;
}

/**
 * Two variants may share sprite bytes only when a curated group says the art really is
 * identical. Checked in both directions: an unlisted collision is a wrong sprite, and a
 * listed group whose members no longer share bytes is a stale claim.
 */
export function sharedSpriteFindings(
  hashes: Map<string, string>,
  groups: { variants: string[] }[],
): SharedSpriteFinding[] {
  const findings: SharedSpriteFinding[] = [];
  const groupOf = new Map<string, number>();
  groups.forEach((g, i) => g.variants.forEach((v) => groupOf.set(v, i)));

  const byHash = new Map<string, string[]>();
  for (const [variant, hash] of [...hashes.entries()].sort()) {
    byHash.set(hash, [...(byHash.get(hash) ?? []), variant]);
  }

  for (const variants of byHash.values()) {
    if (variants.length < 2) continue;
    const listed = new Set(variants.map((v) => groupOf.get(v)));
    if (listed.size !== 1 || listed.has(undefined)) {
      findings.push({
        variants,
        detail: 'share sprite bytes but are not one allow-listed group in data/curated/sprites.yaml',
      });
    }
  }

  groups.forEach((g) => {
    const present = g.variants.filter((v) => hashes.has(v));
    const distinct = new Set(present.map((v) => hashes.get(v)));
    if (distinct.size > 1) {
      findings.push({
        variants: present,
        detail: 'are allow-listed as identical art but their sprites differ -- the claim is stale',
      });
    }
  });

  return findings;
}
