import { defineConfig } from "@playwright/test";
export default defineConfig({
  testDir: "./e2e",
  use: {
    baseURL: process.env.TRADEGUARD_URL || "http://127.0.0.1:4173",
    viewport: { width: 1440, height: 1000 },
  },
  webServer: process.env.TRADEGUARD_URL
    ? undefined
    : {
        command: "npm run dev -- --port 4173",
        url: "http://127.0.0.1:4173",
        reuseExistingServer: true,
      },
  reporter: "list",
});
