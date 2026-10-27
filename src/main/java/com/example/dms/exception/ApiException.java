package com.example.dms.exception;

import org.springframework.http.HttpStatus;

/** Business-rule failure that maps directly to an HTTP status. */
public class ApiException extends RuntimeException {

    private final HttpStatus status;

    public ApiException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public HttpStatus getStatus() { return status; }

    public static ApiException notFound(String m)        { return new ApiException(HttpStatus.NOT_FOUND, m); }
    public static ApiException badRequest(String m)      { return new ApiException(HttpStatus.BAD_REQUEST, m); }
    public static ApiException conflict(String m)        { return new ApiException(HttpStatus.CONFLICT, m); }
    public static ApiException unsupportedType(String m) { return new ApiException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, m); }
    public static ApiException internal(String m)        { return new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, m); }
}
