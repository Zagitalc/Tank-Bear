// Local only: exercises the Valhalla adapter against http://127.0.0.1:8002.
import { routeJourney } from "../src/routing/journey.ts";
import { createValhallaProvider } from "../src/routing/valhalla.ts";

const graphVersion = process.argv[2];
if (!graphVersion) {
  console.error("Usage: npm run smoke:routing --workspace backend -- <graph-version-label>");
  process.exit(2);
}
const provider = createValhallaProvider({ baseUrl: "http://127.0.0.1:8002", graphVersion, fetch, timeoutMs: 20_000 });

const reading = { lat: 51.4543, lon: -0.9781 };
const oxford = { lat: 51.752, lon: -1.2577 };
const stations = [
  { stationId: "near-route", coordinates: { lat: 51.5878, lon: -1.1196 } },
  { stationId: "off-route", coordinates: { lat: 51.4, lon: -1.3 } },
  { stationId: "sea", coordinates: { lat: 50.6, lon: -1.5 } },
];

const result = await routeJourney(provider, { mode: "along_journey", origin: reading, destination: oxford, stations });
console.log(JSON.stringify(result, (k, v) => (k === "geometry" ? `${String(v).length} chars` : v), 2));
