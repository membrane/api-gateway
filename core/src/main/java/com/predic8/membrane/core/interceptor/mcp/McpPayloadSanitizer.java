package com.predic8.membrane.core.interceptor.mcp;

import com.predic8.membrane.core.http.Header;
import com.predic8.membrane.core.http.HeaderField;
import com.predic8.membrane.core.http.Message;
import com.predic8.membrane.core.http.MimeType;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import static com.predic8.membrane.core.http.Header.*;
import static com.predic8.membrane.core.http.Header.PROXY_AUTHORIZATION;
import static java.util.Locale.ROOT;

public final class McpPayloadSanitizer {

    private static final Set<String> SENSITIVE_HEADERS = Set.of(
            AUTHORIZATION.toLowerCase(ROOT),
            COOKIE.toLowerCase(ROOT),
            SET_COOKIE.toLowerCase(ROOT),
            PROXY_AUTHORIZATION.toLowerCase(ROOT)
    );

    private static final String REDACTED = "<redacted>";
    private static final String BINARY_BODY_OMITTED = "<binary body omitted>";
    private static final String BODY_UNAVAILABLE = "<body unavailable>";
    private static final int MAX_BODY_LENGTH = 8 * 1024;

    public Map<String, Object> sanitizeHeaders(Header header) {
        var sanitized = new LinkedHashMap<String, Object>();
        if (header == null) {
            return sanitized;
        }

        for (HeaderField field : header.getAllHeaderFields()) {
            String name = field.getHeaderName().toString();

            sanitized.merge(name, redactIfSensitive(field, name), (previous, current) -> previous + ", " + current);
        }

        return sanitized;
    }

    private static String redactIfSensitive(HeaderField field, String name) {
        return SENSITIVE_HEADERS.contains(name.toLowerCase(ROOT)) ? REDACTED : field.getValue();
    }

    public String sanitizeBody(Message message) {
        if (message == null) {
            return BODY_UNAVAILABLE;
        }

        try {
            if (message.isBodyEmpty()) {
                return "";
            }

            // TODO: keep this?
            String contentType = message.getHeader().getContentType();
            if (contentType != null && !(MimeType.isText(contentType) || MimeType.isJson(contentType) || MimeType.isXML(contentType))) {
                return BINARY_BODY_OMITTED;
            }

            String body = message.getBodyAsStringDecoded();
            if (body.length() <= MAX_BODY_LENGTH) {
                return body;
            }
            return body.substring(0, MAX_BODY_LENGTH) + "... <truncated>";
        } catch (Exception e) {
            return BODY_UNAVAILABLE;
        }
    }
}
