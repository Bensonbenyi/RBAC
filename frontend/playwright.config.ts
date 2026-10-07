import { defineConfig } from '@playwright/test';

export default defineConfig({
  testDir: './tests',
  workers: 1,
  timeout: 30000,
  use: {
    browserName: 'chromium',
    viewport: { width: 1440, height: 1000 },
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
  },
  reporter: [
    ['list'],
    ['html', { open: 'never' }],
    ['json', { outputFile: '../docs/verification/browser-tests.json' }],
  ],
});
