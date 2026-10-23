package com.dropcore.reservation.api;

import java.net.URI;
import java.util.UUID;

import com.dropcore.reservation.application.HoldResult;
import com.dropcore.reservation.application.ReservationService;
import jakarta.validation.constraints.Size;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/v1/events/{eventId}/reservations")
public class ReservationController {
    private final ReservationService service;

    public ReservationController(ReservationService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<CreateHoldResponse> createHold(
            @PathVariable UUID eventId,
            @RequestHeader("X-Demo-Customer-Id") UUID customerId,
            @RequestHeader("Idempotency-Key") @Size(min = 1, max = 128) String idempotencyKey) {
        HoldResult result = service.createHold(eventId, customerId, idempotencyKey);
        CreateHoldResponse response = new CreateHoldResponse(
                result.reservationId(), result.eventId(), "HELD", result.expiresAt());
        if (result.replay()) {
            return ResponseEntity.ok(response);
        }
        return ResponseEntity.created(URI.create("/v1/reservations/" + result.reservationId()))
                .body(response);
    }
}
