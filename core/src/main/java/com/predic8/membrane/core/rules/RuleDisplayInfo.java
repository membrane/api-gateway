package com.predic8.membrane.core.rules;

import com.predic8.membrane.core.*;
import com.predic8.membrane.core.openapi.serviceproxy.*;
import org.jetbrains.annotations.*;
import org.slf4j.*;

import java.util.*;

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
                return " using OpenAPI " + formatLocationInfo(recs, ((APIProxy) rule));
            }
        } else if (rule instanceof SOAPProxy s) {
            return " using WSDL @ " + s.getWsdl();
        }
        return "";
    }

    private static String formatLocationInfo(Map<String, OpenAPIRecord> specs, APIProxy api) {
        if (specs.size() == 1) {
            Map.Entry<String, OpenAPIRecord> record = specs.entrySet().iterator().next();
            return "\"" + record.getKey() + "\"" + " @ " + record.getValue().getSpec().getLocation();
        } else {
            String dir = specs.values().iterator().next().getSpec().getDir();
            return "directory: " + dir + " containing [" +
                    String.join(", ", specs.keySet()) +
                    "]";
        }
    }

    private static String getLocationsAsString(Map<String, OpenAPIRecord> specs) {
        return String.join(", ", specs.keySet());
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