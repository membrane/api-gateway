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
import com.predic8.membrane.core.resolver.ResolverMap;
import com.predic8.membrane.core.util.ConfigurationException;
import com.predic8.membrane.core.util.wsdl.parser.BindingOperation;
import com.predic8.membrane.core.util.wsdl.parser.Definitions;
import com.predic8.membrane.core.util.wsdl.parser.Operation;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.media.Schema;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.DOMException;

import java.util.*;
import java.util.stream.Collectors;

import static com.predic8.membrane.core.exceptions.ProblemDetails.*;
import static com.predic8.membrane.core.http.MimeType.APPLICATION_JSON;
import static com.predic8.membrane.core.http.MimeType.TEXT_XML;
import static com.predic8.membrane.core.interceptor.InterceptorUtil.getInterceptors;
import static com.predic8.membrane.core.interceptor.Outcome.ABORT;
import static com.predic8.membrane.core.interceptor.Outcome.CONTINUE;
import static com.predic8.membrane.core.interceptor.wsdl2openapi.OperationRouter.*;
import static com.predic8.membrane.core.interceptor.wsdl2openapi.Wsdl2OpenApiConverter.ApiInfo;
import static com.predic8.membrane.core.interceptor.wsdl2openapi.Wsdl2OpenApiConverter.FAULT_DETAILS_FIELD;
import static com.predic8.membrane.core.interceptor.wsdl2openapi.Wsdl2OpenApiConverter.OPERATION_ERROR_TYPE;
import static com.predic8.membrane.core.interceptor.wsdl2openapi.XsdDomUtil.camelToKebab;
import static com.predic8.membrane.core.openapi.serviceproxy.OpenAPIPublisherInterceptor.PATH;
import static com.predic8.membrane.core.resolver.ResolverMap.combine;
import static com.predic8.membrane.core.util.URLParamUtil.DuplicateKeyOrInvalidFormStrategy.ERROR;
import static com.predic8.membrane.core.util.URLParamUtil.getParams;
import static com.predic8.membrane.core.util.wsdl.parser.Definitions.parse;
import static com.predic8.membrane.core.util.wsdl.parser.Operation.Direction.OUTPUT;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.w3c.dom.DOMException.INVALID_CHARACTER_ERR;

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
 * <p>
 * The generated OpenAPI document's title is the enclosing <i>api</i>'s <code>name</code>.
 * </p>
 * <p>
 * Errors are returned as problem details documents (RFC 7807). An error the service reports becomes
 * a 500, and the content of an error the WSDL declares appears under <code>details</code>, keyed by
 * the name of the declared error. Errors the gateway itself detects carry the status that fits them,
 * such as 400 for a request it cannot map or 405 for a method the path does not support. Nothing in
 * the response reveals that a SOAP service is being called.
 * </p>
 * @yaml <pre><code>
 * api:
 *   name: Purchasing API
 *   port: 2000
 *   path: /purchasing
 *   flow:
 *     - wsdl2openapi:
 *         wsdl: http://backend-service/service.wsdl
 *         description: Look up and create purchase orders.
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

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private String wsdl;
    private String description;
    private String version;
    /** The api this plugin lives in. Set by init(), which rejects anything that is not an APIProxy. */
    private APIProxy apiProxy;
    private Definitions definitions;
    private XsdToSchema xsdToSchema;
    private String basePath;
    private OperationsConfig operations;
    private Map<String, OperationSettings> operationsByName = Map.of();
    /** Replaced wholesale by init(), so a re-init cannot leave routes of the previous WSDL behind. */
    private OperationRouter operationRouter = new OperationRouter("/", List.of());
    /** Set from the generated document in init(), so the document stays the single source of truth. */
    private Map<String, Set<String>> queryParamNames = Map.of();
    /** Per operation, the body property a URL parameter fills where its published name differs. */
    private Map<String, Map<String, String>> urlParamProperties = Map.of();
    /**
     * Everything the runtime needs to serve one operation. Built once per operation in init(): the
     * WSDL does not change, and every request and response needs all three.
     *
     * @param faultDetailSchema types the content of a SOAP fault detail, one property per fault the
     *                          operation declares; empty for an operation that declares none, in
     *                          which case the detail is still converted, just with every scalar as
     *                          a string.
     */
    record OperationRuntime(Json2SoapTransformer requestTransformer,
                            Schema<?> responseSchema,
                            Schema<?> faultDetailSchema) {}

    /** Replaced wholesale by init(), keyed by operation name — one entry per route. */
    private Map<String, OperationRuntime> operationRuntimes = Map.of();
    private OpenAPIPublisherInterceptor publisher;

    private final String instanceId = UUID.randomUUID().toString();
    private final String operationPropertyKey = "wsdl2openapi.operation." + instanceId;

    public Wsdl2OpenapiInterceptor() {
        name = "wsdl2openapi";
    }

    @Override
    public void init() {
        super.init();

        apiProxy = validateAndGetApiProxy();
        basePath = getBasePath();

        definitions = parseWsdl();
        operationsByName = operations != null ? operations.getMap() : Map.of();
        initOperationFlows();

        // One converter for the document and the runtime alike: the schemas used to convert a
        // response refer to the named types by the very names the document publishes them under.
        var wsdl2OpenApi = new Wsdl2OpenApiConverter(definitions, basePath, operationsByName,
                new ApiInfo(apiProxy.getName(), description, version));
        xsdToSchema = wsdl2OpenApi.getSchemaConverter();

        // init() can run more than once on the same instance: AbstractProxy.clone() and
        // RuleManager.replaceRule both init, and the clone shares this interceptor. Both the router
        // and the runtimes are replaced wholesale, so nothing of the previous WSDL can survive.
        operationRouter = new OperationRouter(basePath, buildRoutes(definitions, operationsByName));
        operationRuntimes = buildOperationRuntimes(operationRouter.getRoutes());

        var openApiModel = wsdl2OpenApi.generate();
        queryParamNames = collectQueryParamNames(openApiModel);
        urlParamProperties = wsdl2OpenApi.getUrlParamProperties();
        publisher = createPublisher(openApiModel);

        registerApiDocsPaths();

        log.info("Loaded WSDL from {} with {} services", wsdl, definitions.getServices().size());
    }

    private APIProxy validateAndGetApiProxy() {
        if (!(proxy instanceof APIProxy api)) {
            throw new ConfigurationException("""
                    The wsdl2openapi plugin can only be used within an api, but '%s' is not one.
                    It publishes an OpenAPI document at %s, which only an api can route to.""".formatted(proxy.getName(), PATH));
        }

        // APIProxy.init() has already run and placed an OpenAPIPublisherInterceptor into the flow
        // if the api declares openapi documents or an openapiPublisher — both publish at PATH,
        // and this plugin's own publisher runs first, which would hide those documents.
        if (!getInterceptors(proxy.getFlow(), OpenAPIPublisherInterceptor.class).isEmpty()) {
            throw new ConfigurationException("""
                    The wsdl2openapi plugin cannot be combined with openapi documents in the same api ('%s').
                    Both publish at %s, and wsdl2openapi generates its document from the WSDL.
                    Put the openapi documents into a separate api.""".formatted(proxy.getName(), PATH));
        }

        if (getInterceptors(proxy.getFlow(), Wsdl2OpenapiInterceptor.class).size() > 1) {
            throw new ConfigurationException("""
                    Only one wsdl2openapi plugin is allowed per API, but API '%s' declares more than one.
                    Each wsdl2openapi owns the paths and the OpenAPI document of its API.
                    Put each WSDL into its own api.""".formatted(proxy.getName()));
        }

        return api;
    }

    private Definitions parseWsdl() {
        try {
            ResolverMap resolverMap = router.getResolverMap();
            String resolvedWsdl = combine(router.getConfiguration().getUriFactory(), getBeanBaseLocation(), wsdl);
            return parse(resolverMap, resolvedWsdl);
        } catch (Exception e) {
            throw new ConfigurationException("Cannot parse WSDL '%s': %s".formatted(wsdl, e.getMessage()), e);
        }
    }

    private void initOperationFlows() {
        for (OperationSettings settings : operationsByName.values()) {
            for (Interceptor i : settings.getFlow()) {
                i.init(router, proxy);
            }
        }
    }

    /** One route per exposed operation: all of them, or only those named in {@code operationsByName}. */
    static List<RouteEntry> buildRoutes(Definitions definitions, Map<String, OperationSettings> operationsByName) {
        return definitions.getOperations().stream()
                .map(op -> op.getName())
                .filter(name -> operationsByName.isEmpty() || operationsByName.containsKey(name))
                .map(name -> toRoute(name, operationsByName.get(name)))
                .toList();
    }

    private static RouteEntry toRoute(String operationName, OperationSettings settings) {
        final String segment = (settings != null && settings.getPath() != null) ? settings.getPath() : camelToKebab(operationName);
        final String method = (settings != null) ? settings.getMethod().toUpperCase() : "POST";
        return new RouteEntry(buildPathPattern(segment), extractParamNames(segment), method, operationName);
    }

    private Map<String, OperationRuntime> buildOperationRuntimes(List<RouteEntry> routes) {
        var runtimes = new LinkedHashMap<String, OperationRuntime>();
        for (RouteEntry route : routes) {
            runtimes.put(route.operationName(), buildOperationRuntime(route.operationName()));
        }
        return Map.copyOf(runtimes);
    }

    private OperationRuntime buildOperationRuntime(String operationName) {
        Optional<Operation> wsdlOp = definitions.findOperation(operationName);
        return new OperationRuntime(
                new Json2SoapTransformer(definitions, operationName, xsdToSchema.getSchemasByNamespace()),
                xsdToSchema.convertMessageParts(wsdlOp.map(op -> op.getMessagesByDirection(OUTPUT)).orElse(List.of())),
                xsdToSchema.convertFaultDetail(wsdlOp.map(Operation::getFaults).orElse(List.of())));
    }

    /** The routes built by the last {@code init()}. */
    OperationRouter getOperationRouter() {
        return operationRouter;
    }

    private OpenAPIPublisherInterceptor createPublisher(OpenAPI openApiModel) {
        var publisherInterceptor = new OpenAPIPublisherInterceptor(new LinkedHashMap<>());
        publisherInterceptor.init(router);
        publisherInterceptor.addRecord(new OpenAPIRecord(openApiModel, new OpenAPISpec()));
        return publisherInterceptor;
    }

    @Override
    public Outcome handleRequest(Exchange exc) {
        Outcome outcome = publisher.handleRequest(exc);
        if (outcome != CONTINUE) {
            return outcome;
        }

        String uri = exc.getRequest().getUri();
        var match = operationRouter.match(uri, exc.getRequest().getMethod());
        if (match.isPresent()) {
            return handleOperation(exc, match.get().operationName(), match.get().pathParams());
        }

        // The path is mapped, but not for this method: answer 405 instead of forwarding an
        // untransformed JSON body to the SOAP backend.
        var allowedMethods = operationRouter.allowedMethods(uri);
        if (!allowedMethods.isEmpty()) {
            return methodNotAllowed(exc, allowedMethods);
        }

        return CONTINUE;
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
            // The property is set by the request path of this very instance after a route matched,
            // so the operation always has a runtime.
            OperationRuntime runtime = operationRuntimes.get(operationName);
            String jsonResponse = new Soap2JsonTransformer(xsdToSchema.getComponents())
                    .transform(exc.getResponse().getBodyAsStringDecoded(),
                            runtime.responseSchema(),
                            runtime.faultDetailSchema());

            exc.getResponse().setBodyContent(jsonResponse.getBytes(UTF_8));
            exc.getResponse().getHeader().setContentType(APPLICATION_JSON);

            OperationSettings opSettings = operationsByName.get(operationName);
            if (opSettings != null && !opSettings.getFlow().isEmpty()) {
                return router.getFlowController().invokeResponseHandlers(exc, opSettings.getFlow());
            }

        } catch (SoapFaultException fault) {
            log.debug("SOAP fault received for operation {}: [{}] {}", operationName, fault.getFaultCode(), fault.getFaultMessage());
            exc.setResponse(soapFaultResponse(fault));
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
     * A fault becomes a 500: the gateway cannot tell whether the backend rejected the input, broke,
     * or timed out, so the most generic status is the only honest one. Nothing in the response names
     * the technology behind the API — the fault code and the backend's fault message stay internal,
     * development-mode aids, because an operator may not want clients to know a legacy service sits
     * behind the gateway, and a faultstring can carry a class name or an internal host name.
     */
    private Response soapFaultResponse(SoapFaultException fault) {
        var pd = problemDetails(OPERATION_ERROR_TYPE, router.getConfiguration().isProduction())
                .component(getDisplayName())
                .status(500)
                .title("Operation failed")
                .detail("The service could not complete the operation.")
                .internal("faultCode", fault.getFaultCode())
                .internal("faultMessage", fault.getFaultMessage());
        if (fault.getSoapDetail() != null) {
            pd.topLevel(FAULT_DETAILS_FIELD, fault.getSoapDetail());
        }
        return pd.build();
    }

    /**
     * Makes the OpenAPI document reachable next to the API's own path. Only base paths are added:
     * the key's path itself is never rewritten, so this stays safe when init() runs again on the
     * same proxy (APIProxy rebuilds its key on each init, so the list cannot accumulate either).
     * PATH goes into the key's api docs paths so that it stays reachable even when the API has a
     * custom path configured.
     */
    private void registerApiDocsPaths() {
        if (proxy.getKey() instanceof APIProxyKey apiKey) {
            apiKey.addApiDocsPaths(new ArrayList<>(List.of(PATH)));
            apiKey.addBasePaths(new ArrayList<>(List.of(basePath)));
        }
    }

    private String getBasePath() {
        var path = apiProxy.getPath();
        if (path != null && path.getUri() != null) {
            return path.getUri();
        }
        return "/";
    }

    /**
     * Adds the values taken from the URL to the JSON body. Query parameters carry the input fields a
     * bodyless method has nowhere else to put; the path parameters are applied last, because the URL
     * path selected the resource and neither the query string nor a body that carries the same field
     * — it is not part of the published request schema, but nothing stops a client from sending it —
     * must silently address another one.
     */
    static String mergeUrlParamsIntoJson(String existingBody, Map<String, String> queryParams,
                                         Map<String, String> pathParams) throws Exception {
        if (queryParams.isEmpty() && pathParams.isEmpty()) return existingBody;
        var merged = new LinkedHashMap<String, Object>();
        if (existingBody != null && !existingBody.isBlank()) {
            merged.putAll(MAPPER.readValue(existingBody, new TypeReference<Map<String, Object>>() {}));
        }
        merged.putAll(queryParams);
        merged.putAll(pathParams);
        return MAPPER.writeValueAsString(merged);
    }

    /**
     * Renames the values taken from the URL to the body properties they fill. The two differ for an
     * XSD attribute: the document publishes it under its plain name, because an {@code @} in a
     * parameter name would have to be percent-encoded by every client, while the JSON the SOAP
     * transformation reads expects the {@code "@"}-prefixed property.
     */
    private Map<String, String> toBodyProperties(Map<String, String> urlParams, String operationName) {
        Map<String, String> properties = urlParamProperties.getOrDefault(operationName, Map.of());
        if (properties.isEmpty() || urlParams.isEmpty()) return urlParams;
        var renamed = new LinkedHashMap<String, String>();
        urlParams.forEach((name, value) -> renamed.put(properties.getOrDefault(name, name), value));
        return renamed;
    }

    /**
     * The query parameters of the request that the operation actually declares. Anything else a
     * client appends stays out of the SOAP request instead of being sent to the service unchecked.
     */
    private Map<String, String> declaredQueryParams(Exchange exc, String operationName) throws Exception {
        Set<String> declared = queryParamNames.getOrDefault(operationName, Set.of());
        if (declared.isEmpty()) return Map.of();
        var params = new LinkedHashMap<>(parseQueryParams(exc));
        params.keySet().retainAll(declared);
        return params;
    }

    /**
     * Parses the query string, rejecting duplicate keys and malformed pairs. Those are client
     * mistakes, so the {@link RuntimeException} {@code getParams} raises for them is translated
     * into a distinct exception instead of ending up as a transformation failure.
     */
    private Map<String, String> parseQueryParams(Exchange exc) throws Exception {
        try {
            return getParams(router.getConfiguration().getUriFactory(), exc, ERROR);
        } catch (RuntimeException e) {
            throw new InvalidQueryStringException(e);
        }
    }

    /** A query string with duplicate keys or malformed key/value pairs. */
    private static class InvalidQueryStringException extends Exception {
        private InvalidQueryStringException(Throwable cause) {
            super(cause);
        }
    }

    /** The query parameters the generated document declares, per operation. */
    static Map<String, Set<String>> collectQueryParamNames(OpenAPI api) {
        var result = new LinkedHashMap<String, Set<String>>();
        if (api.getPaths() == null) return result;
        api.getPaths().values().stream()
                .flatMap(pathItem -> pathItem.readOperations().stream())
                .forEach(op -> {
                    if (op.getParameters() == null) return;
                    Set<String> names = op.getParameters().stream()
                            .filter(p -> "query".equals(p.getIn()))
                            .map(io.swagger.v3.oas.models.parameters.Parameter::getName)
                            .collect(Collectors.toCollection(LinkedHashSet::new));
                    if (!names.isEmpty()) result.put(op.getOperationId(), names);
                });
        return result;
    }

    private Outcome handleOperation(Exchange exc, String operationName, Map<String, String> pathParams) {
        OperationSettings opSettings = operationsByName.get(operationName);

        try {
            if (opSettings != null && !opSettings.getFlow().isEmpty()) {
                Outcome outcome = router.getFlowController().invokeRequestHandlers(exc, opSettings.getFlow());
                if (outcome != CONTINUE) return outcome;
            }

            String jsonBody = mergeUrlParamsIntoJson(exc.getRequest().getBodyAsStringDecoded(),
                    toBodyProperties(declaredQueryParams(exc, operationName), operationName),
                    toBodyProperties(pathParams, operationName));
            byte[] soapRequest = operationRuntimes.get(operationName).requestTransformer().transform(jsonBody);

            exc.getRequest().setBodyContent(soapRequest);
            exc.getRequest().setMethod("POST");
            exc.getRequest().getHeader().setContentType(TEXT_XML);
            exc.getRequest().getHeader().setSOAPAction(getSOAPAction(operationName));

            exc.setProperty(operationPropertyKey, operationName);

            String serviceAddress = getServiceAddress();
            if (serviceAddress != null) {
                exc.setDestinations(List.of(serviceAddress));
            }

        } catch (InvalidQueryStringException e) {
            log.debug("Cannot parse the query string of the request for operation {}", operationName, e);
            user(router.getConfiguration().isProduction(), getDisplayName())
                    .status(400)
                    .title("Invalid query string")
                    .detail("The query string could not be parsed. Check for duplicate or malformed parameters.")
                    .exception(e)
                    .buildAndSetResponse(exc);
            return ABORT;
        } catch (DOMException e) {
            // A field name the JSON body carries is not a legal XML name — a client mistake, so a
            // 400 rather than the 500 any other transformation failure gets. Every other DOM error
            // is the gateway's own problem and takes that generic path.
            if (e.code != INVALID_CHARACTER_ERR) return transformationFailed(exc, operationName, e);
            log.debug("Cannot map a field name of the request body to XML for operation {}", operationName, e);
            user(router.getConfiguration().isProduction(), getDisplayName())
                    .status(400)
                    .title("Invalid field name")
                    .detail("A field name in the request body cannot be mapped to XML.")
                    .exception(e)
                    .buildAndSetResponse(exc);
            return ABORT;
        } catch (Exception e) {
            return transformationFailed(exc, operationName, e);
        }

        return CONTINUE;
    }

    private Outcome transformationFailed(Exchange exc, String operationName, Exception e) {
        log.error("Failed to transform JSON to SOAP for operation {}", operationName, e);
        internal(router.getConfiguration().isProduction(), getDisplayName())
                .detail("Could not transform JSON request to SOAP")
                .exception(e)
                .buildAndSetResponse(exc);
        return ABORT;
    }

    private String getSOAPAction(String operationName) {
        return definitions.findBindingOperation(operationName)
                .map(BindingOperation::getSoapAction)
                .orElse("");
    }

    private String getServiceAddress() {
        String url = apiProxy.getTargetURL();
        if (url != null && !url.isEmpty()) {
            return url;
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

    public String getDescription() {
        return description;
    }

    /**
     * @description API-level description, shown above the auto-generated note about how this
     * OpenAPI document was produced.
     * @example Look up partner records by ID.
     */
    @MCAttribute
    public void setDescription(String description) {
        this.description = description;
    }

    public String getVersion() {
        return version;
    }

    /**
     * @description Version of the generated OpenAPI document.
     * @default 1.0.0
     * @example 2.1.0
     */
    @MCAttribute
    public void setVersion(String version) {
        this.version = version;
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
