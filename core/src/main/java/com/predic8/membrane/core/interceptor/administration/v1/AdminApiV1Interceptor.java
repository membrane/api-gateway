/* Copyright 2026 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */
package com.predic8.membrane.core.interceptor.administration.v1;

import com.predic8.membrane.core.exchange.*;
import com.predic8.membrane.core.http.*;
import com.predic8.membrane.core.interceptor.*;
import com.predic8.membrane.core.util.ConfigurationException;
import com.predic8.membrane.core.util.URLParamUtil;
import org.slf4j.*;

import java.net.InetAddress;
import java.net.URISyntaxException;
import java.net.URLDecoder;
import java.net.UnknownHostException;
import java.util.*;
import java.util.regex.*;

import static com.predic8.membrane.core.http.Header.*;
import static com.predic8.membrane.core.interceptor.Outcome.*;
import static com.predic8.membrane.core.interceptor.administration.v1.Access.*;
import static com.predic8.membrane.core.interceptor.administration.v1.ApiException.*;
import static com.predic8.membrane.core.util.HttpUtil.*;
import static com.predic8.membrane.core.util.text.TextUtil.*;
import static java.nio.charset.StandardCharsets.*;

/**
 * Serves the admin REST API v1 under {@value #PREFIX}, as described by
 * {@code core/src/main/resources/com/predic8/membrane/core/interceptor/administration/api/admin-api-v1.yaml}.
 * <p>
 * Part of {@code adminConsole}; not a config element of its own. Thread safe: all fields are written before
 * {@link #init()} and only read afterwards.
 */
public class AdminApiV1Interceptor extends AbstractInterceptor {

    public static final String PREFIX = "/admin/api/v1";

    private static final Logger log = LoggerFactory.getLogger(AdminApiV1Interceptor.class);

    private static final String SEGMENT = "([^/]+)";

    @FunctionalInterface
    interface Handler {
        Response handle(ApiRequest req, Principal principal) throws Exception;
    }

    @FunctionalInterface
    interface SimpleHandler {
        Response handle(ApiRequest req) throws Exception;
    }

    record Route(String method, Pattern pattern, Access access, Handler handler) {
    }

    private boolean readOnly;
    private boolean useXForwardedForAsClientAddr;
    private String instanceName;

    private List<Route> routes = List.of();

    public AdminApiV1Interceptor() {
        name = "admin api v1";
    }

    @Override
    public void init() {
        super.init();
        if (instanceName != null)
            validateInstanceName(instanceName);
        final var ctx = new ConsoleContext(router, instanceName != null ? instanceName : defaultInstanceName(),
                readOnly, useXForwardedForAsClientAddr, System.currentTimeMillis());
        routes = routes(ctx);
    }

    private static List<Route> routes(ConsoleContext ctx) {
        final var apis = new ApisResource(ctx);
        final var exchanges = new ExchangesResource(ctx);
        final var monitoring = new MonitoringResource(ctx);
        final var balancers = new BalancersResource(ctx);
        final var api = "/apis/" + SEGMENT + "/" + SEGMENT;
        final var exchange = "/exchanges/" + SEGMENT + "/" + SEGMENT;
        final var message = exchange + "/(request|response)";
        final var node = "/balancers/" + SEGMENT + "/" + SEGMENT + "/clusters/" + SEGMENT + "/nodes/" + SEGMENT;

        final var r = new ArrayList<Route>();
        r.add(get("/instances", monitoring::instances));
        r.add(new Route("GET", Pattern.compile("/auth/me"), READ, monitoring::me));
        r.add(notImplemented("POST", "/auth/login", "Login is not available yet."));
        r.add(notImplemented("POST", "/auth/logout", "Login is not available yet."));

        r.add(get("/apis", apis::list));
        r.add(notImplemented("POST", "/apis", "Adding APIs is not available yet."));
        r.add(get(api, apis::get));
        r.add(route("DELETE", api, ADMIN, apis::delete));
        r.add(route("POST", api + "/start", ADMIN, apis::start));
        r.add(get(api + "/flow", apis::flow));
        r.add(notImplemented("GET", api + "/config", "The configuration view is not available yet."));
        r.add(notImplemented("GET", api + "/openapi", "The OpenAPI view is not available yet."));
        r.add(get("/transport/flow", apis::transportFlow));
        r.add(notImplemented("GET", "/config", "The configuration status is not available yet."));

        r.add(get("/exchanges", exchanges::list));
        r.add(get("/exchanges/facets", exchanges::facets));
        r.add(get("/exchanges/store", exchanges::stores));
        r.add(notImplemented("GET", "/exchanges/export", "HAR export is not available yet."));
        r.add(notImplemented("GET", "/exchanges/diff", "Comparing exchanges is not available yet."));
        r.add(get(exchange, exchanges::get));
        r.add(get(message + "/headers", exchanges::headers));
        r.add(get(message + "/raw", exchanges::raw));
        r.add(get(message + "/body", exchanges::body));
        r.add(get(message + "/body/formatted", exchanges::formatted));
        r.add(get(exchange + "/curl", exchanges::curl));
        r.add(notImplemented("GET", exchange + "/frames", "WebSocket frames are not recorded yet."));
        r.add(notImplemented("POST", exchange + "/replay", "Replay is not available yet."));

        r.add(get("/statistics/apis", monitoring::apiStatistics));
        r.add(get("/statistics/clients", monitoring::clientStatistics));
        r.add(notImplemented("GET", "/metrics/timeseries", "Time series are not collected yet."));
        r.add(get("/streams", monitoring::streams));
        r.add(get("/system", monitoring::system));
        r.add(notImplemented("GET", "/logs", "The log viewer is not available yet."));

        r.add(get("/balancers", balancers::list));
        r.add(route("POST", "/balancers/" + SEGMENT + "/" + SEGMENT + "/clusters", ADMIN, balancers::createCluster));
        r.add(route("POST", "/balancers/" + SEGMENT + "/" + SEGMENT + "/clusters/" + SEGMENT + "/nodes", ADMIN, balancers::createNode));
        r.add(get(node, balancers::getNode));
        r.add(route("DELETE", node, ADMIN, balancers::deleteNode));
        r.add(route("POST", node + "/(up|down|takeout|reset)", NODE_CONTROL, balancers::changeNode));

        r.add(notImplemented("GET", "/plugins/rate-limiters", "Plugin state is not available yet."));
        r.add(notImplemented("POST", "/plugins/rate-limiters/" + SEGMENT + "/" + SEGMENT + "/reset", "Plugin state is not available yet."));
        r.add(notImplemented("GET", "/plugins/circuit-breakers", "Plugin state is not available yet."));
        r.add(notImplemented("POST", "/plugins/circuit-breakers/" + SEGMENT + "/" + SEGMENT + "/reset", "Plugin state is not available yet."));
        r.add(notImplemented("GET", "/plugins/credentials", "Plugin state is not available yet."));
        r.add(notImplemented("GET", "/tls/certificates", "The certificate overview is not available yet."));
        return List.copyOf(r);
    }

    private static Route get(String regex, SimpleHandler handler) {
        return route("GET", regex, READ, handler);
    }

    private static Route route(String method, String regex, Access access, SimpleHandler handler) {
        return new Route(method, Pattern.compile(regex), access, (req, principal) -> handler.handle(req));
    }

    private static Route notImplemented(String method, String regex, String detail) {
        return new Route(method, Pattern.compile(regex), READ, (req, principal) -> {
            throw ApiException.notImplemented(detail);
        });
    }

    @Override
    public Outcome handleRequest(Exchange exc) {
        final com.predic8.membrane.core.util.URI uri;
        try {
            uri = router.getConfiguration().getUriFactory().create(exc.getDestinations().getFirst());
        } catch (URISyntaxException e) {
            return CONTINUE;
        }
        final var rawPath = uri.getRawPath();
        if (rawPath == null || !(rawPath.equals(PREFIX) || rawPath.startsWith(PREFIX + "/")))
            return CONTINUE;

        exc.setResponse(dispatch(exc, rawPath.substring(PREFIX.length()), uri.getRawQuery()));
        exc.getResponse().getHeader().setValue(CACHE_CONTROL, "no-store");
        return RETURN;
    }

    private Response dispatch(Exchange exc, String path, String rawQuery) {
        final var method = exc.getRequest().getMethod();
        try {
            Route match = null;
            Matcher matcher = null;
            final var allowed = new TreeSet<String>();
            for (final var route : routes) {
                final var m = route.pattern().matcher(path);
                if (!m.matches())
                    continue;
                allowed.add(route.method());
                if (match == null && route.method().equals(method)) {
                    match = route;
                    matcher = m;
                }
            }
            if (match == null) {
                if (allowed.isEmpty())
                    throw notFound("No such admin API endpoint.");
                return methodNotAllowed(allowed);
            }

            if (!"GET".equals(method) && isCrossOrigin(exc)) {
                log.info("Rejected cross-origin admin API request {} {}", escapeControlCharsForLog(method), escapeControlCharsForLog(path));
                throw forbidden("Cross-origin requests may not change the router's state.");
            }

            final var principal = Principal.anonymous(readOnly);
            if (!principal.may(match.access()))
                throw forbidden(match.access() == NODE_CONTROL
                        ? "Changing the state of a node needs the nodeOperator role."
                        : "The admin console is configured to be readOnly.");

            return match.handler().handle(new ApiRequest(exc, pathParams(matcher), URLParamUtil.parseQueryString(rawQuery)), principal);
        } catch (ApiException e) {
            return ApiJson.problem(e);
        } catch (UnsupportedOperationException e) {
            return ApiJson.problem(ApiException.notImplemented(e.getMessage()));
        } catch (Exception e) {
            log.error("Admin API request {} {} failed.", escapeControlCharsForLog(method), escapeControlCharsForLog(path), e);
            return ApiJson.problem(new ApiException(500, "Internal server error.", null));
        }
    }

    private static Response methodNotAllowed(Set<String> allowed) {
        final var response = ApiJson.problem(new ApiException(405, "Method not allowed.", null));
        response.getHeader().setValue(ALLOW, String.join(", ", allowed));
        return response;
    }

    private static List<String> pathParams(Matcher matcher) {
        final var params = new ArrayList<String>(matcher.groupCount());
        for (int i = 1; i <= matcher.groupCount(); i++)
            // A literal '+' is part of the segment (names may contain it), not an encoded space.
            params.add(URLDecoder.decode(matcher.group(i).replace("+", "%2B"), UTF_8));
        return List.copyOf(params);
    }

    /**
     * Instance names are path segments and values of the comma separated {@code instance} filter.
     */
    static void validateInstanceName(String name) {
        if (name.isBlank())
            throw new ConfigurationException("adminConsole: instanceName must not be empty.");
        if (name.contains(",") || name.contains("/"))
            throw new ConfigurationException("adminConsole: instanceName '%s' must not contain ',' or '/'.".formatted(name));
    }

    private static String defaultInstanceName() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (UnknownHostException e) {
            return "localhost";
        }
    }

    public boolean isReadOnly() {
        return readOnly;
    }

    public void setReadOnly(boolean readOnly) {
        this.readOnly = readOnly;
    }

    public boolean isUseXForwardedForAsClientAddr() {
        return useXForwardedForAsClientAddr;
    }

    public void setUseXForwardedForAsClientAddr(boolean useXForwardedForAsClientAddr) {
        this.useXForwardedForAsClientAddr = useXForwardedForAsClientAddr;
    }

    public String getInstanceName() {
        return instanceName;
    }

    public void setInstanceName(String instanceName) {
        this.instanceName = instanceName;
    }
}
