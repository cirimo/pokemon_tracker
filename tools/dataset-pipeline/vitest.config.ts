import { defineConfig } from 'vitest/config';

export default defineConfig({
  test: {
    // Scoped to our own tests. .upstream/ is a full checkout of the PokePC repo and
    // carries its own suite, which is not ours to run or to keep green.
    include: ['test/**/*.test.ts'],
    exclude: ['node_modules/**', '.upstream/**', 'dist/**'],
  },
});
