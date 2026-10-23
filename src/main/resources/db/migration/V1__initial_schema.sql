-- One event = one general-admission pool. UUIDs are generated in the Java application.
CREATE TABLE events (
    id UUID PRIMARY KEY,
    title VARCHAR(200) NOT NULL,
    status VARCHAR(16) NOT NULL CHECK (status IN ('DRAFT', 'OPEN', 'CLOSED')),
    sales_start_at TIMESTAMPTZ NOT NULL,
    sales_end_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    CHECK (sales_end_at > sales_start_at)
);

CREATE TABLE event_inventory (
    event_id UUID PRIMARY KEY REFERENCES events(id),
    total INTEGER NOT NULL CHECK (total >= 0),
    available INTEGER NOT NULL CHECK (available >= 0),
    held INTEGER NOT NULL CHECK (held >= 0),
    sold INTEGER NOT NULL CHECK (sold >= 0),
    CONSTRAINT inventory_balances CHECK (total = available + held + sold)
);

CREATE TABLE reservations (
    id UUID PRIMARY KEY,
    event_id UUID NOT NULL REFERENCES events(id),
    customer_id UUID NOT NULL,
    status VARCHAR(16) NOT NULL CHECK (status IN ('HELD', 'CONFIRMED', 'EXPIRED', 'CANCELLED')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    expires_at TIMESTAMPTZ NOT NULL,
    confirmed_at TIMESTAMPTZ,
    CHECK (expires_at > created_at),
    CHECK ((status = 'CONFIRMED') = (confirmed_at IS NOT NULL))
);

-- A customer can never hold or own two tickets for the same event.
CREATE UNIQUE INDEX uq_one_active_or_confirmed_reservation
ON reservations(event_id, customer_id)
WHERE status IN ('HELD', 'CONFIRMED');

CREATE INDEX ix_held_reservations_expiration
ON reservations(expires_at, id)
WHERE status = 'HELD';

CREATE TABLE reservation_requests (
    event_id UUID NOT NULL REFERENCES events(id),
    customer_id UUID NOT NULL,
    idempotency_key VARCHAR(128) NOT NULL,
    -- Distinguish the same key used with different inputs.
    request_fingerprint VARCHAR(64) NOT NULL,
    outcome VARCHAR(32) NOT NULL
      CHECK (outcome IN ('IN_PROGRESS', 'RESERVED', 'SOLD_OUT', 'ACTIVE_RESERVATION_EXISTS')),
    reservation_id UUID REFERENCES reservations(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    PRIMARY KEY (event_id, customer_id, idempotency_key),
    CHECK ((outcome = 'RESERVED') = (reservation_id IS NOT NULL))
);
