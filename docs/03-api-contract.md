# API Contract — Phase 1

All requests and responses are JSON. For v1, the identity header is for LOCAL TESTING ONLY; real users need authenticated identity later.

## Create a hold

`POST /v1/events/{eventId}/reservations`

Headers:

- `X-Demo-Customer-Id: <UUID>` (local-only; not trusted auth)
- `Idempotency-Key: <opaque string, 1..128 chars>`

No request body in v1; exactly one ticket is requested.

First successful request: HTTP **201**

```json
{
  "reservationId": "<uuid>",
  "eventId": "<uuid>",
  "status": "HELD",
  "expiresAt": "2026-10-22T20:05:00Z"
}
```

A successful replay of the **same key** yields HTTP **200** and the same reservation ID and original result snapshot (even if the reservation later changed state). A new key from a customer who already holds/owns a ticket yields HTTP **409** and `ACTIVE_RESERVATION_EXISTS`.

If tickets are sold out: HTTP **409** with `SOLD_OUT`.

A finalized 409 business outcome is also replayed for the same key even if capacity later changes. Treat transient 503/timeouts as retryable with the *same* key.

Errors are JSON: `{ "code": "SOLD_OUT", "message": "..." }`.

## Read reservation

`GET /v1/reservations/{reservationId}`

Local-only identity header required. Return status and deadline; reject reads from another demo customer. (Authenticate properly before production.)

## Mock confirmation (next increment)

`POST /v1/reservations/{reservationId}/confirm`

Used to model the payment success path. If and only if state is HELD and the DB deadline has not passed, atomically transition to CONFIRMED and update counters. A repeated confirmation returns the existing CONFIRMED state. Otherwise return conflict; no late confirmation.

## Expiration (background job)

Periodically claim eligible HELD reservations with `expires_at <= DB_clock`, atomically transition to EXPIRED, and release exactly one held inventory unit per transitioned row. Two sweepers must never both release a hold.

## Example first request

```bash
curl -i -X POST   http://localhost:8080/v1/events/00000000-0000-4000-8000-000000000001/reservations   -H 'X-Demo-Customer-Id: 00000000-0000-4000-8000-000000000099'   -H 'Idempotency-Key: attempt-001'
```

This example will return 404 until you implement the reservation endpoint.
