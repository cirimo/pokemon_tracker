import { describe, expect, it } from 'vitest';
import { curatedSchemas } from '../src/curated.js';
import { guideRows } from '../src/guide-rows.js';

const src = 'https://bulbapedia.bulbagarden.net/wiki/Dynamax_Adventure';

const book = curatedSchemas.guideBook.parse({
  gameSet: 'swsh',
  locations: [{ id: 'max-lair', name: 'Max Lair', dlc: 'ct', source: src }],
  requirements: [
    { id: 'crown-tundra', label: 'The Crown Tundra', kind: 'dlc', source: src },
    {
      id: 'shiny-charm',
      label: 'Shiny Charm',
      kind: 'item',
      optional: true,
      source: src,
      farm: [{ kind: 'reward', detail: 'Complete the Galar Pokedex.', source: src }],
    },
  ],
  builds: [],
  procedures: [
    {
      id: 'dynamax-adventure',
      title: 'Dynamax Adventure',
      steps: [
        { section: 'loop', text: 'Pick the noted path.', location: 'max-lair', source: src },
        { section: 'check', text: 'Shininess shows on the keep screen.', source: src },
      ],
    },
  ],
  guides: [
    {
      id: 'zygarde',
      title: 'Zygarde',
      method: 'dynamax-adventure',
      procedure: 'dynamax-adventure',
      variants: ['zygarde'],
      oneInSave: true,
      requires: [{ id: 'crown-tundra' }, { id: 'shiny-charm', role: 'odds' }],
      advice: [{ kind: 'take', subject: 'Ice attackers', why: 'Ice hits Dragon/Ground four times over.', source: src }],
    },
  ],
});

describe('guideRows', () => {
  const rows = guideRows([book]);

  it('writes procedures and guides to one table, told apart by kind', () => {
    expect(rows.guide).toEqual([
      ['dynamax-adventure', 'swsh', 'procedure', 'Dynamax Adventure', null, null, null, 0],
      ['zygarde', 'swsh', 'guide', 'Zygarde', 'dynamax-adventure', 'dynamax-adventure', null, 1],
    ]);
  });

  it('numbers steps in the order they are written, per owner', () => {
    expect(rows.guide_step.map((s) => [s[0], s[1], s[2]])).toEqual([
      ['dynamax-adventure', 0, 'loop'],
      ['dynamax-adventure', 1, 'check'],
    ]);
  });

  it('targets variants, never slots', () => {
    expect(rows.guide_target).toEqual([['zygarde', 'zygarde']]);
  });

  it('links requirements with their role, and gives a requirement its farm chain', () => {
    expect(rows.guide_link).toEqual([
      ['zygarde', 'requirement', 'crown-tundra', null],
      ['zygarde', 'requirement', 'shiny-charm', 'odds'],
    ]);
    expect(rows.farm_source).toEqual([['requirement', 'shiny-charm', 0, 'reward', null, 'Complete the Galar Pokedex.', src]]);
  });

  it('refuses a row without a source', () => {
    const missing = curatedSchemas.guideBook.safeParse({
      gameSet: 'swsh',
      locations: [{ id: 'max-lair', name: 'Max Lair' }],
      requirements: [],
      builds: [],
      procedures: [],
      guides: [],
    });
    expect(missing.success).toBe(false);
  });

  it('refuses a build with no farm chain', () => {
    const unfarmed = curatedSchemas.guideBook.safeParse({
      gameSet: 'swsh',
      locations: [],
      requirements: [],
      builds: [{ id: 'catcher', variant: 'gallade', title: 'Catcher', purpose: 'x', parts: [{ kind: 'move', value: 'False Swipe', why: 'x', source: src }], farm: [] }],
      procedures: [],
      guides: [],
    });
    expect(unfarmed.success).toBe(false);
  });
});
