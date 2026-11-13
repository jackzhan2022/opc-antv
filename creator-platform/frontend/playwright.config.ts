import { defineConfig } from "@playwright/test";
export default defineConfig({
  testDir: "./tests",
  fullyParallel: false,
  workers: 1,
  expect: { timeout: 10000 },
  use: {
    baseURL: "http://127.0.0.1:8002",
    viewport: { width: 1440, height: 1000 },
    headless: true,
    launchOptions: {
      executablePath:
        process.env.PLAYWRIGHT_CHROMIUM_EXECUTABLE_PATH ||
        "C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe",
    },
  },
  webServer: {
    command: "npm run build && node tests/serve-build.cjs",
    url: "http://127.0.0.1:8002/creators/",
    reuseExistingServer: false,
    timeout: 180000,
  },
});
