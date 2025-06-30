package com.predic8.membrane.core.interceptor.dlp;

import com.predic8.membrane.core.http.Message;

import java.util.Locale;
import java.util.regex.Pattern;

public interface FieldActionStrategy {
    void apply(Message msg, Pattern pattern);

    static FieldActionStrategy of(String action) {
        return switch (action.toLowerCase(Locale.ROOT)) {
            case "mask" -> new MaskField();
            case "filter" -> new FilterField();
            case "report" -> new ReportField();
            default -> throw new IllegalArgumentException("Unknown action: " + action);
        };
    }
}
