package com.dropcore.reservation.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import com.dropcore.reservation.infrastructure.ReservationRepository;
import com.dropcore.reservation.infrastructure.ReservationRepository.StoredRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;

class ReservationServiceTest {
    private static final UUID EVENT_ID = UUID.fromString("00000000-0000-4000-8000-000000000001");
    private static final UUID CUSTOMER_ID = UUID.fromString("00000000-0000-4000-8000-000000000099");
    private static final UUID RESERVATION_ID = UUID.fromString("10000000-0000-4000-8000-000000000001");
    private static final OffsetDateTime EXPIRES_AT = OffsetDateTime.of(
            2026, 10, 22, 20, 5, 0, 0, ZoneOffset.UTC);

    private FakeReservationRepository repository;
    private RecordingTransactionManager transactions;
    private ReservationService service;

    @BeforeEach
    void setUp() {
        repository = new FakeReservationRepository();
        transactions = new RecordingTransactionManager();
        service = new ReservationService(repository, transactions, Duration.ofMinutes(5));
    }

    @Test
    void createsOneHoldAndFinalizesTheRequestInTheSameTransaction() {
        HoldResult result = service.createHold(EVENT_ID, CUSTOMER_ID, "key-1");

        assertThat(result.eventId()).isEqualTo(EVENT_ID);
        assertThat(result.expiresAt()).isEqualTo(EXPIRES_AT);
        assertThat(result.replay()).isFalse();
        assertThat(repository.completedOutcome).isEqualTo("RESERVED");
        assertThat(repository.completedReservationId).isEqualTo(result.reservationId());
        assertThat(transactions.commits).isEqualTo(1);
        assertThat(transactions.rollbacks).isZero();
    }

    @Test
    void replaysTheOriginalSuccessfulSnapshot() {
        repository.ownsRequest = false;
        repository.eventOnSale = false;
        repository.storedRequest = new StoredRequest(
                ReservationService.fingerprint(EVENT_ID, CUSTOMER_ID),
                "RESERVED",
                RESERVATION_ID,
                EXPIRES_AT);

        HoldResult result = service.createHold(EVENT_ID, CUSTOMER_ID, "same-key");

        assertThat(result).isEqualTo(new HoldResult(RESERVATION_ID, EVENT_ID, EXPIRES_AT, true));
        assertThat(repository.inventoryClaimAttempted).isFalse();
    }

    @Test
    void returnsNotFoundBeforeInsertingAnIdempotencyRowForAnUnknownEvent() {
        repository.eventExists = false;

        assertThatThrownBy(() -> service.createHold(EVENT_ID, CUSTOMER_ID, "unknown-event"))
                .isInstanceOf(ReservationException.class)
                .satisfies(exception -> {
                    ReservationException reservationException = (ReservationException) exception;
                    assertThat(reservationException.status().value()).isEqualTo(404);
                    assertThat(reservationException.code()).isEqualTo("EVENT_NOT_FOUND");
                });

        assertThat(repository.requestClaimAttempted).isFalse();
        assertThat(transactions.commits).isZero();
        assertThat(transactions.rollbacks).isEqualTo(1);
    }

    @Test
    void commitsAndReplaysSoldOutAsABusinessOutcome() {
        repository.inventoryAvailable = false;

        assertThatThrownBy(() -> service.createHold(EVENT_ID, CUSTOMER_ID, "sold-out"))
                .isInstanceOf(ReservationException.class)
                .extracting(exception -> ((ReservationException) exception).code())
                .isEqualTo("SOLD_OUT");

        assertThat(repository.reservationDeleted).isTrue();
        assertThat(repository.completedOutcome).isEqualTo("SOLD_OUT");
        assertThat(transactions.commits).isEqualTo(1);
        assertThat(transactions.rollbacks).isZero();
    }

    @Test
    void rollsBackTheClaimedKeyWhenTheEventIsNotOnSale() {
        repository.eventOnSale = false;

        assertThatThrownBy(() -> service.createHold(EVENT_ID, CUSTOMER_ID, "closed"))
                .isInstanceOf(ReservationException.class)
                .extracting(exception -> ((ReservationException) exception).code())
                .isEqualTo("EVENT_NOT_ON_SALE");

        assertThat(transactions.commits).isZero();
        assertThat(transactions.rollbacks).isEqualTo(1);
    }

    private static final class FakeReservationRepository extends ReservationRepository {
        private boolean eventExists = true;
        private boolean ownsRequest = true;
        private boolean eventOnSale = true;
        private boolean requestClaimAttempted;
        private boolean inventoryAvailable = true;
        private boolean inventoryClaimAttempted;
        private boolean reservationDeleted;
        private String completedOutcome;
        private UUID completedReservationId;
        private StoredRequest storedRequest;

        private FakeReservationRepository() {
            super(null);
        }

        @Override
        public boolean claimRequest(UUID eventId, UUID customerId, String key, String fingerprint) {
            requestClaimAttempted = true;
            return ownsRequest;
        }

        @Override
        public Optional<StoredRequest> findRequest(UUID eventId, UUID customerId, String key) {
            return Optional.ofNullable(storedRequest);
        }

        @Override
        public Optional<Boolean> lockAndCheckEventIsOnSale(UUID eventId) {
            return eventExists ? Optional.of(eventOnSale) : Optional.empty();
        }

        @Override
        public Optional<OffsetDateTime> insertHold(
                UUID reservationId, UUID eventId, UUID customerId, long holdDurationSeconds) {
            return Optional.of(EXPIRES_AT);
        }

        @Override
        public boolean claimInventory(UUID eventId) {
            inventoryClaimAttempted = true;
            return inventoryAvailable;
        }

        @Override
        public void deleteReservation(UUID reservationId) {
            reservationDeleted = true;
        }

        @Override
        public void completeRequest(
                UUID eventId, UUID customerId, String key, String outcome, UUID reservationId) {
            completedOutcome = outcome;
            completedReservationId = reservationId;
        }
    }

    private static final class RecordingTransactionManager extends AbstractPlatformTransactionManager {
        private int commits;
        private int rollbacks;

        @Override
        protected Object doGetTransaction() {
            return new Object();
        }

        @Override
        protected void doBegin(Object transaction, TransactionDefinition definition) {
        }

        @Override
        protected void doCommit(DefaultTransactionStatus status) {
            commits++;
        }

        @Override
        protected void doRollback(DefaultTransactionStatus status) {
            rollbacks++;
        }
    }
}
