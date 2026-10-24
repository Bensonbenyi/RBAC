import { defineConfig } from '@playwright/test';
import { serverTarget } from './tests/target';

export default defineConfig({
  testDir: './tests',
  workers: 1,
  timeout: serverTarget ? 60000 : 30000,
  expect: { timeout: serverTarget ? 15000 : 5000 },
  outputDir: serverTarget ? 'test-results/server' : 'test-results/local',
  use: {
    browserName: 'chromium',
    viewport: { width: 1440, height: 1000 },
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
  },
  reporter: [
    ['list'],
    [
      'html',
      {
        open: 'never',
        outputFolder: serverTarget ? 'playwright-report/server' : 'playwright-report/local',
      },
    ],
    [
      'json',
      {
        outputFile: serverTarget
          ? '../docs/verification/server/browser-tests.json'
          : '../docs/verification/oral/local-browser-tests.json',
      },
    ],
  ],
});
