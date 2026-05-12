package com.predic8.membrane.core.interceptor.mcp;

import com.predic8.membrane.core.exchange.AbstractExchange;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.proxies.Proxy;
import org.jetbrains.annotations.Nullable;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.regex.PatternSyntaxException;

import static com.predic8.membrane.core.util.HttpUtil.isAbsoluteURI;

final class ExchangeUtils {

    private ExchangeUtils() {
    }

    static boolean matchesExchangeFilter(AbstractExchange exchange, @Nullable String host, @Nullable Integer port, @Nullable String pathPattern) {
        return matchesHost(exchange, host)
               && matchesPort(exchange, port)
               && matchesPathPattern(exchange, pathPattern);
    }

    private static boolean matchesHost(AbstractExchange exchange, @Nullable String host) {
        if (host == null) {
            return true;
        }

        String requestHost = getRequestHost(exchange);
        return requestHost != null && requestHost.equalsIgnoreCase(host);
    }

    private static boolean matchesPort(AbstractExchange exchange, @Nullable Integer port) {
        if (port == null) {
            return true;
        }

        Integer requestPort = getRequestPort(exchange);
        return requestPort != null && requestPort.equals(port);
    }

    private static boolean matchesPathPattern(AbstractExchange exchange, @Nullable String pathPattern) {
        if (pathPattern == null) {
            return true;
        }

        String requestPath = getRequestPath(exchange);
        return requestPath != null && (requestPath.startsWith(pathPattern) || matchesRegex(requestPath, pathPattern)); //TODO keep 'startsWith'?
    }

    private static boolean matchesRegex(String requestPath, String pathPattern) {
        try {
            return requestPath.matches(pathPattern);
        } catch (PatternSyntaxException ignored) {
            return false;
        }
    }

    private static @Nullable String getRequestHost(AbstractExchange exchange) {
        URI authority = getRequestAuthority(exchange);
        return authority != null ? authority.getHost() : null;
    }

    private static @Nullable Integer getRequestPort(AbstractExchange exchange) {
        URI authority = getRequestAuthority(exchange);
        if (authority != null) {
            int authorityPort = authority.getPort();
            if (authorityPort != -1) {
                return authorityPort;
            }
        }

        Proxy proxy = exchange.getProxy();
        if (proxy != null && proxy.getKey() != null && proxy.getKey().getPort() > 0) {
            return proxy.getKey().getPort();
        }
        return null;
    }

    private static @Nullable URI getRequestAuthority(AbstractExchange exchange) {
        if (exchange instanceof Exchange exc) {
            String host = exc.getOriginalHostHeaderHost();
            if (host != null && !host.isBlank()) {
                try {
                    String port = exc.getOriginalHostHeaderPort();
                    return new URI("http://" + host + (port.isBlank() ? "" : ":" + port));
                } catch (URISyntaxException ignored) {}
            }
        }

        String uri = exchange.getRequest().getUri();
        if (uri != null && isAbsoluteURI(uri)) {
            try {
                return new URI(uri);
            } catch (URISyntaxException ignored) {
                return null;
            }
        }

        return null;
    }

    private static @Nullable String getRequestPath(AbstractExchange exchange) {
        if (exchange instanceof Exchange exc) {
            try {
                return exc.getOriginalRelativeURI();
            } catch (RuntimeException ignored) {}
        }

        String uri = exchange.getRequest().getUri();
        if (uri == null) {
            return null;
        }
        if (!isAbsoluteURI(uri)) {
            return uri;
        }

        try {
            URI parsed = new URI(uri);
            String path = parsed.getRawPath();
            if (path == null || path.isEmpty()) {
                path = "/";
            }
            return parsed.getRawQuery() == null ? path : path + "?" + parsed.getRawQuery();
        } catch (URISyntaxException ignored) {
            return uri;
        }
    }
}
