import { describe, expect, it } from 'vitest';
import { buildSlotRows } from '../src/build-reference.js';

/**
 * These assertions are the TypeScript half of a pair: :core:model has the same cases in
 * CopyIndexTest.kt. The pipeline assigns copyIndex at build time and the app assigns it
 * when diffing raw upstream JSON, so the two implementations must agree exactly or a
 * preset revision will appear to add and remove the same slot.
 */
describe('buildSlotRows', () => {
  it('skips holes without shifting the slot index', () => {
    const rows = buildSlotRows(
      [{ name: 'Hisui', slots: ['decidueye-hisui', null, null, 'typhlosion-hisui'] }],
      'grouped-balanced',
    );
    expect(rows).toHaveLength(2);
    expect(rows.map((r) => r.slotIndex)).toEqual([0, 3]);
  });

  it('assigns an incrementing copy index to a repeated variant, in preset order', () => {
    const rows = buildSlotRows(
      [
        { name: 'Johto 2', slots: ['unown'] },
        { name: 'Unown Dex', slots: ['unown', 'unown-b'] },
      ],
      'grouped-balanced',
    );
    expect(rows.map((r) => [r.variantId, r.copyIndex])).toEqual([
      ['unown', 0],
      ['unown', 1],
      ['unown-b', 0],
    ]);
  });

  it('gives distinct variants copy index zero', () => {
    const rows = buildSlotRows(
      [{ name: 'Kanto 1', slots: ['bulbasaur', 'ivysaur', 'venusaur'] }],
      'grouped-balanced',
    );
    expect(rows.every((r) => r.copyIndex === 0)).toBe(true);
  });

  it('numbers boxes from zero and carries the preset id', () => {
    const rows = buildSlotRows(
      [
        { name: 'A', slots: ['bulbasaur'] },
        { name: 'B', slots: ['ivysaur'] },
      ],
      'grouped-balanced',
    );
    expect(rows.map((r) => r.boxIndex)).toEqual([0, 1]);
    expect(rows.every((r) => r.presetId === 'grouped-balanced')).toBe(true);
  });

  it('produces no rows for a box that is entirely holes', () => {
    expect(buildSlotRows([{ name: 'empty', slots: [null, null] }], 'p')).toEqual([]);
  });
});
