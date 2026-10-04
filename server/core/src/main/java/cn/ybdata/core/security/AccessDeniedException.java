package cn.ybdata.core.security;

/** The actor's identity may not open this page / perform this action (served as 403). */
public class AccessDeniedException extends RuntimeException {
    public AccessDeniedException(String message) {
        super(message);
    }
}
