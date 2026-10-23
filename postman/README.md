# DropCore Postman tests

## Import

Import both files into Postman:

1. `DropCore.postman_collection.json`
2. `DropCore-Local.postman_environment.json`

Select **DropCore Local** from Postman's environment selector.

## Maintenance requirement

The collection is part of the API contract, not a one-time example. Every
endpoint addition or contract change must update the collection in the same
change, including:

- Request and folder organization.
- Required environment variables.
- Human-readable descriptions for headers, bodies, and behavior.
- Assertions for documented success and error responses.
- Replay assertions for idempotent operations.
- This guide when setup, fixtures, or execution order changes.

Validate the collection and environment as JSON before committing. Do not add
real credentials, tokens, customer data, or other secrets to either file.

## Start the application

From the repository root:

```bash
docker compose up -d
docker compose exec -T db psql -U dropcore -d dropcore < dev/seed.sql
mvn spring-boot:run
```

The environment targets `http://localhost:8080` and the event inserted by
`dev/seed.sql`.

## Run the tests

The simplest option is **Run collection**. Keep the collection's defined order.
The workflow does the following:

1. Checks application health.
2. Generates a fresh customer and idempotency keys.
3. Creates a hold and expects `201 Created`.
4. Replays the same key and expects `200 OK` with the original snapshot.
5. Uses a new key for the same customer and expects
   `409 ACTIVE_RESERVATION_EXISTS`.
6. Replays that finalized conflict.
7. Exercises header validation and an unknown event.

The first workflow request updates the environment's `customerId`,
`idempotencyKey`, `alternativeIdempotencyKey`, `reservationId`, and
`originalExpiresAt` values. This is intentional and makes each collection run
independent of previous holds.

## Testing `SOLD_OUT`

`SOLD_OUT` is not part of the automatic workflow because the seeded event has
100 tickets and the API does not yet provide event administration. Use a
separate zero-capacity fixture, change the environment's `eventId` to that
event, and send **1 - Create a new hold**. The expected response is:

```json
{
  "code": "SOLD_OUT",
  "message": "No tickets are available"
}
```

Repeating the same request with the same key must return the same finalized
`409` outcome even if capacity is later added.
