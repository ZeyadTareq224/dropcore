package com.dropcore.reservation.api;

import java.time.OffsetDateTime;
import java.util.UUID;

public record CreateHoldResponse(
        UUID reservationId,
        UUID eventId,
        String status,
        OffsetDateTime expiresAt) {
}
