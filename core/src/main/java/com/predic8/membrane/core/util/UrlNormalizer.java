package com.predic8.membrane.core.util;

import java.net.*;
import java.net.URI;

public class UrlNormalizer {

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
