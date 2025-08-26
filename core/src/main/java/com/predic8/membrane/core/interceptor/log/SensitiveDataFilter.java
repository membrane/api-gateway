package com.predic8.membrane.core.interceptor.log;

import com.predic8.membrane.core.http.Header;
import com.predic8.membrane.core.http.HeaderField;
import com.predic8.membrane.core.http.HeaderName;

import java.util.HashSet;
import java.util.Set;
import java.util.stream.Stream;

import static com.predic8.membrane.core.http.Header.*;

public class SensitiveDataFilter {

    private static final Set<HeaderName> SENSITIVE_HEADER_NAMES = new HashSet<>();

    static {
        Stream.of(AUTHORIZATION, PROXY_AUTHORIZATION, COOKIE, SET_COOKIE)
                .map(HeaderName::new)
                .forEach(SENSITIVE_HEADER_NAMES::add);

        String[] baseKeys = {"Api-Key", "ApiToken", "Auth-Token"};
        for (String base : baseKeys) {
            SENSITIVE_HEADER_NAMES.add(new HeaderName(base));
            SENSITIVE_HEADER_NAMES.add(new HeaderName("X-" + base));
            SENSITIVE_HEADER_NAMES.add(new HeaderName(base.replace("-", "")));
        }
    }

    public static Header mask(Header header) {
        Header masked = new Header(header);
        for (HeaderField headerField : masked.getAllHeaderFields()) {
            if (SENSITIVE_HEADER_NAMES.contains(headerField.getHeaderName())) {
                headerField.setValue("*".repeat(headerField.getValue().length()));
            }
        }
        return masked;
    }
}
