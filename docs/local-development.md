# Local development and backend setup

## Requirements

- Node.js 24 (`.nvmrc`), npm 11 recommended.
- Network access for the first dependency installation.
- No Cloudflare login, remote database or Fuel Finder credentials for Stage 1.

From the repository root:

```sh
npm ci
npm run check
npm run dev
```

`check` runs strict TypeScript checks, Node's test runner and an esbuild Worker
bundle plus the standalone calculation engine. It does not invoke a deployment
command. The lockfile pins dependencies. TypeScript's `erasableSyntaxOnly`
setting keeps code compatible with Node 24's native type-stripping test runner.
The Worker runtime compatibility date is pinned independently of today's date.

Wrangler starts on `127.0.0.1:8787` with D1 emulated locally. Its database ID is
an explicit zero UUID placeholder, not a real Cloudflare resource. Data lives in
ignored `.wrangler` state. No migrations or application tables are needed yet.
There is no configured cron or upstream request.

Stage 2 also runs entirely offline:

```sh
npm run demo:economics
```

The demo requires neither Wrangler nor D1. It evaluates the shared synthetic
route fixture with the pure engine. The engine bundle is built at
`backend/dist/domain/economics.js`; the Worker still exposes only `/health`.

```sh
curl -i http://127.0.0.1:8787/health
curl -I http://127.0.0.1:8787/health
curl -i -X POST http://127.0.0.1:8787/health
```

Expected: GET 200 with database `ok` and optimisation unavailable; HEAD 200
without a body; POST 405 with `Allow: GET, HEAD`. Unknown endpoints return 404.
A failed D1 probe returns 503 with a sanitised status. Responses are `no-store`.

With that server running, `npm run test:runtime` verifies the HTTP contract
against actual local workerd/D1 rather than a database stub. This suite is
separate from `npm run check` because it needs the running local server.

`backend/.dev.vars.example` documents where future local secrets belong. No
secret is required now. Never commit `.dev.vars`, `.env` or production keys.

## Android connection later

The Stage 1 app is an independent offline shell. Android emulator host loopback
uses `10.0.2.2`; physical devices need a deliberate development connection such
as `adb reverse`. Networking and a debug-only transport configuration will be
added when the client API adapter exists. Do not weaken release cleartext policy
or expose the local backend on all interfaces for this skeleton.

## Costs and remote actions

These scripts install public dependencies and use local emulation only. No
remote creation, migration, deployment, release, commit or push is included.
Production hosting, routing and map costs require a separate decision.
