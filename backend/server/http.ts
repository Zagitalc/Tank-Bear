import type { IncomingMessage, RequestListener, ServerResponse } from "node:http";

export interface FetchHandler<E> {
  fetch(request: Request, env: E): Promise<Response>;
}

export interface HandlerOptions {
  /** Trust X-Real-IP from a reverse proxy on the same machine (Caddy sets it). Never enable on an open port. */
  trustProxy: boolean;
  maxBodyBytes: number;
  log?: (line: string) => void;
}

const LOOPBACK = new Set(["127.0.0.1", "::1", "::ffff:127.0.0.1"]);

function logRoute(path: string): string {
  if (["/health", "/status", "/v1/stations/nearby", "/v1/journeys/optimise"].includes(path)) return path;
  if (/^\/v1\/stations\/[^/]+\/history$/.test(path)) return "/v1/stations/:id/history";
  return "[unmatched]";
}

/**
 * The caller's address for rate limiting. A client-supplied cf-connecting-ip is always discarded: the guard
 * trusts that header, so only this function may set it, from the socket or from a trusted local proxy.
 */
export function clientAddress(req: Pick<IncomingMessage, "headers" | "socket">, trustProxy: boolean): string {
  const remote = req.socket.remoteAddress ?? "";
  const real = req.headers["x-real-ip"];
  if (trustProxy && LOOPBACK.has(remote) && typeof real === "string" && real.length > 0 && real.length < 64) return real;
  return remote;
}

export function createNodeHandler<E>(app: FetchHandler<E>, env: E, options: HandlerOptions): RequestListener {
  return async (req: IncomingMessage, res: ServerResponse) => {
    const started = Date.now();
    let status = 500;
    let path = "-";
    try {
      const headers = new Headers();
      for (const [k, v] of Object.entries(req.headers)) {
        if (k === "cf-connecting-ip" || v === undefined) continue;
        headers.set(k, Array.isArray(v) ? v.join(", ") : v);
      }
      headers.set("cf-connecting-ip", clientAddress(req, options.trustProxy));
      const url = new URL(req.url ?? "/", `http://${req.headers.host ?? "localhost"}`);
      path = logRoute(url.pathname); // Never log arbitrary path segments or query strings.
      const method = req.method ?? "GET";

      let body: Buffer | undefined;
      if (method !== "GET" && method !== "HEAD") {
        const chunks: Buffer[] = [];
        let size = 0;
        for await (const chunk of req) {
          size += (chunk as Buffer).length;
          if (size > options.maxBodyBytes) {
            status = 413;
            res.writeHead(413, { "content-type": "application/json", "cache-control": "no-store" });
            res.end(JSON.stringify({ error: { code: "TOO_LARGE", message: "Request body is too large." } }));
            req.destroy();
            return;
          }
          chunks.push(chunk as Buffer);
        }
        body = Buffer.concat(chunks);
      }

      const request = new Request(url, { method, headers, ...(body && body.length > 0 ? { body } : {}) });
      const response = await app.fetch(request, env);
      status = response.status;
      const out: Record<string, string> = {};
      response.headers.forEach((v, k) => { out[k] = v; });
      res.writeHead(status, out);
      res.end(method === "HEAD" ? undefined : Buffer.from(await response.arrayBuffer()));
    } catch {
      // Raw errors can carry URLs, coordinates or credentials: send nothing specific.
      if (!res.headersSent) {
        status = 500;
        res.writeHead(500, { "content-type": "application/json", "cache-control": "no-store" });
        res.end(JSON.stringify({ error: { code: "INTERNAL", message: "Something went wrong." } }));
      } else {
        res.end();
      }
    } finally {
      options.log?.(`${req.method ?? "-"} ${path} ${status} ${Date.now() - started}ms`);
    }
  };
}
