import { test, expect } from "@playwright/test";
test("login, navigation, and responsive workspace with deterministic API fixtures", async ({
  page,
}) => {
  await page.route("**/api/v1/**", async (route) => {
    const path = new URL(route.request().url()).pathname;
    let data: any = {};
    if (path.endsWith("/auth/login")) {
      expect(route.request().headers()).not.toHaveProperty("authorization");
      data = { accessToken: "fixture", user: "trader", role: "TRADER" };
    }
    else if (path.endsWith("/dashboard"))
      data = {
        orders: 12,
        statuses: { READY: 7, BLOCKED: 3, AWAITING_REVIEW: 2 },
        openCases: 2,
        recentActivity: [
          {
            id: "event1",
            eventType: "PreTradeEvaluated",
            aggregateId: "order123456",
            portfolioId: "PORTFOLIO_A",
            occurredAt: "2026-10-14T04:00:00Z",
          },
        ],
      };
    else if (path.endsWith("/orders"))
      data = {
        items: [
          {
            id: "order123456",
            portfolioId: "PORTFOLIO_A",
            instrumentId: "ACME",
            side: "BUY",
            quantity: "10",
            price: "100",
            status: "READY",
            createdAt: "2026-10-14T04:00:00Z",
          },
        ],
        total: 1,
      };
    else if (path.endsWith("/cases")) data = { items: [], total: 0 };
    else if (path.endsWith("/rulesets"))
      data = {
        items: [
          { id: "rules-v1", name: "Initial restrictions", published: true, restrictedInstruments: ["BANNED"], restrictedIssuers: ["BANNED_CORP"], maxNotional: "100000" },
          { id: "rules-v2", name: "Updated restrictions", published: false, restrictedInstruments: ["BANNED", "ACME"], restrictedIssuers: ["BANNED_CORP"], maxNotional: "100000" },
        ],
        active: { rulesetId: "rules-v1", version: 1 },
      };
    else if (path.endsWith("/portfolios"))
      data = [{ id: "PORTFOLIO_A", name: "Growth Fund" }];
    else if (path.endsWith("/stream")) {
      await route.fulfill({
        status: 200,
        contentType: "text/event-stream",
        body: ": heartbeat\n\n",
      });
      return;
    }
    await route.fulfill({ json: data });
  });
  await page.goto("/");
  await expect(
    page.getByRole("heading", { name: "Welcome to TradeGuard" }),
  ).toBeVisible();
  await page.getByLabel("Demo password").fill("fixture-password");
  await page.getByRole("button", { name: "Open workspace" }).click();
  await expect(
    page.getByRole("heading", { name: "Portfolio overview" }),
  ).toBeVisible();
  await expect(page.getByText("12", { exact: true })).toBeVisible();
  await page.screenshot({
    path: "../docs/screenshots/dashboard.png",
    fullPage: true,
  });
  await page.getByRole("link", { name: "Orders", exact: true }).click();
  await expect(page.getByText("ACME", { exact: true })).toBeVisible();
  await page.getByRole("button", { name: "New order" }).click();
  await expect(page.getByRole("dialog")).toBeVisible();
  await page.getByRole("button", { name: "Cancel" }).click();
  await page.setViewportSize({ width: 390, height: 844 });
  await expect(
    page.getByRole("heading", { name: "Orders", exact: true }),
  ).toBeVisible();
  await page.screenshot({
    path: "../docs/screenshots/mobile-orders.png",
    fullPage: true,
  });
  await page.getByRole("link", { name: "Rule management" }).click();
  await page.getByRole("button", { name: "Compare with active" }).nth(1).click();
  await expect(page.getByRole("dialog")).toBeVisible();
  await expect(page.getByRole("dialog").getByText("BANNED, ACME", { exact: true })).toBeVisible();
  await page.getByRole("button", { name: "Close", exact: true }).click();
});
