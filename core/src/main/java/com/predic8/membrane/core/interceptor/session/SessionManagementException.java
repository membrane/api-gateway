package com.predic8.membrane.core.interceptor.session;

public class SessionManagementException extends RuntimeException {
    public SessionManagementException(Exception cause) {
        super(getMessageFromCause(cause));
    }

    private static String getMessageFromCause(Throwable e) {
        var cause = getInnermostCause(e);
        return "%s: %s".formatted(cause.getClass().getSimpleName(), cause.getMessage());
    }

    private static Throwable getInnermostCause(Throwable e) {
        if (e.getCause() == null || e.getCause() == e) {
            return e;
        }
        return getInnermostCause(e.getCause());
    }
}
