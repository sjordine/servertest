package com.servertest.products.common;

import org.springframework.http.HttpStatus;

/** A business error that maps directly to an HTTP status and a ProblemDetail response. */
public class ApiException extends RuntimeException {

    private final HttpStatus status;

    public ApiException(HttpStatus status, String detail) {
        super(detail);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public static ApiException notFound(String what, Object key) {
        return new ApiException(HttpStatus.NOT_FOUND, what + " '" + key + "' not found");
    }

    public static ApiException conflict(String detail) {
        return new ApiException(HttpStatus.CONFLICT, detail);
    }

    public static ApiException badRequest(String detail) {
        return new ApiException(HttpStatus.BAD_REQUEST, detail);
    }
}
