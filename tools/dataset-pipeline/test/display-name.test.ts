import { describe, expect, it } from 'vitest';
import { displayNameFor } from '../src/build-reference.js';

/** Names as upstream pokepc/dataset 6.8.2 ships them in data/pokemon/<id>.json. */
describe('displayNameFor', () => {
  it('names a form that upstream left as the bare species name', () => {
    // unown-n.json: names.eng "Unown", formNames.eng "N". Likewise O, U and W.
    const unownN = {
      id: 'unown-n',
      isDefault: false,
      names: { eng: 'Unown' },
      formNames: { eng: 'N' },
    };
    expect(displayNameFor(unownN, 'Unown')).toBe('Unown (N)');
  });

  it('does the same for Eternal Flower Floette', () => {
    const eternal = {
      id: 'floette-eternal',
      isDefault: false,
      names: { eng: 'Floette' },
      formNames: { eng: 'Eternal Flower' },
    };
    expect(displayNameFor(eternal, 'Floette')).toBe('Floette (Eternal Flower)');
  });

  it('keeps upstream names that already carry the form', () => {
    const unownB = {
      id: 'unown-b',
      isDefault: false,
      names: { eng: 'Unown (B)' },
      formNames: { eng: 'B' },
    };
    expect(displayNameFor(unownB, 'Unown')).toBe('Unown (B)');
  });

  it('leaves the default form as the species name', () => {
    // Red Flower is Floette's default; it is plain "Floette" in every game.
    const floette = {
      id: 'floette',
      isDefault: true,
      names: { eng: 'Floette' },
      formNames: { eng: 'Red Flower' },
    };
    expect(displayNameFor(floette, 'Floette')).toBe('Floette');
  });

  it('leaves a regional name alone even when its form name is odd', () => {
    // sneasel-hisui.json has formNames.eng "Male", an upstream quirk that the name
    // already sidesteps.
    const sneasel = {
      id: 'sneasel-hisui',
      isDefault: false,
      names: { eng: 'Sneasel (Hisuian Form)' },
      formNames: { eng: 'Male' },
    };
    expect(displayNameFor(sneasel, 'Sneasel')).toBe('Sneasel (Hisuian Form)');
  });
});
