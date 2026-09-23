import { describe, expect, it } from 'vitest';
import {
  sharedSpriteFindings,
  spriteCandidates,
  type SpriteVariant,
} from '../src/sprite-resolution.js';

/** Shaped like the upstream rows these cases were taken from, at pokepc/dataset 6.8.2. */
function variant(overrides: Partial<SpriteVariant> & Pick<SpriteVariant, 'id' | 'dexNum'>): SpriteVariant {
  return {
    isDefault: false,
    isFemaleForm: false,
    isRegional: false,
    shinyReleased: true,
    baseSpecies: null,
    refs: {},
    ...overrides,
  };
}

const paths = (v: SpriteVariant, override?: string) =>
  spriteCandidates(v, override).map((c) => c.path);

describe('spriteCandidates', () => {
  it('tries a cosmetic form\'s own file before its base species\' file', () => {
    // The bug: pkApiId is 201 for every Unown letter, so resolving by it alone gave all
    // 28 the same picture.
    const unownB = variant({
      id: 'unown-b',
      dexNum: 201,
      baseSpecies: 'unown',
      refs: { pkApiId: '201', pkApiFormSlug: 'unown-b' },
    });
    expect(paths(unownB)).toEqual(['shiny/201-b.png', 'shiny/201.png']);
  });

  it('strips only the species prefix from a multi-part form slug', () => {
    const alcremie = variant({
      id: 'alcremie-ruby-swirl-star',
      dexNum: 869,
      baseSpecies: 'alcremie',
      refs: { pkApiId: '869', pkApiFormSlug: 'alcremie-ruby-swirl-star-sweet' },
    });
    expect(paths(alcremie)[0]).toBe('shiny/869-ruby-swirl-star-sweet.png');
  });

  it('finds the default form\'s own file when the default is not PokeAPI\'s default', () => {
    // PokePC's default Vivillon is Icy Snow; PokeAPI's 666.png is Meadow.
    const vivillon = variant({
      id: 'vivillon',
      dexNum: 666,
      isDefault: true,
      refs: { pkApiId: '666', pkApiFormSlug: 'vivillon-icy-snow' },
    });
    expect(paths(vivillon)[0]).toBe('shiny/666-icy-snow.png');
  });

  it('tries the female file first for a gender form', () => {
    const venusaurF = variant({
      id: 'venusaur-f',
      dexNum: 3,
      isFemaleForm: true,
      baseSpecies: 'venusaur',
      refs: { pkApiId: '3', pkApiFormSlug: 'venusaur' },
    });
    expect(paths(venusaurF)).toEqual(['shiny/female/3.png', 'shiny/3.png']);
  });

  it('resolves a regional form by its own pokemon id', () => {
    const sneasel = variant({
      id: 'sneasel-hisui',
      dexNum: 215,
      isRegional: true,
      baseSpecies: 'sneasel',
      refs: { pkApiId: '10235', pkApiFormSlug: 'sneasel-hisui' },
    });
    expect(paths(sneasel)).toEqual(['shiny/10235.png']);
  });

  it('never tries the pokemon-form id, whose number means a different Pokemon', () => {
    // Form 10196 is Original Cap Pikachu; pokemon 10196 is Gigantamax Charizard.
    const cap = variant({
      id: 'pikachu-original',
      dexNum: 25,
      shinyReleased: false,
      baseSpecies: 'pikachu',
      refs: { pkApiId: '10094', pkApiFormSlug: 'pikachu-original-cap' },
    });
    expect(paths(cap).some((p) => p.includes('10196'))).toBe(false);
  });

  it('uses a curated override alone', () => {
    const minior = variant({
      id: 'minior-blue',
      dexNum: 774,
      baseSpecies: 'minior-red',
      refs: { pkApiId: '10140', pkApiFormSlug: 'minior-blue' },
    });
    expect(spriteCandidates(minior, 'shiny/10136.png')).toEqual([
      { rule: 'override', path: 'shiny/10136.png' },
    ]);
  });

  it('allows normal-colour art only for a variant with no shiny', () => {
    const cap = variant({ id: 'pikachu-original', dexNum: 25, shinyReleased: false });
    expect(paths(cap, '10094.png')).toEqual(['10094.png']);

    const pikachu = variant({ id: 'pikachu', dexNum: 25, isDefault: true });
    expect(() => spriteCandidates(pikachu, '25.png')).toThrow(/not shiny art/);
  });
});

describe('sharedSpriteFindings', () => {
  const hashes = (entries: Record<string, string>) => new Map(Object.entries(entries));

  it('passes when every sprite is distinct', () => {
    expect(sharedSpriteFindings(hashes({ a: '1', b: '2' }), [])).toEqual([]);
  });

  it('refuses two variants that share bytes without a curated group', () => {
    const findings = sharedSpriteFindings(hashes({ unown: 'x', 'unown-b': 'x', abra: 'y' }), []);
    expect(findings.map((f) => f.variants)).toEqual([['unown', 'unown-b']]);
  });

  it('accepts a collision that is exactly one curated group', () => {
    const groups = [{ variants: ['sinistea', 'sinistea-antique'] }];
    expect(sharedSpriteFindings(hashes({ sinistea: 'x', 'sinistea-antique': 'x' }), groups)).toEqual([]);
  });

  it('refuses a collision that spans two curated groups', () => {
    // Two groups each say "these share art", but not with each other.
    const groups = [{ variants: ['a', 'b'] }, { variants: ['c', 'd'] }];
    const findings = sharedSpriteFindings(hashes({ a: 'x', b: 'x', c: 'x', d: 'x' }), groups);
    expect(findings).toHaveLength(1);
    expect(findings[0]!.variants).toEqual(['a', 'b', 'c', 'd']);
  });

  it('refuses a collision where only some variants are listed', () => {
    const groups = [{ variants: ['torchic', 'torchic-f'] }];
    const findings = sharedSpriteFindings(
      hashes({ torchic: 'x', 'torchic-f': 'x', combusken: 'x' }),
      groups,
    );
    expect(findings).toHaveLength(1);
  });

  it('refuses a curated group whose sprites no longer match', () => {
    const groups = [{ variants: ['torchic', 'torchic-f'] }];
    const findings = sharedSpriteFindings(hashes({ torchic: 'x', 'torchic-f': 'y' }), groups);
    expect(findings.map((f) => f.detail)).toEqual([expect.stringMatching(/stale/)]);
  });
});
