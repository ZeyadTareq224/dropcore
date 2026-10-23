package com.dropcore.reservation.application;

import org.springframework.http.HttpStatus;

public final class ReservationException extends RuntimeException {
    private final String code;
    private final HttpStatus status;

    public ReservationException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public String code() {
        return code;
    }

    public HttpStatus status() {
        return status;
    }
}
