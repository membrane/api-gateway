package com.predic8.membrane.core.proxies;

import com.predic8.membrane.core.*;
import com.predic8.membrane.core.openapi.serviceproxy.*;
import org.jetbrains.annotations.*;
import org.slf4j.*;

import java.util.*;

import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.joining;

/**
 * Provides infos about a proxy e.g. for startup infos
 */
public class RuleDisplayInfo {

    private static final Logger log = LoggerFactory.getLogger(RuleDisplayInfo.class.getName());

    public static void logInfosAboutStartedProxies(RuleManager manager) {
        log.info("Started {} API{}:", manager.getRules().size(), (manager.getRules().size() > 1 ? "s" : ""));
        manager.getRules().forEach(proxy ->
                log.info("  {} {}{}{}", proxyDisplayName(proxy), proxyCustomName(proxy), getProxyKeyDisplayName(proxy), additionalProxyDisplayName(proxy))
        );
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

    private static String additionalProxyDisplayName(Proxy proxy) {
        if (proxy instanceof APIProxy a) {
            Map<String,OpenAPIRecord> recs = a.getApiRecords();
            if (!recs.isEmpty()) {
                return " using OpenAPI " + formatLocationInfo(recs);
            }
        } else if (proxy instanceof SOAPProxy s) {
            return " using WSDL @ " + s.getWsdl();
        }
        return "";
    }

    private static String formatLocationInfo(Map<String, OpenAPIRecord> specs) {
        return getSpecsByDir(specs).entrySet().stream()
                .map(e -> formatDirGroup(e.getKey(), e.getValue()))
                .collect(joining("\n"));
    }

    private static @NotNull Map<String, @NotNull List<Map.Entry<String, OpenAPIRecord>>> getSpecsByDir(Map<String, OpenAPIRecord> specs) {
        return specs.entrySet().stream().collect(groupingBy(e ->
                Optional.ofNullable(e.getValue().getSpec().getDir()).orElse("")
        ));
    }

    private static String formatDirGroup(String dir, List<Map.Entry<String, OpenAPIRecord>> entries) {
        var specsInfo = entries.stream()
                .map(e -> "\"%s\" @ %s".formatted(e.getKey(), e.getValue().getSpec().getLocation()))
                .collect(joining("\n" + " ".repeat(67)));

        return dir.isEmpty() ? specsInfo : ("Directory \"%s\":\n" + " ".repeat(67) + "%s").formatted(dir, specsInfo);
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