package cn.ybdata.core.config;

import java.sql.SQLException;
import java.util.Map;
import java.util.NoSuchElementException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.TypeMismatchException;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

/**
 * Every API error answers {@code {"error": "<中文说明>"}}. Request problems are 4xx with a message meant for
 * the user; anything unexpected is a 500 with a generic message — Java exception text is logged, never sent.
 */
@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(NoSuchElementException.class)
    ResponseEntity<Map<String, String>> notFound(NoSuchElementException e) {
        return body(HttpStatus.NOT_FOUND, e.getMessage() == null ? "未找到" : e.getMessage());
    }

    @ExceptionHandler(NumberFormatException.class)
    ResponseEntity<Map<String, String>> badNumber(NumberFormatException e) {
        return body(HttpStatus.BAD_REQUEST, "参数格式不正确");
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<Map<String, String>> badRequest(IllegalArgumentException e) {
        return body(HttpStatus.BAD_REQUEST, e.getMessage() == null ? "请求参数有误" : e.getMessage());
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<Map<String, String>> unreadable(HttpMessageNotReadableException e) {
        return body(HttpStatus.BAD_REQUEST, "请求体不是合法的 JSON");
    }

    @ExceptionHandler(TypeMismatchException.class)
    ResponseEntity<Map<String, String>> typeMismatch(TypeMismatchException e) {
        return body(HttpStatus.BAD_REQUEST, "参数格式不正确" + (e.getPropertyName() == null ? "" : ": " + e.getPropertyName()));
    }

    /** SQL state class 22 (data exception: value too long, invalid character …) / 23 (constraint) are the caller's doing. */
    @ExceptionHandler(DataAccessException.class)
    ResponseEntity<Map<String, String>> data(DataAccessException e) {
        String state = sqlState(e);
        if (state != null && (state.startsWith("22") || state.startsWith("23"))) {
            log.info("rejected data ({}): {}", state, e.getMostSpecificCause().getMessage());
            return body(HttpStatus.BAD_REQUEST, "提交的数据不符合要求(字段过长、格式不正确或与现有数据冲突)");
        }
        log.error("database error", e);
        return body(HttpStatus.INTERNAL_SERVER_ERROR, "服务器内部错误,请稍后再试");
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<Map<String, String>> other(Exception e) {
        if (e instanceof ResponseStatusException r && r.getReason() != null) return body(r.getStatusCode(), r.getReason());
        if (e instanceof ErrorResponse r) return body(r.getStatusCode(), generic(r.getStatusCode()));
        log.error("unhandled API error", e);
        return body(HttpStatus.INTERNAL_SERVER_ERROR, "服务器内部错误,请稍后再试");
    }

    private static String generic(HttpStatusCode s) {
        return switch (s.value()) {
            case 400 -> "请求参数有误";
            case 404 -> "接口不存在";
            case 405 -> "不支持的请求方法";
            case 406, 415 -> "不支持的数据格式";
            case 413 -> "请求体过大";
            default -> s.is5xxServerError() ? "服务器内部错误,请稍后再试" : "请求无法处理";
        };
    }

    private static String sqlState(Throwable e) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (t instanceof SQLException s && s.getSQLState() != null) return s.getSQLState();
        }
        return null;
    }

    private static ResponseEntity<Map<String, String>> body(HttpStatusCode status, String message) {
        return ResponseEntity.status(status).body(Map.of("error", message));
    }
}
