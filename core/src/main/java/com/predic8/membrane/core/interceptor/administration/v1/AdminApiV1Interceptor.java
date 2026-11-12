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
import com.predic8.membrane.core.interceptor.administration.*;
import com.predic8.membrane.core.util.ConfigurationException;
import com.predic8.membrane.core.util.URLParamUtil;
import org.slf4j.*;

import java.net.InetAddress;
import java.net.URISyntaxException;
import java.net.URLDecoder;
import java.net.UnknownHostException;
import java.security.*;
import java.util.*;
import java.util.regex.*;

import static com.predic8.membrane.core.http.Header.*;
import static com.predic8.membrane.core.interceptor.Outcome.*;
import static com.predic8.membrane.core.interceptor.administration.v1.Access.*;
import static com.predic8.membrane.core.interceptor.administration.v1.AdminApiV1Interceptor.Kind.*;
import static com.predic8.membrane.core.interceptor.administration.v1.AdminApiV1Interceptor.Scope.*;
import static com.predic8.membrane.core.interceptor.administration.v1.ApiException.*;
import static com.predic8.membrane.core.util.HttpUtil.*;
import static com.predic8.membrane.core.util.text.TextUtil.*;
import static java.nio.charset.StandardCharsets.*;

/**
 * Serves the admin REST API v1 under {@value #PREFIX}, as described by
 * {@code core/src/main/resources/com/predic8/membrane/core/interceptor/administration/api/admin-api-v1.yaml}.
 * <p>
 * With {@code peers}, requests are answered for all instances: see {@link Aggregator}.
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

    /**
     * How a console with peers answers a route.
     */
    enum Scope {
        /**
         * Only this instance answers.
         */
        LOCAL,
        /**
         * The first path parameter names the instance that answers.
         */
        INSTANCE,
        /**
         * All instances answer; the results are merged as described by the route's {@link Merge}.
         */
        COLLECTION
    }

    enum Kind {
        /**
         * A paged, sorted list in {@code field}.
         */
        PAGE,
        /**
         * The arrays in {@code field} are concatenated.
         */
        CONCAT,
        /**
         * Every array property is united.
         */
        FACETS,
        /**
         * The {@code /instances} list.
         */
        INSTANCES,
        /**
         * HAR export: the exchanges are fetched from the instances they belong to.
         */
        HAR,
        /**
         * Comparison of two messages that may belong to different instances.
         */
        DIFF
    }

    /**
     * @param sorts allowed values of the {@code sort} parameter of a {@code PAGE}; checked before fanning out
     */
    record Merge(Kind kind, String field, String defaultSort, String defaultOrder, Set<String> sorts) {
        Merge(Kind kind, String field, String defaultSort, String defaultOrder) {
            this(kind, field, defaultSort, defaultOrder, Set.of());
        }
    }

    record Route(String method, Pattern pattern, Access access, Handler handler, Scope scope, Merge merge) {
    }

    private boolean readOnly;
    private boolean useXForwardedForAsClientAddr;
    private String instanceName;
    private String apiKey;
    private List<AdminConsolePeer> peers = List.of();

    private List<Route> routes = List.of();
    private Aggregator aggregator;

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
        final var exchanges = new ExchangesResource(ctx);
        final var tools = new CallToolsResource(ctx, exchanges);
        routes = routes(ctx, exchanges, tools);
        validatePeers(ctx.instanceName());
        aggregator = peers.isEmpty() ? null : new Aggregator(ctx, peers, new PeerClient(), tools);
    }

    private static List<Route> routes(ConsoleContext ctx, ExchangesResource exchanges, CallToolsResource tools) {
        final var apis = new ApisResource(ctx);
        final var monitoring = new MonitoringResource(ctx);
        final var balancers = new BalancersResource(ctx);
        final var api = "/apis/" + SEGMENT + "/" + SEGMENT;
        final var exchange = "/exchanges/" + SEGMENT + "/" + SEGMENT;
        final var message = exchange + "/(request|response)";
        final var balancer = "/balancers/" + SEGMENT + "/" + SEGMENT;
        final var node = balancer + "/clusters/" + SEGMENT + "/nodes/" + SEGMENT;

        final var r = new ArrayList<Route>();
        r.add(collection("/instances", new Merge(INSTANCES, "instances", null, null), monitoring::instances));
        r.add(new Route("GET", Pattern.compile("/auth/me"), READ, monitoring::me, LOCAL, null));
        r.add(notImplemented("POST", "/auth/login", "Login is not available yet."));
        r.add(notImplemented("POST", "/auth/logout", "Login is not available yet."));

        r.add(collection("/apis", new Merge(PAGE, "apis", "order", "asc", ApisResource.SORTS.keySet()), apis::list));
        r.add(notImplemented("POST", "/apis", "Adding APIs is not available yet."));
        r.add(instance("GET", api, READ, apis::get));
        r.add(instance("DELETE", api, ADMIN, apis::delete));
        r.add(instance("POST", api + "/start", ADMIN, apis::start));
        r.add(instance("GET", api + "/flow", READ, apis::flow));
        r.add(notImplemented("GET", api + "/config", "The configuration view is not available yet."));
        r.add(notImplemented("GET", api + "/openapi", "The OpenAPI view is not available yet."));
        r.add(collection("/transport/flow", new Merge(CONCAT, "instances", null, null), apis::transportFlow));
        r.add(notImplemented("GET", "/config", "The configuration status is not available yet."));

        r.add(collection("/exchanges", new Merge(PAGE, "exchanges", "time", "desc", ExchangesResource.SORTS), exchanges::list));
        r.add(collection("/exchanges/facets", new Merge(FACETS, null, null, null), exchanges::facets));
        r.add(collection("/exchanges/store", new Merge(CONCAT, "stores", null, null), exchanges::stores));
        r.add(collection("/exchanges/export", new Merge(HAR, null, null, null), tools::export));
        r.add(collection("/exchanges/diff", new Merge(DIFF, null, null, null), tools::diff));
        r.add(instance("GET", exchange, READ, exchanges::get));
        r.add(instance("GET", message + "/headers", READ, exchanges::headers));
        r.add(instance("GET", message + "/raw", READ, exchanges::raw));
        r.add(instance("GET", message + "/body", READ, exchanges::body));
        r.add(instance("GET", message + "/body/formatted", READ, exchanges::formatted));
        r.add(instance("GET", exchange + "/curl", READ, exchanges::curl));
        r.add(notImplemented("GET", exchange + "/frames", "WebSocket frames are not recorded yet."));
        r.add(instance("POST", exchange + "/replay", ADMIN, tools::replay));

        r.add(collection("/statistics/apis", new Merge(CONCAT, "apis", null, null), monitoring::apiStatistics));
        r.add(collection("/statistics/clients", new Merge(PAGE, "clients", "client", "asc", MonitoringResource.CLIENT_SORTS.keySet()), monitoring::clientStatistics));
        r.add(notImplemented("GET", "/metrics/timeseries", "Time series are not collected yet."));
        r.add(collection("/streams", new Merge(CONCAT, "streams", null, null), monitoring::streams));
        r.add(collection("/system", new Merge(CONCAT, "instances", null, null), monitoring::system));
        r.add(notImplemented("GET", "/logs", "The log viewer is not available yet."));

        r.add(collection("/balancers", new Merge(CONCAT, "balancers", null, null), balancers::list));
        r.add(instance("POST", balancer + "/clusters", ADMIN, balancers::createCluster));
        r.add(instance("POST", balancer + "/clusters/" + SEGMENT + "/nodes", ADMIN, balancers::createNode));
        r.add(instance("GET", node, READ, balancers::getNode));
        r.add(instance("DELETE", node, ADMIN, balancers::deleteNode));
        r.add(instance("POST", node + "/(up|down|takeout|reset)", NODE_CONTROL, balancers::changeNode));

        r.add(notImplemented("GET", "/plugins/rate-limiters", "Plugin state is not available yet."));
        r.add(notImplemented("POST", "/plugins/rate-limiters/" + SEGMENT + "/" + SEGMENT + "/reset", "Plugin state is not available yet."));
        r.add(notImplemented("GET", "/plugins/circuit-breakers", "Plugin state is not available yet."));
        r.add(notImplemented("POST", "/plugins/circuit-breakers/" + SEGMENT + "/" + SEGMENT + "/reset", "Plugin state is not available yet."));
        r.add(notImplemented("GET", "/plugins/credentials", "Plugin state is not available yet."));
        r.add(notImplemented("GET", "/tls/certificates", "The certificate overview is not available yet."));
        return List.copyOf(r);
    }

    private static Route collection(String regex, Merge merge, SimpleHandler handler) {
        return new Route("GET", Pattern.compile(regex), READ, (req, principal) -> handler.handle(req), COLLECTION, merge);
    }

    private static Route instance(String method, String regex, Access access, SimpleHandler handler) {
        return new Route(method, Pattern.compile(regex), access, (req, principal) -> handler.handle(req), INSTANCE, null);
    }

    private static Route notImplemented(String method, String regex, String detail) {
        return new Route(method, Pattern.compile(regex), READ, (req, principal) -> {
            throw ApiException.notImplemented(detail);
        }, LOCAL, null);
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

        exc.setResponse(dispatch(exc, rawPath, uri.getRawQuery()));
        exc.getResponse().getHeader().setValue(CACHE_CONTROL, "no-store");
        return RETURN;
    }

    private Response dispatch(Exchange exc, String rawPath, String rawQuery) {
        final var path = rawPath.substring(PREFIX.length());
        final var method = exc.getRequest().getMethod();
        try {
            final var principal = authenticate(exc);

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

            if (!principal.may(match.access()))
                throw forbidden(match.access() == NODE_CONTROL
                        ? "Changing the state of a node needs the nodeOperator role."
                        : "The admin console is configured to be readOnly.");

            final var req = new ApiRequest(exc, pathParams(matcher), URLParamUtil.parseQueryString(rawQuery));
            if (aggregator != null && !isForwarded(exc))
                return aggregator.handle(match, req, principal, rawPath, rawQuery);
            return match.handler().handle(req, principal);
        } catch (ApiException e) {
            return ApiJson.problem(e);
        } catch (UnsupportedOperationException e) {
            return ApiJson.problem(ApiException.notImplemented(e.getMessage()));
        } catch (Exception e) {
            log.error("Admin API request {} {} failed.", escapeControlCharsForLog(method), escapeControlCharsForLog(path), e);
            return ApiJson.problem(new ApiException(500, "Internal server error.", null));
        }
    }

    /**
     * With an {@code apiKey}, every request must carry it; the key holder has all roles. Without one, callers are
     * anonymous, as in the old console.
     */
    private Principal authenticate(Exchange exc) {
        if (apiKey == null)
            return Principal.anonymous(readOnly);
        final var given = exc.getRequest().getHeader().getFirstValue(PeerClient.API_KEY_HEADER);
        if (given == null || !MessageDigest.isEqual(given.getBytes(UTF_8), apiKey.getBytes(UTF_8)))
            throw new ApiException(401, "Unauthorized.", "A valid X-Api-Key header is required.");
        return Principal.of(true, "apiKey", Set.of(Principal.VIEWER, Principal.ADMIN, Principal.NODE_OPERATOR), readOnly);
    }

    private static boolean isForwarded(Exchange exc) {
        return exc.getRequest().getHeader().getFirstValue(PeerClient.FORWARDED) != null;
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

    private void validatePeers(String self) {
        final var names = new HashSet<String>();
        names.add(self);
        for (final var peer : peers) {
            validatePeerUrl(peer.getUrl());
            if (peer.getName() != null) {
                validateInstanceName(peer.getName());
                if (!names.add(peer.getName()))
                    throw new ConfigurationException("adminConsole: the instance name '%s' is used more than once.".formatted(peer.getName()));
            }
            if (peer.isWarnOnPlainHttp() && peer.getApiKey() != null && peer.getUrl().regionMatches(true, 0, "http:", 0, 5))
                log.warn("adminConsole: the API key for peer {} is sent over plain HTTP. Use https://, or set warnOnPlainHttp: false if the connection is protected otherwise.",
                        escapeControlCharsForLog(peer.getUrl()));
        }
    }

    static void validatePeerUrl(String url) {
        final java.net.URI uri;
        try {
            uri = url == null ? null : new java.net.URI(url);
        } catch (java.net.URISyntaxException e) {
            throw new ConfigurationException("The url of an adminConsole peer is invalid: " + url);
        }
        if (uri == null || uri.getScheme() == null || !uri.getScheme().matches("(?i)https?") || uri.getHost() == null
            || uri.getRawFragment() != null || uri.getRawQuery() != null)
            throw new ConfigurationException("The url of an adminConsole peer must be http:// or https:// with a host, and without query or fragment, but was: " + url);
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

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public List<AdminConsolePeer> getPeers() {
        return peers;
    }

    public void setPeers(List<AdminConsolePeer> peers) {
        this.peers = peers == null ? List.of() : List.copyOf(peers);
    }
}
