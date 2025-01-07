package com.predic8.membrane.core.proxies;

import com.predic8.membrane.core.*;
import com.predic8.membrane.core.openapi.serviceproxy.*;
import org.jetbrains.annotations.*;
import org.slf4j.*;

import java.util.*;

import static java.util.stream.Collectors.joining;

public class ProxyDisplayInfo {

    private static final Logger log = LoggerFactory.getLogger(ProxyDisplayInfo.class.getName());
    private static final int INDENT = 55;

    public static void logInfosAboutStartedProxies(RuleManager manager) {
        log.info("Started {} API{}:", manager.getRules().size(), (manager.getRules().size() > 1 ? "s" : ""));
        manager.getRules().forEach(proxy ->
                log.info("  {} {}{}{}", proxyDisplayName(proxy), proxyCustomName(proxy), getProxyKeyDisplayName(proxy), additionalProxyDisplayName(proxy))
        );
    }

    private static String additionalProxyDisplayName(Proxy proxy) {
        if (proxy instanceof APIProxy a) {
            Map<String,OpenAPIRecord> recs = a.getApiRecords();
            if (!recs.isEmpty()) {
                return " using OpenAPI specifications:\n" + formatLocationInfo(recs);
            }
        } else if (proxy instanceof SOAPProxy s) {
            return " using WSDL @ " + s.getWsdl();
        }
        return "";
    }

    private static String getProxyKeyDisplayName(Proxy proxy) {
        return String.format("%s:%d%s",
                getHost(proxy),
                proxy.getKey().getPort(),
                getPath(proxy));
    }

    private static @NotNull String getPath(Proxy proxy) {
        String path = proxy.getKey().getPath();
        return path != null ? path : "";
    }

    private static @Nullable String getHost(Proxy proxy) {
        String host = proxy.getKey().getHost();
        return Objects.equals(host, "*") ? getIP(proxy) : host;
    }

    private static @NotNull String getIP(Proxy proxy) {
        String ip = proxy.getKey().getIp();
        if (ip == null) {
            return  "0.0.0.0";
        }
        return ip;
    }

    private static String formatLocationInfo(Map<String, OpenAPIRecord> specs) {
        return specs.entrySet().stream()
                .map(e -> " ".repeat(INDENT) + "- \"%s\" @ %s".formatted(
                        e.getKey(),
                        Optional.ofNullable(e.getValue().getSpec().getLocation()).orElse("(location not set)")
                ))
                .collect(joining("\n"));
    }

    private static String proxyCustomName(Proxy proxy) {
        if (Objects.equals(proxy.getName(), proxy.getKey().toString())) {
            return "";
        }
        return "\"%s\" ".formatted(proxy.getName());
    }

    private static String proxyDisplayName(Proxy proxy) {
        if (proxy instanceof APIProxy) {
            return "API";
        } else if (proxy instanceof ServiceProxy) {
            return "ServiceProxy";
        } else if (proxy instanceof SOAPProxy) {
            return "SOAPProxy";
        } else if (proxy instanceof InternalProxy) {
            return "InternalProxy";
        }
        return "Proxy";
    }
}