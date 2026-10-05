# Local Valhalla for the Stage 4 benchmark

Not started or downloaded by any test or script. Run it yourself when ready:

```bash
cd tools/valhalla
docker compose up -d
docker compose logs -f valhalla   # wait for the service to listen on 8002
```

The first run downloads the Great Britain extract and builds the graph into
`tools/valhalla/data/` (ignored by Git). Later runs reuse it. To refresh OSM data,
delete `data/` or set `force_rebuild: "True"` once. Record the extract date and use it as
the adapter's graph version, because route responses do not carry one.

Then, from the repository root:

```bash
npm run smoke:routing --workspace backend -- 2026-10-05
```

The argument is the graph version label. The smoke script routes a few fixed Reading
to Oxford style journeys with the real adapter and prints distance, time, ferry/toll flags
and snap distances. It sends only those fixed coordinates to your local container.

Benchmark cases still to be checked by hand from `docs/routing.md`: opposite motorway
services, divided roads, rural stations, near-identical endpoints and ferries.
