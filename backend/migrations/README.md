# Migrations

No application tables are needed in Stage 1. The health check runs `SELECT 1`
against local D1 without creating a station schema. Station, price, history,
and ingestion-state migrations arrive in Stage 3 after the official feed
contract is verified. No remote database exists for this skeleton.
