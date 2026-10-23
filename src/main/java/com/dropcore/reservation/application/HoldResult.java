package com.dropcore.reservation.application;

import java.time.OffsetDateTime;
import java.util.UUID;

public record HoldResult(
        UUID reservationId,
        UUID eventId,
        OffsetDateTime expiresAt,
        boolean replay) {
}
