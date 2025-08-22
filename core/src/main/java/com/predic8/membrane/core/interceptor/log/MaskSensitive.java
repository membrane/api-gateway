package com.predic8.membrane.core.interceptor.log;

import com.predic8.membrane.core.http.Header;
import com.predic8.membrane.core.http.HeaderField;

import java.util.Set;

public class MaskSensitive {

    private static final Set<String> SENSITIVE_HEADER_NAMES = Set.of(
            Header.AUTHORIZATION,
            Header.PROXY_AUTHORIZATION,
            Header.COOKIE,
            Header.SET_COOKIE,
            "X-API-Key", "Api-Key", "ApiKey",
            "X-Api-Token", "X-Auth-Token", "Authorization-Token"
    );

    public static String mask(Header header) {
        StringBuilder sb = new StringBuilder();

        for (HeaderField headerField : header.getAllHeaderFields()) {
            String field = headerField.getHeaderName().toString();
            if (SENSITIVE_HEADER_NAMES.contains(field)) {
                sb.append(field).append(": *****\n");
            } else {
                sb.append(field).append(": ").append(headerField.getValue()).append("\n");
            }
        }

        return sb.toString().trim();
    }
}
