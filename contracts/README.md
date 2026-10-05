# Shared contracts

The implemented HTTP contract defines only health.
`fixtures/health-skeleton.json` is checked by the backend tests.

Stage 2 adds normalized domain fixtures (not HTTP or official feed schemas):

- `economics-cash-saving.json`: exactly 150p pump saving minus 80p detour fuel.
- `economics-route-ranking.json`: cheapest pump loses on routed overall cost.

Both are exercised by backend tests. `npm run demo:economics` runs the second
fixture locally. The optimisation request/response remains a proposal until
Stage 5; no generated clients exist yet.

Future Android, iOS, and web clients consume the same versioned API and economic
fixtures. They do not independently implement the authoritative ranking engine.
