package com.predic8.membrane.common;

public class ExceptionUtil {

    public static Throwable getRootCause(Throwable t) {
        if (t == null)
            return null;
        Throwable cause = t;
        while (cause.getCause() != null && cause.getCause() != cause) {
            cause = cause.getCause();
        }
        return cause;
    }
}
