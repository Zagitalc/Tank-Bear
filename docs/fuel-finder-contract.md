# Fuel Finder contract and access gate

Reviewed 5 October 2026 in the local browser. Stage 3 is authorised but not
complete. Live credentials are configured in ignored `backend/.dev.vars`.
A bounded authentication/read-only smoke check passed. An in-memory ingestion adapter (client, pagination, validation, refresh
assessment) now exists with tests. No database migrations, cron or remote
infrastructure have been created.

## Official sources inspected

- [Public API access](https://www.developer.fuel-finder.service.gov.uk/fuel-finder/public-api)
- [Recipient endpoint reference](https://www.developer.fuel-finder.service.gov.uk/fuel-finder/apis-ifr/info-recipent/docs?operationId=getAllPFSFuelPrices)
- [Fields guide](https://www.developer.fuel-finder.service.gov.uk/fuel-finder/api-guide)
- [Detailed token reference](https://www.developer.fuel-finder.service.gov.uk/fuel-finder/apis-ifr/access-token/docs?operationId=generateAccessToken)
- [Authentication overview](https://www.developer.fuel-finder.service.gov.uk/fuel-finder/api-authentication)
- [Developer guidelines](https://www.developer.fuel-finder.service.gov.uk/fuel-finder/dev-guideline)

## Verified details

The recipient reference lists GET `/api/v1/pfs` for station details and GET
`/api/v1/pfs/fuel-prices` for prices. Both require `batch-number`, starting at
1. Station documentation specifies up to 500 forecourts per batch. The official
pages state no end-of-pagination signal; see the observed evidence below. A
failed batch must never be interpreted as completion.

Samples return arrays of forecourt objects joined by `node_id`. Station fields
include `trading_name`, `brand_name`, location/address/postcode/latitude/longitude,
amenities, opening times, fuel types, temporary/permanent closure and motorway/
supermarket flags. The inspected station sample does not expose operator or a
station update timestamp; do not invent either from the broader access guidance.
`temporary_closure` means closure for three days or more, not current opening status.

Price entries contain `fuel_type`, numeric `price`, `price_last_updated` and
`price_change_effective_timestamp`. The fields guide identifies price timestamps
as UTC ISO 8601/RFC 3339 with trailing Z. Preserve both timestamps plus local
successful observation time. Examples use decimal values such as 132.9, but the
inspected field description does not explicitly establish units or allowed
precision; confirm those before converting or persisting money.

The response samples use `E10`, `E5`, `B7_STANDARD` and `B7_PREMIUM`. Standard
diesel must be mapped to Tank Bear's internal `B7`, not assumed to arrive as `B7`.
The guide uses mixed-case spellings, so confirm the production enum contract.

The detailed token reference specifies JSON POST
`/api/v1/oauth/generate_access_token` with `client_id` and `client_secret`.
The sample response wraps tokens in `data`, with access expiry 3600 seconds and
refresh expiry 172800 seconds. Refresh uses JSON POST
`/api/v1/oauth/regenerate_access_token` with `client_id` and `refresh_token`.
Reuse valid tokens; never log tokens/secrets or return them to a client. Exact
the expanded operation panel identifies the production host as
`https://www.fuel-finder.service.gov.uk`. The live smoke check confirmed HTTP
200 and the documented token envelope. Sequential first-batch station and price
requests each returned HTTP 200, arrays and 500 records. Tokens remained in
memory; raw feed data was not saved or printed. The test host is unverified.
The Python TLS client failed locally; system curl succeeded with certificate
verification enabled.

[Official trader reporting guidance](https://www.gov.uk/guidance/fuel-finder/reporting-your-fuel-prices)
requires submitted prices in pence per litre and permits up to three decimal
places. Recipient representation/precision still needs confirmation.

Developer guidelines state 100 requests/minute and one concurrent request per
client in live mode. They recommend one-hour station, 15-minute price and
five-minute search caching. These are upstream feed limits; the provisional
routing concurrency is a separate service limit.

## Discrepancies and unresolved terms

- Authentication overview shows form-encoded OAuth and `/v1/prices`, whereas
  the detailed references specify JSON token requests and `/api/v1/pfs` paths.
  The live smoke check confirmed the detailed JSON reference works for these
  credentials; follow that reference rather than the generic overview.
- Incremental query schema describes YYYY-MM-DD, while the displayed endpoint
  includes YYYY-MM-DD HH:MM:SS. Timezone, inclusivity and update semantics remain
  unresolved. Prefer a validated full refresh initially once permitted rather
  than guessing an incremental cursor.
- Fields guide describes a JSON object but endpoint examples show arrays.
  Bank holiday field names and time precision also differ across pages.
- Developer guidelines prohibit raw API data redistribution, while page footers
  mention the Open Government Licence with exceptions. Clarify permission for
  backend caching, price history retention, derived recommendation responses
  with station prices, future clients and required attribution. Do not assume
  a footer overrides a specific restriction or that the proposed API is allowed.
- Confirm numeric price units/precision, required/null fields, coordinate CRS,
  full-feed completion, removal semantics and regional access for Workers.

## User access step

Open [the access page](https://www.developer.fuel-finder.service.gov.uk/fuel-finder/get-started-ifr/onelogin),
continue through GOV.UK One Login, and obtain Information Recipient application
access following the portal. Review the actual terms before acceptance. Ask the
Fuel Finder team to clarify the issues above using the portal's contact link;
no message has been sent on the user's behalf.

Do not paste credentials into chat. Once issued, configure them in an ignored
local backend secret file using the verified environment and variable names
specified by the eventual adapter. `backend/.dev.vars` is already ignored.

Next implementation patch, after this gate: official response validation,
precise price normalisation, D1 current/change persistence and refresh health,
then bounded nearby queries. Test failed pagination, malformed records,
duplicates, stale observations, out-of-order updates and unchanged-price history
before any live refresh. No routing is part of Stage 3.

## Remaining support questions after credential validation

Credentials have been obtained; no further registration is needed. The user
does not know whether onboarding terms covered the planned data use.
The [official contact page](https://www.developer.fuel-finder.service.gov.uk/fuel-finder/contact-us)
lists `fuel.finder@ve3.global` and `02038365818`. No message has been sent.

- May Tank Bear cache forecourt/prices server-side, retain observed price changes
  and display prices with derived routed savings in Android/iOS/web clients?
  How does this differ from prohibited raw API redistribution, and what
  attribution/retention terms apply?
- What is the documented end-of-pagination signal for both feeds, and is there
  snapshot consistency across batches?
- Confirm recipient price representation/precision, nullable fields, station
  update timestamps/operator availability and permanent closure/removal rules.
- Confirm incremental timestamp format/timezone/inclusivity. A validated full
  refresh may avoid this incremental ambiguity in the initial adapter.

The October 1, 2026 [release note](https://www.developer.fuel-finder.service.gov.uk/fuel-finder/release-notes)
confirms temporarily closed forecourts are included in recipient output.
They must not be treated as available just because they appear in the feed.

## Clarification after reviewing the developer guidelines link

The guidelines explicitly recommend caching station, price and search data.
Caching itself is therefore supported and must not be described as prohibited.
The restriction concerns redistribution of raw API data. Derived in-app
recommendations appear consistent with the stated application purpose, but that
is an interpretation, not explicit confirmation of history retention or a
public reusable feed. Do not publish a raw feed/export. A local ingestion adapter
still needs a verified pagination completion rule before it can mark a national
refresh complete; a 404 or arbitrary batch cap is not documented proof of success.

## Observed pagination evidence (not official documentation)

Observed 5 October 2026 around 11:50 to 12:00 UTC, **live** host
`https://www.fuel-finder.service.gov.uk`, issued client credentials, one
sequential request at a time about 1.5 s apart (well under 100 requests/minute).
Re-checked the recipient endpoint reference and developer guidelines the same
day: neither states how the last batch is signalled, nor any total or next-cursor
field. The only statement is "up to 500 forecourts" per batch. Everything in
this section is inference from the API's behaviour and could change without
notice.

| Endpoint | Batches 1 to 16 | Batch 17 | Batch 18 |
| --- | --- | --- | --- |
| `/api/v1/pfs` | 500 each | 126 | HTTP 404 |
| `/api/v1/pfs/fuel-prices` | 500 each | 122 | HTTP 404 |

- Totals were 8126 stations and 8122 priced stations, with no duplicate
  `node_id` within or across batches. Every priced `node_id` was a known
  station; four stations had no price entry.
- The 404 body is JSON of the form `success:false` with a nested message
  `Requested batch 18 is not available` and `error.code` 404.
- Responses were plain JSON arrays with `content-type: application/json`. There
  were no total, next-cursor, rate-limit or pagination headers, and no wrapper
  object. Not observed: a batch of exactly 500 followed by the end, a 429, or
  data changing between batches.
- Prices were JSON numbers between 100.9 and 299.9, almost all with one decimal
  place (a few with 0, 2 or 3), consistent with pence per litre. Fuel types seen:
  `E10`, `B7_STANDARD`, `E5`, `B7_PREMIUM`, `HVO`, `B10`. All timestamps were UTC
  with a trailing Z. 50 stations had `temporary_closure` true; none had
  `permanent_closure` true. All coordinates were inside the UK.
- Repeating the full run on the same day produced the same shape and counts.

### Rule adopted by the adapter

1. Primary (observed): a batch with fewer than 500 records is the final batch.
2. Secondary (never observed): a 404 "Requested batch N is not available" or an
   empty array after an exactly full batch ends the feed, but that run is held to
   a stricter total check, because a transient 404 would look identical.
3. Any other failure (401, 403, 429, 5xx, other 404, timeout, non-array body,
   oversize batch, no end within 100 batches, or an empty or missing batch 1)
   makes the run incomplete. Retries are limited to 5xx and token renewal; a 429
   stops the run.
4. A run is accepted as a complete nationwide refresh only if both feeds ended
   by rule 1 or 2, no `node_id` repeats, no more than 5% of records are
   malformed, and valid totals are at least 90% of the last accepted refresh
   (98% for rule 2; at least 1000 stations if there is none). These thresholds
   are provisional choices, not provider facts.
5. Stations absent from a complete run are reported as candidates only. Nothing
   is deleted automatically; closure comes from `permanent_closure`.
6. Failed or rejected runs leave current data untouched.

Still unconfirmed by the provider: official end signal, snapshot consistency
across batches, units and precision, nullability, and the data-use terms. The
questions above remain worth asking, but no longer block the adapter.

## Persistence and scheduling (local, not deployed)

`backend/src/ingestion/run.ts` runs one refresh under a lease, applies the rules
above, diffs against stored state and writes only changes. `scheduled` in
`src/index.ts` calls it when credentials are present, and `wrangler.jsonc` lists
a 15-minute cron that takes effect only if deployed. A full refresh is about 36
paced requests (roughly 30 seconds); Worker CPU and wall-time behaviour for that
run is untested. `GET /v1/stations/nearby` (`lat`, `lon`, optional
`radiusMetres` up to 25000, `limit` up to 50) returns closest-first stations with
prices, temporary-closure flags and feed health kept apart from price age.
Permanently closed stations are excluded. It makes no opening-hours or routing
claims. Tests run the real migration on SQLite through a D1 shim, not on D1 itself.

## Observed: User-Agent required (5 October 2026)

`POST /api/v1/oauth/generate_access_token` returned HTTP 403 for a request with no
`User-Agent` header (curl with the header removed, and a local Worker, whose `fetch`
sends none) and HTTP 200 with any non-empty value. Not documented by the provider.
The client now always sends `TankBear/0.1 (+https://github.com/Zagitalc/Tank-Bear)`.
Whether Cloudflare's production egress is accepted by the provider remains untested.
