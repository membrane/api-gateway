/* Copyright 2025 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */
package com.predic8.membrane.core.proxies;

import com.predic8.membrane.core.*;
import com.predic8.membrane.core.openapi.serviceproxy.*;
import org.jetbrains.annotations.*;
import org.slf4j.*;

import java.util.*;

import static java.util.stream.Collectors.joining;

public class ApiInfo {

    private static final Logger log = LoggerFactory.getLogger(ApiInfo.class.getName());
    private static final int INDENT = 55;

    public static void logInfosAboutStartedProxies(RuleManager manager) {
        log.info("Started {} API{}:", manager.getRules().size(), (manager.getRules().size() > 1 ? "s" : ""));
        manager.getRules().forEach(proxy ->
                log.info("  {} {}{}", proxyDisplayName(proxy), proxy.getName(), additionalProxyInfo(proxy))
        );
    }

    private static String additionalProxyInfo(Proxy proxy) {
        if (proxy instanceof APIProxy a) {
            Map<String,OpenAPIRecord> recs = a.getApiRecords();
            if (!recs.isEmpty()) {
                return " using OpenAPI specifications:\n" + formatLocationInfo(recs);
            }
        } else if (proxy instanceof SOAPProxy s) {
            return " %s\n    using WSDL @ %s".formatted(getPathString(s),s.getWsdl());
        }
        return "";
    }

    private static String getPathString(SOAPProxy s) {
        if (s.getPath() != null) {
            return s.getPath().getUri();
        }
        return "";
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
                        e.getValue().getSpec().getLocation()
                ))
                .collect(joining("\n"));
    }

    private static String proxyDisplayName(Proxy proxy) {
        if (proxy instanceof APIProxy) {
            return "API";
        } else if (proxy instanceof ServiceProxy) {
            return "Service";
        } else if (proxy instanceof SOAPProxy) {
            return "SOAP";
        } else if (proxy instanceof InternalProxy) {
            return "Internal";
        }
        return "Proxy";
    }
}