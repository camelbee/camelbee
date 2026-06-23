/// <reference types="vitest/config" />
import { defineConfig } from 'vitest/config';
import path from 'path';

// Standalone test config so the dev-server mock plugin / proxy in vite.config.ts
// are not loaded during tests. Mirrors the "@" alias used by the app.
export default defineConfig({
  resolve: {
    alias: {
      '@': path.resolve(__dirname, 'src'),
    },
  },
  test: {
    globals: true,
    environment: 'jsdom',
    setupFiles: ['./src/test/setup.ts'],
    include: ['src/**/*.{test,spec}.{ts,tsx}'],
    coverage: {
      provider: 'v8',
      include: ['src/**/*.{ts,tsx}'],
      exclude: [
        'src/**/*.{test,spec}.{ts,tsx}',
        'src/test/**',
        'src/types/**', // type-only declarations, no runtime code
        'src/main.tsx', // app bootstrap / DOM mount
        'src/vite-env.d.ts',
      ],
      reporter: ['text', 'html'],
      thresholds: {
        lines: 90,
        functions: 90,
        statements: 90,
        branches: 80,
      },
    },
  },
});
