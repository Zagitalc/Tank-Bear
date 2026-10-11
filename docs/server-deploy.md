# Running Tank Bear on your own server (runbook)

**Deployment update, 11 October 2026:** the API and Caddy are installed and running
on `145.241.223.194`. [Public status](https://tankbear.duckdns.org/status) returns
HTTP 200 over verified HTTPS; HTTP redirects to HTTPS and protected API routes
reject missing keys. Oracle and host firewalls allow TCP 22, 80 and 443.
The first Fuel Finder refresh completed at 12:12 UTC (13:12 BST), loading 8,131
stations, 8,127 with prices. Status reports `ok`. The 15-minute refresh and daily
backup timers are enabled; routing has not been deployed.

See [server verification](server-verification.md) for the 11 October local test results and remaining limits.

Why a server at all: Fuel Finder locked out Cloudflare's shared addresses (see `hosting.md`), so the refresh needs its own
static UK IP. This setup runs the whole thing on one machine: API, database, 15-minute refresh, routing and HTTPS.

```
phone app --HTTPS--> Caddy (80/443) --> Node API (127.0.0.1:8787) --> SQLite file
                                              |--> Valhalla routing (127.0.0.1:8002, Docker)
systemd timer (every 15 min) --> refresh job --> Fuel Finder --> SQLite file
```

## What you need first

- The Oracle VM from `oracle-setup.md` with a reserved public IP, SSH working, and in the cloud network security list
  inbound TCP **22, 80 and 443** open (and nothing else; **never 8002**).
- A free hostname pointing at that IP: sign in at duckdns.org, create a name such as `tankbear` (giving
  `tankbear.duckdns.org`), and set its IP to the reserved address.
- Your Fuel Finder client id and secret (you type them onto the server yourself).

## 1. Copy the code (from your Mac)

```bash
cd /Users/longhchung/Documents/GitHub/Tank-Bear
deploy/oracle/push.sh ubuntu@YOUR_RESERVED_IP
```

This sends only `backend/src`, `backend/server`, `backend/migrations` and `backend/package.json`, plus the install kit. No secrets.

## 2. Install (on the server)

```bash
ssh -i ~/.ssh/tankbear_oracle ubuntu@YOUR_RESERVED_IP
sudo bash /tmp/tankbear-deploy/install.sh
```

It installs Node 24.19.0 (checksum verified), Docker, Caddy, SQLite tools, creates the `tankbear` user and folders,
installs the systemd units and Caddy config, and opens ports 22, 80 and 443 on the machine's own firewall.
Docker starts; Tank Bear services are not started. On first installation Caddy is held stopped until configured.

## 3. Fill in the settings (on the server)

```bash
sudo nano /etc/tankbear/tankbear.env
sudo nano /etc/caddy/tankbear.env
```

In `/etc/tankbear/tankbear.env`, set `API_KEYS` (make one with `openssl rand -base64 33`; keep a copy
for the app) and the two Fuel Finder values. Leave `ROUTING_GRAPH_VERSION` empty until routing is ready;
journey searches return unavailable while Nearby and refresh can run. The file is readable only by root and
the service user. Never paste it into chat or Git.

In `/etc/caddy/tankbear.env`, set only `TANKBEAR_HOST` (for example `tankbear.duckdns.org`).
Caddy must not receive the application keys or Fuel Finder credentials.

## 4. Start the API and prove one refresh

```bash
sudo systemctl enable --now tankbear-api caddy
sudo systemctl start tankbear-refresh
journalctl -u tankbear-refresh -n 20 --no-pager
curl -s http://127.0.0.1:8787/status
curl -s https://YOUR_HOSTNAME/status
```

Require the refresh log to show `"status":"complete"` and `/status` to report `fuelData: "ok"`.
Only after that succeeds, start continuous collection:

```bash
sudo systemctl enable --now tankbear-refresh.timer tankbear-backup.timer
systemctl list-timers 'tankbear-*'
```

If the refresh reports HTTP 403 or 429, keep the refresh timer disabled and investigate provider access.
Request allowlisting with the organisation name, reason, location "Oracle Cloud UK South, London", and the
reserved IP if needed. Do not retry in a loop: failed attempts can extend a lockout.
No allowlist request is sent automatically.

## 5. Start routing after the refresh stage is reviewed

Before deployment, select and record a tested Valhalla image digest in the Compose file; its current `latest`
reference is provisional. Verify that the selected image supports Arm64.

```bash
cd /tmp/tankbear-deploy/valhalla
sudo docker compose up -d
sudo docker compose logs -f valhalla
```

The first start downloads the Great Britain extract and builds the road graph: expect a long wait and several GB of RAM.
If the build runs out of memory, inspect the logs. It listens on 127.0.0.1 only.
Check with `curl -s http://127.0.0.1:8002/status`. After the graph is ready, set `ROUTING_GRAPH_VERSION`
to the recorded extract/build identifier and restart `tankbear-api`. Measure a real journey search before claiming
routing is ready. `/status` measures feed health, not routing readiness.

## 6. Verify operation

Check the HTTPS endpoint, successful timer runs, actual backups and a restore rehearsal. Confirm logs contain no
coordinates or keys, including proxy error paths. Keep an off-machine backup; price history cannot be reconstructed.

## 7. Point the app at it

In the Android app Settings: server address `https://YOUR_HOSTNAME`, app key = the `API_KEYS` value. (This typed setup is
for development; see `release-readiness.md` for the release plan.)

## Day-to-day

- **Is it healthy?** `GET /status` is open and cheap. Point a free uptime monitor of your choice at it and alert on
  `"status":"degraded"` or no answer. `fuelData` becomes `stale` after 6 hours without a successful refresh.
- **Logs:** `journalctl -u tankbear-api -f`. Request lines hold method, a fixed route label, status and time only: never coordinates or keys.
- **Update the code:** run `push.sh` again, then `sudo systemctl restart tankbear-api` (migrations apply at start).
- **Backups:** daily, last 7 kept in `/var/lib/tankbear/backups` (price history cannot be rebuilt). Copy the newest one
  somewhere else now and then; the same disk is not a backup against losing the machine.
- **Rotate the app key:** put the new key first in `API_KEYS` (comma-separated), update the app, then remove the old one and restart.
- **Refresh the road graph:** change `ROUTING_GRAPH_VERSION` after rebuilding Valhalla (delete `/var/lib/valhalla/custom_files`
  and `docker compose up -d` again) so cached route contexts differ.

## What I could not test (watch for these)

- Full restore rehearsal, off-machine backups, and Docker routing operation.
- Valhalla's memory use building Great Britain on 12 GB, and its route quality for the whole UK (only Reading to Oxford was checked).
- Node's built-in SQLite under load from the app plus the refresh at once (WAL mode and a 10 second lock wait are set).
- Long-term refresh reliability (the first live refresh succeeded without an allowlist change).

## Security notes

- Only Caddy faces the internet. The API and routing engine listen on 127.0.0.1.
- The API trusts the caller address only from Caddy on the same machine (`TRUST_PROXY=1`); a client-sent address header is ignored.
- Caddy has no access log on purpose, because request URLs contain coordinates.
- Secrets live in `/etc/tankbear/tankbear.env` only. SSH keys: keep the private key off shared drives.
- Keep the machine patched: `sudo apt update && sudo apt upgrade` now and then, and reboot when asked.
