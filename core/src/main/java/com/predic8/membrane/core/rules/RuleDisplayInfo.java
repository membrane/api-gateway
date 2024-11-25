package com.predic8.membrane.core.rules;

import com.predic8.membrane.core.*;
import com.predic8.membrane.core.openapi.serviceproxy.*;
import org.jetbrains.annotations.*;
import org.slf4j.*;

import java.util.*;

import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.joining;

/**
 * Provides infos about a rule e.g. for startup infos
 */
public class RuleDisplayInfo {

    private static final Logger log = LoggerFactory.getLogger(RuleDisplayInfo.class.getName());

    public static void logInfosAboutStartedProxies(RuleManager manager) {
        log.info("Started {} API{}:", manager.getRules().size(), (manager.getRules().size() > 1 ? "s" : ""));
        manager.getRules().forEach(rule ->
                log.info("  {} {}{}{}", ruleDisplayName(rule), ruleCustomName(rule), getRuleKeyDisplayName(rule), additionalRuleDisplayName(rule))
        );
    }

    private static String getRuleKeyDisplayName(Rule rule) {
        return String.format("%s:%d%s",
                getHost(rule),
                rule.getKey().getPort(),
                getPath(rule));
    }

    private static @NotNull String getPath(Rule rule) {
        String path = rule.getKey().getPath();
        return path != null ? path : "";
    }

    private static @Nullable String getHost(Rule rule) {
        String host = rule.getKey().getHost();
        return Objects.equals(host, "*") ? getIP(rule) : host;
    }

    private static @NotNull String getIP(Rule rule) {
        String ip = rule.getKey().getIp();
        if (ip == null) {
            return  "0.0.0.0";
        }
        return ip;
    }

    private static String additionalRuleDisplayName(Rule rule) {
        if (rule instanceof APIProxy a) {
            Map<String,OpenAPIRecord> recs = a.getApiRecords();
            if (!recs.isEmpty()) {
                return " using OpenAPI " + formatLocationInfo(recs);
            }
        } else if (rule instanceof SOAPProxy s) {
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

    private static String ruleCustomName(Rule rule) {
        if (Objects.equals(rule.getName(), rule.getKey().toString())) {
            return "";
        }
        return "\"%s\" ".formatted(rule.getName());
    }

    private static String ruleDisplayName(Rule rule) {
        if (rule instanceof APIProxy) {
            return "API";
        } else if (rule instanceof ServiceProxy) {
            return "ServiceProxy";
        } else if (rule instanceof SOAPProxy) {
            return "SOAPProxy";
        } else if (rule instanceof InternalProxy) {
            return "InternalProxy";
        }
        return "Proxy";
    }
}