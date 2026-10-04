package gov.ybj.chsdpub.common;

import org.springframework.http.HttpStatus;

/** 业务异常：统一渲染为 {code, message}。 */
public class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final String code;

    public ApiException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public HttpStatus getStatus() { return status; }
    public String getCode() { return code; }

    public static ApiException unauthorized(String msg) { return new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", msg); }
    public static ApiException forbidden(String msg) { return new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", msg); }
    public static ApiException notFound(String msg) { return new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", msg); }
    public static ApiException validation(String msg) { return new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION", msg); }
    public static ApiException conflict(String msg) { return new ApiException(HttpStatus.CONFLICT, "CONFLICT", msg); }
    public static ApiException locked(String msg) { return new ApiException(HttpStatus.LOCKED, "LOCKED", msg); }
    public static ApiException engineUnavailable(String msg) { return new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "ENGINE_UNAVAILABLE", msg); }
}
