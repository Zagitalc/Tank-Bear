# Hosting options (researched 5 October 2026)

Goal: Tank Bear runs without the developer's Mac, at no cost if possible. Limits below are from Cloudflare's and
Oracle's own documentation as read that day; check them again before relying on them.

## What has to run somewhere

1. API (journeys, nearby, history) with a small database that holds about 8,100 stations, current prices, price changes
   and a refresh log.
2. A refresh job every 15 minutes: about 36 paced requests (roughly 30 seconds), parsing about 11 MB of JSON, then diffing
   against stored data.
3. A routing engine (Valhalla) with a Great Britain road graph, reachable from the API.
4. HTTPS in front of it, and the Fuel Finder credentials kept as secrets.

## Finding 1: Cloudflare's free plan cannot run the refresh

Workers Free allows **10 ms of CPU** per request and per cron trigger; Paid ($5 a month at last read) allows 30 seconds for a
cron under an hour apart. Parsing and diffing the national feed costs far more than 10 ms of CPU, so the scheduled handler
would be cut off on the free plan. (Wall-clock waiting on the network does not count as CPU, but the JSON parsing does.)
Free D1 also limits reads to 5 million rows a day: each refresh currently reads about 34,000 rows, so 96 refreshes use
about 3.3 million, leaving little room for user lookups. Writes (100,000 a day) are fine after the first load.

So "Workers + D1, free" works for the API at low traffic but not for the refresh. This was not visible locally because
`wrangler dev` has no CPU limit.

## Finding 2: Valhalla needs a real server

It needs a few GB of RAM to build the Great Britain graph and several GB of disk. Oracle Cloud's Always Free tier is the
only mainstream free option big enough: an Arm (Ampere A1) VM with up to **2 OCPUs and 12 GB** of memory, 200 GB of
disk and 10 TB a month of outbound traffic (per Oracle's documentation page; older material says 4 OCPUs and 24 GB).

Risks with Oracle Always Free: sign-up needs a card and is sometimes refused; A1 capacity in a region is often "out of
capacity" and may need retrying; idle Always Free instances can be reclaimed if CPU, network and (for A1) memory all stay
under 20% for seven days, which a quiet routing server could do; there is no support or uptime promise.

## Options

| Option | Cost | Mac-independent | Catches |
|---|---|---|---|
| **A. One Oracle Always Free VM runs everything** (Node host for the existing code with SQLite, Valhalla in Docker, Caddy or a Cloudflare Tunnel for HTTPS, systemd timer for the refresh) | £0 (a domain is optional) | Yes | Needs a Node host for the API (the code is runtime-neutral; the test D1 stand-in already runs it on SQLite). I own patching. Reclaim and capacity risks above. Single point of failure. |
| **B. Cloudflare Workers Paid ($5/month) + D1 for API and refresh; Oracle VM for Valhalla only** | about $5 a month | Yes | Not free. Two places to run. D1 read limits need watching on the paid plan too (billing instead of a hard stop). |
| **C. Hosted routing API instead of Valhalla** (for example OpenRouteService free plan) | £0 for low use | Yes | Daily request caps are small relative to 13 routes per search; the exact quota and commercial terms could not be read on 5 October. Needs a new routing adapter and route quality may differ. |
| **D. Keep Valhalla on the Mac** | £0 | No | Rules out the stated goal. |

## Recommendation

Option **A**, as a staged plan with a cost fallback to **B** if Oracle refuses sign-up or has no capacity:

1. Create an Oracle Always Free account (needs your card and your decision; nothing can be created without you).
2. Add a Node entry point that runs the existing handlers over SQLite (reusing the migration SQL), plus a refresh script.
   Test it locally first.
3. Build the Great Britain Valhalla graph on the VM (the Berkshire graph used by Spirited is too small).
4. Add HTTPS with a free hostname, set secrets as environment files with tight permissions, add the refresh timer, and start
   accumulating history.
5. Add a simple alert for refresh failures (a status check you can see; no third-party monitoring decided).

Nothing in this plan has been created or deployed. Keep Fuel Finder credentials and `API_KEYS` out of Git and out of
shared logs, and review the provider's terms on hosting a cached copy (see `release-readiness.md`).
