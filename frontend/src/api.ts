export type Row = Record<string, any>;
let token = "";
export function setToken(value: string) {
  token = value;
}
export async function api<T = Row>(
  path: string,
  body?: Row,
  key?: string,
): Promise<T> {
  const response = await fetch(`/api/v1${path}`, {
    method: body ? "POST" : "GET",
    headers: {
      ...(path !== "/auth/login" && token
        ? { Authorization: `Bearer ${token}` }
        : {}),
      ...(body
        ? {
            "Content-Type": "application/json",
            "Idempotency-Key": key ?? crypto.randomUUID(),
          }
        : {}),
    },
    ...(body ? { body: JSON.stringify(body) } : {}),
  });
  if (!response.ok) {
    let message = `Request failed (${response.status})`;
    try {
      const error = await response.json();
      message = error.detail || message;
    } catch {}
    if (response.status === 401)
      window.dispatchEvent(new Event("tradeguard-expired"));
    throw new Error(message);
  }
  return response.json();
}
export async function stream(
  signal: AbortSignal,
  onChange: () => void,
  onConnection: (connected: boolean) => void,
) {
  let cursor = "0";
  while (!signal.aborted) {
    try {
      const response = await fetch("/api/v1/stream", {
        headers: { Authorization: `Bearer ${token}`, "Last-Event-ID": cursor },
        signal,
      });
      if (response.status === 401) {
        window.dispatchEvent(new Event("tradeguard-expired"));
        return;
      }
      if (!response.ok || !response.body) throw new Error("Stream unavailable");
      onConnection(true);
      const reader = response.body.getReader();
      const decoder = new TextDecoder();
      let buffer = "";
      while (!signal.aborted) {
        const chunk = await reader.read();
        if (chunk.done) break;
        buffer += decoder
          .decode(chunk.value, { stream: true })
          .replaceAll("\r\n", "\n");
        let index;
        while ((index = buffer.indexOf("\n\n")) >= 0) {
          const event = buffer.slice(0, index);
          buffer = buffer.slice(index + 2);
          const id = event.split("\n").find((x) => x.startsWith("id:"));
          if (id) cursor = id.slice(3).trim();
          if (
            event.includes("event:change") ||
            event.includes("event: change") ||
            event.includes("event:refresh") ||
            event.includes("event: refresh")
          )
            onChange();
        }
      }
    } catch (error) {
      if (signal.aborted) return;
    }
    onConnection(false);
    await new Promise<void>((resolve) => {
      const aborted = () => {
        clearTimeout(timeout);
        resolve();
      };
      const timeout = setTimeout(() => {
        signal.removeEventListener("abort", aborted);
        resolve();
      }, 2000);
      signal.addEventListener("abort", aborted, { once: true });
    });
  }
}
