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
                log.info("  {} {}{}{}", ruleDisplayName(), ruleCustomName(), getRuleKeyDisplayName(), additionalRuleDisplayName())
        );
    }

    private String getRuleKeyDisplayName() {
        return String.format("%s:%d%s",
                getHost(),
                rule.getKey().getPort(),
                getPath());
    }

    private  @NotNull String getPath() {
        String path = rule.getKey().getPath();
        return path != null ? path : "";
    }

    private  @Nullable String getHost() {
        String host = rule.getKey().getHost();
        return Objects.equals(host, "*") ? getIP() : host;
    }

    private  @NotNull String getIP() {
        String ip = rule.getKey().getIp();
        if (ip == null) {
            return  "0.0.0.0";
        }
        return ip;
    }

    private String additionalRuleDisplayName() {
        if (rule instanceof APIProxy a) {
            Map<String,OpenAPIRecord> recs = a.getApiRecords();
            if (!recs.isEmpty()) {
                return " using OpenAPI @ " + getLocationsAsString(recs);
            }
        } else if (rule instanceof SOAPProxy s) {
            return " using WSDL @ " + s.getWsdl();
        }
        return "";
    }

    private String getLocationsAsString(Map<String, OpenAPIRecord> specs) {
        return specs.entrySet().stream().map(e -> e.getKey()).collect(joining(", "));
    }

    private String ruleCustomName() {
        if (Objects.equals(rule.getName(), rule.getKey().toString())) {
            return "";
        }
        return "\"%s\" ".formatted(rule.getName());
    }

    private String ruleDisplayName() {
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
