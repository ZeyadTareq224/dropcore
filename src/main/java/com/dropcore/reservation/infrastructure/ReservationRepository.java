package com.dropcore.reservation.infrastructure;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class ReservationRepository {
    private final JdbcClient jdbc;

    public ReservationRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public boolean claimRequest(UUID eventId, UUID customerId, String key, String fingerprint) {
        return jdbc.sql("""
                        INSERT INTO reservation_requests
                            (event_id, customer_id, idempotency_key, request_fingerprint, outcome)
                        VALUES (:event_id, :customer_id, :idempotency_key, :fingerprint, 'IN_PROGRESS')
                        ON CONFLICT (event_id, customer_id, idempotency_key) DO NOTHING
                        """)
                .params(requestParams(eventId, customerId, key))
                .param("fingerprint", fingerprint)
                .update() == 1;
    }

    public Optional<StoredRequest> findRequest(UUID eventId, UUID customerId, String key) {
        return jdbc.sql("""
                        SELECT rr.request_fingerprint, rr.outcome, rr.reservation_id, r.expires_at
                        FROM reservation_requests rr
                        LEFT JOIN reservations r ON r.id = rr.reservation_id
                        WHERE rr.event_id = :event_id
                          AND rr.customer_id = :customer_id
                          AND rr.idempotency_key = :idempotency_key
                        """)
                .params(requestParams(eventId, customerId, key))
                .query((rs, rowNum) -> new StoredRequest(
                        rs.getString("request_fingerprint"),
                        rs.getString("outcome"),
                        rs.getObject("reservation_id", UUID.class),
                        rs.getObject("expires_at", OffsetDateTime.class)))
                .optional();
    }

    public Optional<Boolean> lockAndCheckEventIsOnSale(UUID eventId) {
        return jdbc.sql("""
                        SELECT status = 'OPEN'
                               AND sales_start_at <= clock_timestamp()
                               AND clock_timestamp() < sales_end_at AS on_sale
                        FROM events
                        WHERE id = :event_id
                        FOR SHARE
                        """)
                .param("event_id", eventId)
                .query(Boolean.class)
                .optional();
    }

    public Optional<OffsetDateTime> insertHold(
            UUID reservationId, UUID eventId, UUID customerId, long holdDurationSeconds) {
        return jdbc.sql("""
                        INSERT INTO reservations (id, event_id, customer_id, status, expires_at)
                        VALUES (
                            :reservation_id,
                            :event_id,
                            :customer_id,
                            'HELD',
                            clock_timestamp() + (:hold_seconds * interval '1 second'))
                        ON CONFLICT (event_id, customer_id)
                            WHERE status IN ('HELD', 'CONFIRMED')
                            DO NOTHING
                        RETURNING expires_at
                        """)
                .param("reservation_id", reservationId)
                .param("event_id", eventId)
                .param("customer_id", customerId)
                .param("hold_seconds", holdDurationSeconds)
                .query(OffsetDateTime.class)
                .optional();
    }

    public boolean claimInventory(UUID eventId) {
        return jdbc.sql("""
                        UPDATE event_inventory
                        SET available = available - 1,
                            held = held + 1
                        WHERE event_id = :event_id
                          AND available > 0
                        """)
                .param("event_id", eventId)
                .update() == 1;
    }

    public void deleteReservation(UUID reservationId) {
        jdbc.sql("DELETE FROM reservations WHERE id = :reservation_id")
                .param("reservation_id", reservationId)
                .update();
    }

    public void completeRequest(
            UUID eventId, UUID customerId, String key, String outcome, UUID reservationId) {
        int updated = jdbc.sql("""
                        UPDATE reservation_requests
                        SET outcome = :outcome,
                            reservation_id = :reservation_id
                        WHERE event_id = :event_id
                          AND customer_id = :customer_id
                          AND idempotency_key = :idempotency_key
                          AND outcome = 'IN_PROGRESS'
                        """)
                .params(requestParams(eventId, customerId, key))
                .param("outcome", outcome)
                .param("reservation_id", reservationId)
                .update();
        if (updated != 1) {
            throw new IllegalStateException("Could not finalize the reservation request");
        }
    }

    private Map<String, ?> requestParams(UUID eventId, UUID customerId, String key) {
        return Map.of(
                "event_id", eventId,
                "customer_id", customerId,
                "idempotency_key", key);
    }

    public record StoredRequest(
            String fingerprint,
            String outcome,
            UUID reservationId,
            OffsetDateTime expiresAt) {
    }
}
