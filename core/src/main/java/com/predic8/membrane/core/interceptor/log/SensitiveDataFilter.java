package com.predic8.membrane.core.interceptor.log;

import com.predic8.membrane.core.http.Header;
import com.predic8.membrane.core.http.HeaderField;
import com.predic8.membrane.core.http.HeaderName;

import java.util.HashSet;
import java.util.Set;
import java.util.stream.Stream;

import static com.predic8.membrane.core.http.Header.*;

public class SensitiveDataFilter {

    private final Set<HeaderName> sensitiveHeaderNames;

    public SensitiveDataFilter() {
        Set<HeaderName> names = new HashSet<>();

        Stream.of(AUTHORIZATION, PROXY_AUTHORIZATION, COOKIE, SET_COOKIE)
                .map(HeaderName::new)
                .forEach(names::add);

        String[] baseKeys = {"Api-Key", "ApiToken", "Auth-Token"};
        for (String base : baseKeys) {
            names.add(new HeaderName(base));
            names.add(new HeaderName("X-" + base));
            names.add(new HeaderName(base.replace("-", "")));
        }
        this.sensitiveHeaderNames = names;
    }

    public Header mask(Header header) {
        Header masked = new Header(header);
        for (HeaderField headerField : masked.getAllHeaderFields()) {
            if (sensitiveHeaderNames.contains(headerField.getHeaderName())) {
                headerField.setValue("*".repeat(headerField.getValue().length()));
            }
        }
        return masked;
    }
}
