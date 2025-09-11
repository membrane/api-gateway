package com.predic8.membrane.core.interceptor.log;

import com.predic8.membrane.core.http.*;

import java.util.*;
import java.util.stream.*;

import static com.predic8.membrane.core.http.Header.*;

/**
 * Utility to redact secret-bearing headers from logs.
 *
 * <p>Applies a mask (asterisks) to values of headers considered sensitive.
 * The default set typically includes:
 * <ul>
 *   <li>{@code Authorization}, {@code Proxy-Authorization}</li>
 *   <li>{@code Cookie}, {@code Set-Cookie}</li>
 *   <li>{@code X-API-Key}, {@code Api-Key}, {@code X-Auth-Token}</li>
 *   <li>{@code WWW-Authenticate} challenges that might reflect tokens</li>
 * </ul>
 * The set may be extended in future versions.
 * </p>
 *
 * <p>Example:</p>
 * <pre>{@code
 * Header h = new Header();
 * h.add("Authorization", "Bearer eyJhbGciOi...");
 * Header masked = filter.getMaskedHeader(h);
 * // Authorization: ********************
 * }</pre>
 *
 * <p>Design notes:
 * <ul>
 *   <li>Redaction is length-preserving</li>
 *   <li>Non-sensitive headers are left untouched.</li>
 * </ul>
 *
 * @author predic8
 * @since 6.x
 */
public class SensitiveDataFilter {

    /**
     * Names of headers whose values must be masked.
     */
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

    public Header getMaskedHeader(Header header) {
        Header masked = new Header(header);
        for (HeaderField headerField : masked.getAllHeaderFields()) {
            if (sensitiveHeaderNames.contains(headerField.getHeaderName())) {
                headerField.setValue(maskField(headerField));
            }
        }
        return masked;
    }

    private String maskField(HeaderField headerField) {
        return "*".repeat(headerField.getValue().length());
    }
}
