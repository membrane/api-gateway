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

package com.predic8.membrane.core.interceptor.wsdl2openapi;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.predic8.membrane.annot.MCAttribute;
import com.predic8.membrane.annot.MCChildElement;
import com.predic8.membrane.annot.MCElement;
import com.predic8.membrane.annot.Required;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.Response;
import com.predic8.membrane.core.interceptor.AbstractInterceptor;
import com.predic8.membrane.core.interceptor.Interceptor;
import com.predic8.membrane.core.interceptor.Outcome;
import com.predic8.membrane.core.openapi.serviceproxy.*;
import com.predic8.membrane.core.proxies.AbstractServiceProxy;
import com.predic8.membrane.core.proxies.ServiceProxy;
import com.predic8.membrane.core.resolver.ResolverMap;
import com.predic8.membrane.core.util.ConfigurationException;
import com.predic8.membrane.core.util.wsdl.parser.BindingOperation;
import com.predic8.membrane.core.util.wsdl.parser.Definitions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.predic8.membrane.core.exceptions.ProblemDetails.*;
import static com.predic8.membrane.core.http.MimeType.APPLICATION_JSON;
import static com.predic8.membrane.core.http.MimeType.TEXT_XML;
import static com.predic8.membrane.core.interceptor.InterceptorUtil.getInterceptors;
import static com.predic8.membrane.core.interceptor.Outcome.ABORT;
import static com.predic8.membrane.core.interceptor.Outcome.CONTINUE;
import static com.predic8.membrane.core.interceptor.wsdl2openapi.XsdDomUtil.camelToKebab;
import static com.predic8.membrane.core.openapi.serviceproxy.OpenAPIPublisherInterceptor.PATH;
import static com.predic8.membrane.core.resolver.ResolverMap.combine;
import static com.predic8.membrane.core.util.wsdl.parser.Definitions.parse;
import static com.predic8.membrane.core.util.wsdl.parser.Operation.Direction.OUTPUT;
import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * @description <p>
 * The <i>wsdl2openapi</i> interceptor exposes SOAP/WSDL services via HTTP/JSON/OpenAPI.
 * It automatically converts JSON requests to SOAP XML and SOAP XML responses back to JSON.
 * </p>
 * <p>
 * Can only be used within an <i>api</i>, and only one <i>wsdl2openapi</i> is allowed per API: it
 * owns the paths and the generated OpenAPI document of the API it is placed in. To expose several
 * WSDLs, declare one API per WSDL.
 * </p>
 * @yaml <pre><code>
 * api:
 *   port: 2000
 *   path: /purchasing
 *   flow:
 *     - wsdl2openapi:
 *         wsdl: http://backend-service/service.wsdl
 *         operations:
 *           getAll:
 *             method: GET
 *             path: articles
 *           createOrder:
 *             method: POST
 *             path: orders
 * </code></pre>
 * @topic 6. Web Services with SOAP and WSDL
 */
@MCElement(name = "wsdl2openapi")
public class Wsdl2OpenapiInterceptor extends AbstractInterceptor {

    private static final Logger log = LoggerFactory.getLogger(Wsdl2OpenapiInterceptor.class);

    private String wsdl;
    private Definitions definitions;
    private XsdToSchema xsdToSchema;
    private String basePath;
    private OperationsConfig operations;
    private Map<String, OperationSettings> operationsByName = Map.of();
    record RouteEntry(Pattern pathPattern, List<String> paramNames, String method, String operationName) {}
    record RouteMatch(String operationName, Map<String, String> pathParams) {}

    private List<RouteEntry> routes = new ArrayList<>();
    private final Map<String, Json2SoapTransformer> requestTransformers = new LinkedHashMap<>();
    private OpenAPIPublisherInterceptor publisher;

    private final String instanceId = UUID.randomUUID().toString();
    private final String operationPropertyKey = "wsdl2openapi.operation." + instanceId;

    public Wsdl2OpenapiInterceptor() {
        name = "wsdl2openapi";
    }

    // Package-private — used by tests only
    Wsdl2OpenapiInterceptor(String basePath, List<RouteEntry> routes) {
        this.basePath = basePath;
        this.routes = new ArrayList<>(routes);
    }

    @Override
    public void init() {
        super.init();

        if (!(proxy instanceof APIProxy)) {
            throw new ConfigurationException("""
                    The wsdl2openapi plugin can only be used within an api, but '%s' is not one.
                    It publishes an OpenAPI document at %s, which only an api can route to.""".formatted(proxy.getName(), PATH));
        }

        if (getInterceptors(proxy.getFlow(), Wsdl2OpenapiInterceptor.class).size() > 1) {
            throw new ConfigurationException("""
                    Only one wsdl2openapi plugin is allowed per API, but API '%s' declares more than one.
                    Each wsdl2openapi owns the paths and the OpenAPI document of its API.
                    Put each WSDL into its own api.""".formatted(proxy.getName()));
        }

        basePath = getBasePath();

        // init() can run more than once on the same instance: AbstractProxy.clone() and
        // RuleManager.replaceRule both init, and the clone shares this interceptor.
        routes.clear();
        requestTransformers.clear();

        try {
            ResolverMap resolverMap = router.getResolverMap();
            String resolvedWsdl = combine(router.getConfiguration().getUriFactory(), getBeanBaseLocation(), wsdl);
            definitions = parse(resolverMap, resolvedWsdl);
        } catch (Exception e) {
            throw new ConfigurationException("Cannot parse WSDL '%s': %s".formatted(wsdl, e.getMessage()));
        }

        xsdToSchema = new XsdToSchema(definitions);

        operationsByName = operations != null ? operations.getMap() : Map.of();

        for (OperationSettings settings : operationsByName.values()) {
            for (Interceptor i : settings.getFlow()) {
                i.init(router, proxy);
            }
        }

        var allOps = definitions.getPortTypes().stream()
                .flatMap(pt -> pt.getOperations().stream())
                .toList();
        var opsToExpose = operationsByName.isEmpty()
                ? allOps
                : allOps.stream().filter(op -> operationsByName.containsKey(op.getName())).toList();
        for (var op : opsToExpose) {
            OperationSettings opSettings = operationsByName.get(op.getName());
            String segment = (opSettings != null && opSettings.getPath() != null) ? opSettings.getPath() : camelToKebab(op.getName());
            String method = (opSettings != null) ? opSettings.getMethod().toUpperCase() : "POST";
            routes.add(new RouteEntry(buildPathPattern(segment), extractParamNames(segment), method, op.getName()));
            requestTransformers.put(op.getName(), new Json2SoapTransformer(definitions, op.getName()));
        }

        var generator = new Wsdl2OpenApiConverter(definitions, basePath, operationsByName);
        var openApiModel = generator.generate();
        var record = new OpenAPIRecord(openApiModel, new OpenAPISpec());

        publisher = new OpenAPIPublisherInterceptor(new LinkedHashMap<>());
        publisher.init(router);
        publisher.addRecord(record);

        registerApiDocsPaths();

        log.info("Loaded WSDL from {} with {} services", wsdl, definitions.getServices().size());
    }

    @Override
    public Outcome handleRequest(Exchange exc) {
        Outcome outcome = publisher.handleRequest(exc);
        if (outcome != CONTINUE) {
            return outcome;
        }

        String uri = exc.getRequest().getUri();
        var match = matchRoute(uri, exc.getRequest().getMethod());
        if (match.isPresent() && isValidOperation(match.get().operationName())) {
            return handleOperation(exc, match.get().operationName(), match.get().pathParams());
        }

        // The path is mapped, but not for this method: answer 405 instead of forwarding an
        // untransformed JSON body to the SOAP backend.
        var allowedMethods = allowedMethods(uri);
        if (!allowedMethods.isEmpty()) {
            return methodNotAllowed(exc, allowedMethods);
        }

        return CONTINUE;
    }

    /** The methods registered for the route(s) matching {@code path}, in declaration order. */
    List<String> allowedMethods(String path) {
        String segment = pathSegment(path);
        return routes.stream()
                .filter(entry -> entry.pathPattern().matcher(segment).matches())
                .map(RouteEntry::method)
                .distinct()
                .toList();
    }

    private Outcome methodNotAllowed(Exchange exc, List<String> allowedMethods) {
        String allow = String.join(", ", allowedMethods);
        Response response = user(router.getConfiguration().isProduction(), getDisplayName())
                .status(405)
                .title("Method not allowed")
                .detail("The requested path does not support %s. Allowed: %s.".formatted(exc.getRequest().getMethod(), allow))
                .build();
        response.getHeader().add("Allow", allow);
        exc.setResponse(response);
        return ABORT;
    }

    @Override
    public Outcome handleResponse(Exchange exc) {
        String operationName = exc.getProperty(operationPropertyKey, String.class);
        if (operationName == null) {
            return CONTINUE;
        }

        try {
            var outputMessages = definitions.getPortTypes().stream()
                    .flatMap(pt -> pt.getOperations().stream())
                    .filter(op -> operationName.equals(op.getName()))
                    .findFirst()
                    .map(op -> op.getMessagesByDirection(OUTPUT))
                    .orElse(List.of());
            var responseSchema = xsdToSchema.convertMessageParts(outputMessages);

            Soap2JsonTransformer responseTransformer = new Soap2JsonTransformer();
            String jsonResponse = responseTransformer.transform(exc.getResponse().getBodyAsStringDecoded(), responseSchema);

            exc.getResponse().setBodyContent(jsonResponse.getBytes(UTF_8));
            exc.getResponse().getHeader().setContentType(APPLICATION_JSON);

            OperationSettings opSettings = operationsByName.get(operationName);
            if (opSettings != null && !opSettings.getFlow().isEmpty()) {
                return router.getFlowController().invokeResponseHandlers(exc, opSettings.getFlow());
            }

        } catch (SoapFaultException fault) {
            log.debug("SOAP fault received for operation {}: [{}] {}", operationName, fault.getFaultCode(), fault.getFaultMessage());
            var pd = problemDetails("soap-fault", router.getConfiguration().isProduction())
                    .component(getDisplayName())
                    .status(fault.getHttpStatus())
                    .title(fault.getFaultMessage())
                    .topLevel("faultCode", fault.getFaultCode());
            if (fault.getSoapDetail() != null) {
                pd.internal("error", fault.getSoapDetail());
            }
            exc.setResponse(pd.build());
            return ABORT;
        } catch (Exception e) {
            log.error("Failed to transform SOAP to JSON for operation {}", operationName, e);
            internal(router.getConfiguration().isProduction(), getDisplayName())
                    .detail("Could not transform SOAP response to JSON")
                    .exception(e)
                    .buildAndSetResponse(exc);
            return ABORT;
        }

        return CONTINUE;
    }

    /**
     * Makes the OpenAPI document reachable next to the API's own path. Only base paths are added:
     * the key's path itself is never rewritten, so this stays safe when init() runs again on the
     * same proxy (APIProxy rebuilds its key on each init, so the list cannot accumulate either).
     */
    private void registerApiDocsPaths() {
        if (proxy.getKey() instanceof APIProxyKey apiKey) {
            apiKey.addBasePaths(new ArrayList<>(List.of(PATH, basePath)));
        }
    }

    private boolean isValidOperation(String operationName) {
        if (!operationsByName.isEmpty()) {
            return operationsByName.containsKey(operationName);
        }
        return definitions.getPortTypes().stream()
                .flatMap(pt -> pt.getOperations().stream())
                .anyMatch(op -> operationName.equals(op.getName()));
    }

    private String getBasePath() {
        if (proxy instanceof ServiceProxy sp) {
            var path = sp.getPath();
            if (path != null && path.getUri() != null) {
                return path.getUri();
            }
        }
        return "/";
    }

    /** Strips the base path and any query string, leaving the segment the routes are matched against. */
    private String pathSegment(String path) {
        String withoutBase = path.replaceFirst("^" + Pattern.quote(basePath), "");
        if (withoutBase.startsWith("/")) withoutBase = withoutBase.substring(1);
        return withoutBase.contains("?") ? withoutBase.substring(0, withoutBase.indexOf('?')) : withoutBase;
    }

    Optional<RouteMatch> matchRoute(String path, String method) {
        String segment = pathSegment(path);
        for (var entry : routes) {
            if (!entry.method().equalsIgnoreCase(method)) continue;
            Matcher m = entry.pathPattern().matcher(segment);
            if (m.matches()) {
                var params = new LinkedHashMap<String, String>();
                for (int i = 0; i < entry.paramNames().size(); i++) {
                    params.put(entry.paramNames().get(i), m.group(i + 1));
                }
                return Optional.of(new RouteMatch(entry.operationName(), params));
            }
        }
        return Optional.empty();
    }

    static List<String> extractParamNames(String template) {
        List<String> names = new ArrayList<>();
        Matcher m = Pattern.compile("\\{([^}]+)}").matcher(template);
        while (m.find()) names.add(m.group(1));
        return names;
    }

    static Pattern buildPathPattern(String template) {
        StringBuilder sb = new StringBuilder("^");
        Matcher m = Pattern.compile("\\{[^}]+}").matcher(template);
        int last = 0;
        while (m.find()) {
            sb.append(Pattern.quote(template.substring(last, m.start())));
            sb.append("([^/]+)");
            last = m.end();
        }
        sb.append(Pattern.quote(template.substring(last)));
        sb.append("$");
        return Pattern.compile(sb.toString());
    }

    private String mergePathParamsIntoJson(String existingBody, Map<String, String> pathParams) throws Exception {
        if (pathParams.isEmpty()) return existingBody;
        var merged = new LinkedHashMap<String, Object>(pathParams);
        if (existingBody != null && !existingBody.isBlank()) {
            var parsed = new ObjectMapper().readValue(existingBody, new TypeReference<Map<String, Object>>() {});
            merged.putAll(parsed);
        }
        return new ObjectMapper().writeValueAsString(merged);
    }

    private Outcome handleOperation(Exchange exc, String operationName, Map<String, String> pathParams) {
        OperationSettings opSettings = operationsByName.get(operationName);

        try {
            if (opSettings != null && !opSettings.getFlow().isEmpty()) {
                Outcome outcome = router.getFlowController().invokeRequestHandlers(exc, opSettings.getFlow());
                if (outcome != CONTINUE) return outcome;
            }

            String jsonBody = mergePathParamsIntoJson(exc.getRequest().getBodyAsStringDecoded(), pathParams);
            byte[] soapRequest = requestTransformers.get(operationName).transform(jsonBody);

            exc.getRequest().setBodyContent(soapRequest);
            exc.getRequest().setMethod("POST");
            exc.getRequest().getHeader().setContentType(TEXT_XML);
            exc.getRequest().getHeader().setSOAPAction(getSOAPAction(operationName));

            exc.setProperty(operationPropertyKey, operationName);

            String serviceAddress = getServiceAddress();
            if (serviceAddress != null) {
                exc.setDestinations(List.of(serviceAddress));
            }

        } catch (Exception e) {
            log.error("Failed to transform JSON to SOAP for operation {}", operationName, e);
            internal(router.getConfiguration().isProduction(), getDisplayName())
                    .detail("Could not transform JSON request to SOAP")
                    .exception(e)
                    .buildAndSetResponse(exc);
            return ABORT;
        }

        return CONTINUE;
    }

    private String getSOAPAction(String operationName) {
        return definitions.getBindings().stream()
                .flatMap(b -> b.getBindingOperations().stream())
                .filter(op -> op.getName().equals(operationName))
                .findFirst()
                .map(BindingOperation::getSoapAction)
                .orElse("");
    }

    private String getServiceAddress() {
        if (proxy instanceof AbstractServiceProxy asp) {
            String url = asp.getTargetURL();
            if (url != null && !url.isEmpty()) {
                return url;
            }
        }
        var services = definitions.getServices();
        if (services.isEmpty()) return null;
        var ports = services.getFirst().getPorts();
        if (ports.isEmpty()) return null;
        var address = ports.getFirst().getAddress();
        return address != null ? address.getLocation() : null;
    }

    public String getWsdl() {
        return wsdl;
    }

    /**
     * @description The WSDL (URL or file).
     * @example http://backend-service/service.wsdl
     */
    @Required
    @MCAttribute
    public void setWsdl(String wsdl) {
        this.wsdl = wsdl;
    }

    public OperationsConfig getOperations() {
        return operations;
    }

    /**
     * @description Named map of WSDL operations to expose via OpenAPI
     */
    @MCChildElement
    public void setOperations(OperationsConfig operations) {
        this.operations = operations;
    }

    @Override
    public String getShortDescription() {
        return "Exposes WSDL service at %s as HTTP/JSON/OpenAPI".formatted(wsdl);
    }
}
