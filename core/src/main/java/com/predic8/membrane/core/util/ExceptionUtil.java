package com.predic8.membrane.core.util;

public class ExceptionUtil {
    public static String concatMessageAndCauseMessages(Throwable throwable) {
        StringBuilder sb = new StringBuilder();
        do {
            sb.append(throwable.getMessage());
            throwable = throwable.getCause();
            if (throwable != null) {
                sb.append(" caused by: ");
            }
        } while (throwable != null);
        return sb.toString();
    }
}
