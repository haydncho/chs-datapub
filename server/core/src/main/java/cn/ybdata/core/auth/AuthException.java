package cn.ybdata.core.auth;

import org.springframework.http.HttpStatus;

/** Login / session failure with the HTTP status and a user-facing (Chinese) message. */
public class AuthException extends RuntimeException {

    private final HttpStatus status;
    private final Long retryAfterSeconds;

    public AuthException(HttpStatus status, String message) {
        this(status, message, null);
    }

    public AuthException(HttpStatus status, String message, Long retryAfterSeconds) {
        super(message);
        this.status = status;
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public HttpStatus status() {
        return status;
    }

    public Long retryAfterSeconds() {
        return retryAfterSeconds;
    }
}
