import { test, expect, vi, afterEach } from "vitest";
import { api, setToken } from "./api";
afterEach(() => {
  setToken("");
  vi.unstubAllGlobals();
});
test("sends bearer token and stable retry key", async () => {
  const fetcher = vi
    .fn()
    .mockResolvedValue({ ok: true, json: async () => ({ id: "one" }) });
  vi.stubGlobal("fetch", fetcher);
  setToken("access");
  await api("/orders", { quantity: "1" }, "same-key");
  expect(fetcher.mock.calls[0][1].headers).toMatchObject({
    Authorization: "Bearer access",
    "Idempotency-Key": "same-key",
  });
});
test("surfaces structured backend errors", async () => {
  vi.stubGlobal(
    "fetch",
    vi
      .fn()
      .mockResolvedValue({
        ok: false,
        status: 409,
        json: async () => ({ detail: "Record changed" }),
      }),
  );
  await expect(api("/orders")).rejects.toThrow("Record changed");
});
test("never places access tokens in URL", async () => {
  const fetcher = vi
    .fn()
    .mockResolvedValue({ ok: true, json: async () => ({}) });
  vi.stubGlobal("fetch", fetcher);
  setToken("secret");
  await api("/dashboard");
  expect(fetcher.mock.calls[0][0]).toBe("/api/v1/dashboard");
});
test("login omits an empty bearer header before authentication", async () => {
  const fetcher = vi.fn().mockResolvedValue({ ok: true, json: async () => ({ accessToken: "new-token" }) });
  vi.stubGlobal("fetch", fetcher);
  setToken("");
  await api("/auth/login", { username: "trader", password: "test-only" });
  expect(fetcher.mock.calls[0][1].headers).not.toHaveProperty("Authorization");
});
test("login omits stale tokens even if one is present in memory", async () => {
  const fetcher = vi.fn().mockResolvedValue({ ok: true, json: async () => ({ accessToken: "new-token" }) });
  vi.stubGlobal("fetch", fetcher);
  setToken("expired-token");
  await api("/auth/login", { username: "trader", password: "test-only" });
  expect(fetcher.mock.calls[0][1].headers).not.toHaveProperty("Authorization");
});
test("unauthenticated requests never send malformed empty bearer headers", async () => {
  const fetcher = vi.fn().mockResolvedValue({ ok: true, json: async () => ({}) });
  vi.stubGlobal("fetch", fetcher);
  setToken("");
  await api("/dashboard");
  expect(fetcher.mock.calls[0][1].headers).not.toHaveProperty("Authorization");
});
