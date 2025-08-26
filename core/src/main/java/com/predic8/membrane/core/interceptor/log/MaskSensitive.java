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

    public static Header mask(Header header) {
        Header masked = new Header(header);
        for (HeaderField headerField : masked.getAllHeaderFields()) {
            if (SENSITIVE_HEADER_NAMES.contains(headerField.getHeaderName().toString().toLowerCase(Locale.ROOT))) {
                headerField.setValue("*".repeat(headerField.getValue().length()));
            }
            else {
                headerField.setValue(headerField.getValue());
            }
        }
        return masked;
    }
}
