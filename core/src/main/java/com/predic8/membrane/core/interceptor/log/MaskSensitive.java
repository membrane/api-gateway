package com.predic8.membrane.core.interceptor.log;

import com.predic8.membrane.core.http.Header;
import com.predic8.membrane.core.http.HeaderField;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.predic8.membrane.core.http.Header.*;

public class MaskSensitive {

    private static final Set<String> SENSITIVE_HEADER_NAMES = Stream.of(
                    AUTHORIZATION,
                    PROXY_AUTHORIZATION,
                    COOKIE,
                    SET_COOKIE
            )
            .map(h -> h.toLowerCase(Locale.ROOT))
            .collect(Collectors.toCollection(HashSet::new));

    static {
        String[] baseKeys = {"Api-Key", "ApiToken", "Auth-Token"};
        for (String base : baseKeys) {
            SENSITIVE_HEADER_NAMES.add(base.toLowerCase(Locale.ROOT));
            SENSITIVE_HEADER_NAMES.add(("X-" + base).toLowerCase(Locale.ROOT));
            SENSITIVE_HEADER_NAMES.add(base.replace("-", "").toLowerCase(Locale.ROOT));
        }
    }

    public static String mask(Header header) {
        StringBuilder sb = new StringBuilder();
        for (HeaderField headerField : header.getAllHeaderFields()) {
            String fieldName = headerField.getHeaderName().toString();
            if (SENSITIVE_HEADER_NAMES.contains(fieldName.toLowerCase(Locale.ROOT))) {
                sb.append(fieldName).append(": ").append("*".repeat(headerField.getValue().length())).append("\n");
            } else {
                sb.append(headerField).append("\n");
            }
        }
        return sb.toString();
    }
}
