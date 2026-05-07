package com.predic8.membrane.core.interceptor.ai;

import com.predic8.membrane.core.http.Header;

public class AiUtil {

    public static final String BEARER_PREFIX = "Bearer";

    private AiUtil() {}

    /**
     * Estimates the number of tokens in a given text.
     * The calculation assumes an average token length of 4 characters.
     * <p></p>
     * Content	Approximation
     * English prose	chars / 4
     * German/French	chars / 3.5
     * JSON/XML/code	chars / 2.5–3
     * Chinese/Japanese	very different
     * <p></p>
     * For API gateways, quotas, billing alerts, or rate limiting, approximate counting is often sufficient.
     * <p></p>
     * @param text the input string whose tokens are to be estimated
     * @return the estimated number of tokens, rounded up to the nearest integer
     */
    public static int estimateTokens(String text) {
        return (int) Math.ceil(text.length() / 4.0);
    }

    /**
     * Extracts the Bearer token from the Authorization header.
     * If the Authorization header is null or does not contain
     * a Bearer token, this method returns null.
     *
     * @param header the Header object from which the Authorization
     *               header is to be extracted
     * @return the Bearer token as a String if present; otherwise null
     */
    public static String extractBearerToken(Header header) {
        var ah = header.getAuthorization();
        if (ah == null) {
            return null;
        }

        int index = ah.indexOf(BEARER_PREFIX);
        if (index < 0) {
            return null;
        }

        var token = ah.substring(index + BEARER_PREFIX.length()).trim();

        return token.isEmpty() ? null : token;
    }

}
