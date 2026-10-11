# Routing deployment — 11 October 2026

## Image and isolation

- Official image: `ghcr.io/valhalla/valhalla-scripted:3.9.1`.
- Linux Arm64 manifest: `sha256:f182f45e9e71cbe737e6c394d68808c67a7bd12e2a4cc47c4aba864de77ecdc6`.
- Manifest architecture, pulled image architecture and running binary version verified
  on the Oracle VM before graph construction.
- Source: [official package](https://github.com/valhalla/valhalla/pkgs/container/valhalla-scripted)
  and [container documentation](https://github.com/valhalla/valhalla/blob/3.9.1/docker/README.md).
- Compose binding: `127.0.0.1:8002:8002`. No Oracle ingress rule was added.
- Build capped at 9 GiB RAM and 1.5 CPUs; two builder/server threads. Automatic
  restart disabled during the initial build, then changed to `unless-stopped` after
  successful benchmarks. Existing API and fuel timers remained running.

## Graph build

Great Britain extract downloaded from Geofabrik on 11 October 2026. Northern
Ireland is outside this graph. PBF SHA-256:
`35630d2312384a0b288ec7055bf83579dce24b42b556b6892e58239df038362b`.
Default speed configuration SHA-256:
`9953f78c2407b8d2e3701fce6cc93c6a91b207a365a5d2f5564a6d1c231e5ba7`.

The first build took approximately 39 minutes including download and packaging,
finishing at 12:55 UTC (13:55 BST). It produced 1,297 tiles in a 2.6 GiB archive.
The cgroup memory peak reached the 9 GiB cap, which reclaimed filesystem cache;
`oom` and `oom_kill` both remained zero. No Mac fallback was needed. Scheduled
12:30 and 12:45 UTC fuel refreshes completed during construction.

The builder reported ferry-connection reclassification warnings. Ferry/island
quality remains unverified; Tank Bear excludes ferry/toll comparisons. Elevation
and traffic are disabled, so missing elevation/traffic warnings are expected.

## Measured journeys

`backend/scripts/benchmark-routing.ts` ran on the VM against live SQLite station
prices and Valhalla, without the application's route cache. These are public
sample town centres, not user journeys. Each ran three times; **every run made
13 real routing calls: one baseline and 12 station routes**, with no routing
failures or partial coverage.

| Journey | First search | Repeat searches | Baseline distance | Ranked candidates |
| --- | ---: | ---: | ---: | ---: |
| Reading–Oxford | 886 ms | 453 / 431 ms | 42.376 km | 12 |
| Manchester–Leeds | 1,622 ms | 1,401 / 1,391 ms | 69.930 km | 11 |
| Cardiff–Bristol | 703 ms | 610 / 622 ms | 69.812 km | 12 |
| Glasgow–Edinburgh | 775 ms | 637 / 659 ms | 75.055 km | 12 |

Manchester's twelfth station was routed but excluded from ranking by the existing
`negative_route_difference` rule: distance was 1 metre shorter than baseline,
with no duration difference. No difference was silently clamped.

After measurements, configured `ROUTING_GRAPH_VERSION` as
`gb-20261011-35630d231238-valhalla3.9.1` and restarted only the API.
An authenticated Manchester–Leeds request through public HTTPS returned 200 in
1,556 ms, with 13 routing calls and the expected graph version.

The service reused the saved tile archive after a container restart and returned
200 from `/status` within **12.4 seconds**, including container shutdown time.
It did not rebuild. This check retained OS page caches; it is not a full VM reboot
or an empty-cache benchmark. App launches do not restart the routing service.
Port 8002 remained unreachable from the developer's Mac, and Docker confirmed
its only published binding was `127.0.0.1:8002`.

## Update policy and limits

Keep this graph pinned during the initial week of price collection. Future graph
updates are manual: build a separate directory from a recorded extract and image,
benchmark it, then switch the service and graph-version label together. Retain the
previous graph for rollback. Do not erase the active graph to initiate an update.
No automatic graph-update job is enabled.

These are single-client sample measurements, not a national route-quality or
concurrent-load guarantee. Divided-road/forecourt snapping tests, Northern Ireland,
proxy error-log redaction, off-machine backup and full restore rehearsal remain
release gates. `/status` describes feed health, not routing readiness.

## Android preparation

The default server address now points to `https://tankbear.duckdns.org`.
Existing saved addresses remain user-controlled; the app key still uses the
existing local settings field. No credentials are included in the APK or source.
Debug build, all 19 JVM unit tests and lint passed. All five existing instrumentation
tests passed on a Samsung SM-S938B running Android 17 after reconnecting USB.
The opt-in `LiveServerTest` also passed live Nearby over HTTPS from that phone.
It uses a public Reading demonstration point, no device location, and reads a
privately provisioned key from app-private `files/live-test-key`, deleting that
file immediately. No key is stored in test arguments, source or build artifacts.
The full Nearby plus Reading–Oxford journey variant then passed against live HTTPS
in 0.934 seconds of test execution. With explicit `provisionApp=true`, the test
saved the validated hostname and app key in private app preferences; car settings
were preserved. The temporary key file was confirmed absent afterward.
This does not establish a mobile-data-only run or permission-denial coverage.
