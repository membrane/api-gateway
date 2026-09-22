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

import com.predic8.membrane.core.openapi.serviceproxy.APIProxy;
import com.predic8.membrane.core.openapi.serviceproxy.OpenAPIRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

import static com.predic8.membrane.core.util.NetworkUtil.addBracketsIfIPv6;
import static com.predic8.membrane.core.util.text.TerminalColors.BRIGHT_GREEN;
import static com.predic8.membrane.core.util.text.TerminalColors.RESET;
import static java.util.stream.Collectors.joining;

public class ApiInfo {

    private static final Logger log = LoggerFactory.getLogger(ApiInfo.class.getName());
    private static final int INDENT = 55;

    public static void logInfosAboutStartedProxies(RuleManager manager) {
        if (manager.getRules().isEmpty()) {
            // Nothing started yet. Happens when the router is started with on K8S startup.
            return;
        }
        log.info("Started {} API{}:", manager.getRules().size(), (manager.getRules().size() > 1 ? "s" : ""));
        manager.getRules().forEach(proxy ->
                log.info(" {}{} {}{}{}", BRIGHT_GREEN(), proxyKind(proxy), proxyLabel(proxy), RESET(), additionalProxyInfo(proxy))
        );
    }

    static String proxyLabel(Proxy proxy) {
        if (proxy instanceof AbstractServiceProxy asp && !(proxy instanceof NotPortOpeningProxy)) {
            return proxy.getName() + "  " + buildUrl(asp);
        }
        return proxy.getName();
    }

    static String buildUrl(AbstractServiceProxy proxy) {
        RuleKey key = proxy.getKey();
        String origin = "%s://%s:%d".formatted(proxy.getProtocol(), displayHost(key), key.getPort());
        if (proxy instanceof APIProxy api && api.getBasePaths() != null && !api.getBasePaths().isEmpty()) {
            return api.getBasePaths().keySet().stream()
                    .map(path -> origin + path)
                    .collect(joining(", "));
        }
        String path = key.getPath();
        return origin + (path != null ? path : "");
    }

    static String displayHost(RuleKey key) {
        if (key.getIp() != null) {
            return addBracketsIfIPv6(key.getIp());
        }
        String host = key.getHost();
        return addBracketsIfIPv6((host == null || host.equals("*")) ? "127.0.0.1" : host);
    }

    private static String additionalProxyInfo(Proxy proxy) {
        if (proxy instanceof APIProxy a) {
            Map<String,OpenAPIRecord> recs = a.getApiRecords();
            if (!recs.isEmpty()) {
                return "\n" + formatLocationInfo(recs);
            }
        } else if (proxy instanceof SOAPProxy s) {
            return "\n    using WSDL @ %s".formatted(s.getWsdl());
        }
        return "";
    }

    private static String formatLocationInfo(Map<String, OpenAPIRecord> specs) {
        return specs.entrySet().stream()
                .map(e -> " ".repeat(INDENT) + "- \"%s\" @ %s".formatted(
                        e.getKey(),
                        e.getValue().getSpec().getLocation()
                ))
                .collect(joining("\n"));
    }

    private static String proxyKind(Proxy proxy) {
        if (proxy instanceof APIProxy) {
            return "";
        }
        if (proxy instanceof ServiceProxy) {
            return "Service:";
        }
        if (proxy instanceof SOAPProxy) {
            return "SOAP:";
        }
        if (proxy instanceof InternalProxy) {
            return "Internal:";
        }
        return "Proxy:";
    }
}