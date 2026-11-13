import { defineConfig } from '@playwright/test';
export default defineConfig({
 expect: {timeout:15000}, testDir: './tests', fullyParallel: false, workers: 1,
 use: { baseURL: 'http://localhost:8000', viewport: {width:1440,height:900}, headless:true,
  launchOptions: {executablePath:process.env.PLAYWRIGHT_CHROMIUM_EXECUTABLE_PATH} },
 webServer: { command: 'npm run build && node tests/serve-build.cjs', url: 'http://localhost:8000/login', reuseExistingServer: false, timeout:180000 },
});
