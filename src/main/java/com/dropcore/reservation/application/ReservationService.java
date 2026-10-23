package com.dropcore.reservation.application;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Objects;
import java.util.UUID;

import com.dropcore.reservation.infrastructure.ReservationRepository;
import com.dropcore.reservation.infrastructure.ReservationRepository.StoredRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class ReservationService {
    private final ReservationRepository repository;
    private final Duration holdDuration;
    private final TransactionTemplate transaction;

    public ReservationService(
            ReservationRepository repository,
            PlatformTransactionManager transactionManager,
            @Value("${dropcore.reservations.hold-duration:PT5M}") Duration holdDuration) {
        if (holdDuration.isZero() || holdDuration.isNegative()) {
            throw new IllegalArgumentException("Hold duration must be positive");
        }
        this.repository = repository;
        this.holdDuration = holdDuration;
        this.transaction = new TransactionTemplate(transactionManager);
    }

    public HoldResult createHold(UUID eventId, UUID customerId, String idempotencyKey) {
        Attempt attempt = Objects.requireNonNull(transaction.execute(
                status -> attemptHold(eventId, customerId, idempotencyKey)));
        if (attempt.failureCode() != null) {
            throw conflict(attempt.failureCode(), attempt.failureMessage());
        }
        return attempt.result();
    }

    private Attempt attemptHold(UUID eventId, UUID customerId, String idempotencyKey) {
        // reservation_requests has an event foreign key, so establish existence before
        // trying to claim the idempotency key. Keep the event row locked for the rest
        // of this allocation transaction.
        boolean onSale = repository.lockAndCheckEventIsOnSale(eventId)
                .orElseThrow(() -> new ReservationException(
                        HttpStatus.NOT_FOUND, "EVENT_NOT_FOUND", "Event was not found"));

        String fingerprint = fingerprint(eventId, customerId);
        boolean ownsRequest = repository.claimRequest(eventId, customerId, idempotencyKey, fingerprint);

        if (!ownsRequest) {
            StoredRequest stored = repository.findRequest(eventId, customerId, idempotencyKey)
                    .orElseThrow(() -> new IllegalStateException("Conflicting request disappeared"));
            if (!stored.fingerprint().equals(fingerprint)) {
                throw conflict("IDEMPOTENCY_KEY_REUSED", "Idempotency key was used for another request");
            }
            return replay(eventId, stored);
        }

        if (!onSale) {
            throw conflict("EVENT_NOT_ON_SALE", "Event is not open for reservations");
        }

        UUID reservationId = UUID.randomUUID();
        var expiresAt = repository.insertHold(
                reservationId, eventId, customerId, holdDuration.toSeconds());
        if (expiresAt.isEmpty()) {
            repository.completeRequest(
                    eventId, customerId, idempotencyKey, "ACTIVE_RESERVATION_EXISTS", null);
            return Attempt.failure(
                    "ACTIVE_RESERVATION_EXISTS", "Customer already has an active reservation");
        }

        if (!repository.claimInventory(eventId)) {
            repository.deleteReservation(reservationId);
            repository.completeRequest(eventId, customerId, idempotencyKey, "SOLD_OUT", null);
            return Attempt.failure("SOLD_OUT", "No tickets are available");
        }

        repository.completeRequest(eventId, customerId, idempotencyKey, "RESERVED", reservationId);
        return Attempt.success(new HoldResult(
                reservationId, eventId, expiresAt.orElseThrow(), false));
    }

    private Attempt replay(UUID eventId, StoredRequest stored) {
        return switch (stored.outcome()) {
            case "RESERVED" -> Attempt.success(new HoldResult(
                    stored.reservationId(), eventId, stored.expiresAt(), true));
            case "SOLD_OUT" -> Attempt.failure("SOLD_OUT", "No tickets are available");
            case "ACTIVE_RESERVATION_EXISTS" -> Attempt.failure(
                    "ACTIVE_RESERVATION_EXISTS", "Customer already has an active reservation");
            case "IN_PROGRESS" -> throw new IllegalStateException("Request remained in progress");
            default -> throw new IllegalStateException("Unknown stored outcome: " + stored.outcome());
        };
    }

    private ReservationException conflict(String code, String message) {
        return new ReservationException(HttpStatus.CONFLICT, code, message);
    }

    static String fingerprint(UUID eventId, UUID customerId) {
        String canonicalRequest = "create-hold:v1:" + eventId + ":" + customerId + ":quantity=1";
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(canonicalRequest.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private record Attempt(HoldResult result, String failureCode, String failureMessage) {
        private static Attempt success(HoldResult result) {
            return new Attempt(result, null, null);
        }

        private static Attempt failure(String code, String message) {
            return new Attempt(null, code, message);
        }
    }
}
