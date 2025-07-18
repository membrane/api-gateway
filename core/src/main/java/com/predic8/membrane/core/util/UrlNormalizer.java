package com.predic8.membrane.core.util;

import java.net.*;
import java.net.URI;

public class UrlNormalizer {

    /**
     * Normalizes a base URL by converting scheme and host to lowercase
     * and omitting default ports (80 for HTTP, 443 for HTTPS).
     *
     * @param url the URL string to normalize
     * @return the normalized base URL string
     * @throws URISyntaxException       if the URL is malformed
     * @throws IllegalArgumentException if the URL is null
     */
    public static String normalizeBaseUrl(String url) throws URISyntaxException {
        URI uri = new URI(url);
        String scheme = uri.getScheme().toLowerCase();
        String host = uri.getHost().toLowerCase();

        int port = uri.getPort();
        boolean defaultPort = (scheme.equals("http") && port == 80) || (scheme.equals("https") && port == 443);

        return defaultPort || port == -1
                ? scheme + "://" + host
                : scheme + "://" + host + ":" + port;
    }

}
