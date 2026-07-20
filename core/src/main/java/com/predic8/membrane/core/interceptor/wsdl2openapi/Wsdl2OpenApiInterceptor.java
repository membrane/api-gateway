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

import com.predic8.membrane.annot.*;
import com.predic8.membrane.core.exchange.*;
import com.predic8.membrane.core.interceptor.*;
import com.predic8.membrane.core.openapi.serviceproxy.*;
import com.predic8.membrane.core.proxies.*;
import com.predic8.membrane.core.resolver.*;
import com.predic8.membrane.core.util.*;
import com.predic8.membrane.core.util.wsdl.parser.*;
import org.slf4j.*;

import java.util.*;
import java.util.regex.*;

import static com.predic8.membrane.core.exceptions.ProblemDetails.*;
import static com.predic8.membrane.core.http.MimeType.*;
import static com.predic8.membrane.core.http.Response.statusCode;
import static com.predic8.membrane.core.interceptor.Outcome.*;
import static com.predic8.membrane.core.openapi.serviceproxy.OpenAPIPublisherInterceptor.*;
import static com.predic8.membrane.core.openapi.util.OpenAPIUtil.*;
import static com.predic8.membrane.core.resolver.ResolverMap.combine;
import static com.predic8.membrane.core.util.wsdl.parser.Definitions.parse;
import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * @description <p>
 * The <i>wsdl2openapi</i> interceptor exposes SOAP/WSDL services via HTTP/JSON/OpenAPI.
 * It automatically converts JSON requests to SOAP XML and SOAP XML responses back to JSON.
 * </p>
 * @yaml <pre><code>
 * api:
 *   port: 2000
 *   path: /purchasing
 *   flow:
 *     - wsdl2openapi:
 *         wsdl: http://backend-service/service.wsdl
 * </code></pre>
 * @topic 6. Web Services with SOAP and WSDL
 */
@MCElement(name = "wsdl2openapi")
public class Wsdl2OpenApiInterceptor extends AbstractInterceptor {

    private static final Logger log = LoggerFactory.getLogger(Wsdl2OpenApiInterceptor.class);

    private String wsdl;
    private Definitions definitions;
    private XsdToSchema xsdToSchema;
    private String basePath;
    private List<OperationConfig> operations = new ArrayList<>();
    private final Map<String, OperationConfig> operationsByName = new LinkedHashMap<>();
    private OpenAPIPublisherInterceptor publisher;

    public Wsdl2OpenApiInterceptor() {
        name = "wsdl2openapi";
    }

    @Override
    public void init() {
        super.init();

        basePath = getBasePath();

        try {
            ResolverMap resolverMap = router.getResolverMap();
            String resolvedWsdl = combine(router.getConfiguration().getUriFactory(), getBeanBaseLocation(), wsdl);
            definitions = parse(resolverMap, resolvedWsdl);
        } catch (Exception e) {
            throw new ConfigurationException("Cannot parse WSDL '%s': %s".formatted(wsdl, e.getMessage()));
        }

        xsdToSchema = new XsdToSchema(definitions);

        for (OperationConfig op : operations) {
            if (op.getName() != null) {
                operationsByName.put(op.getName(), op);
            }
        }

        var generator = new OpenApiGenerator(definitions, basePath, operationsByName);
        var openApiModel = generator.generate();
        var record = new OpenAPIRecord(openApiModel, new OpenAPISpec());
        publisher = new OpenAPIPublisherInterceptor(Map.of(getIdFromAPI(openApiModel), record));
        publisher.init(router);

        registerApiDocsPaths();

        log.info("Loaded WSDL from {} with {} services", wsdl, definitions.getServices().size());
    }

    @Override
    public Outcome handleRequest(Exchange exc) {
        Outcome outcome = publisher.handleRequest(exc);
        if (outcome != CONTINUE) {
            return outcome;
        }

        String operationName = extractOperationName(exc.getRequest().getUri());
        if (isValidOperation(operationName)) {
            return handleOperation(exc, operationName);
        }

        return CONTINUE;
    }

    @Override
    public Outcome handleResponse(Exchange exc) {
        String operationName = (String) exc.getProperty("wsdl2openapi.operation");
        if (operationName == null) {
            return CONTINUE;
        }

        try {
            var outputMessages = definitions.getPortTypes().stream()
                    .flatMap(pt -> pt.getOperations().stream())
                    .filter(op -> operationName.equals(op.getName()))
                    .findFirst()
                    .map(op -> op.getMessagesByDirection(Operation.Direction.OUTPUT))
                    .orElse(List.of());
            var responseSchema = xsdToSchema.convertMessageParts(outputMessages);

            Soap2JsonTransformer responseTransformer = new Soap2JsonTransformer();
            String jsonResponse = responseTransformer.transform(exc.getResponse().getBodyAsStringDecoded(), responseSchema);

            exc.getResponse().setBodyContent(jsonResponse.getBytes(UTF_8));
            exc.getResponse().getHeader().setContentType(APPLICATION_JSON);

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

    private void registerApiDocsPaths() {
        RuleKey key = proxy.getKey();
        if (key instanceof APIProxyKey apiKey) {
            apiKey.addBasePaths(new ArrayList<>(List.of(PATH, basePath)));
        } else if (key instanceof AbstractRuleKey ark && ark.isUsePathPattern()) {
            String existing = ark.getPath();
            if (existing != null) {
                ark.setPathRegExp(true);
                ark.setPath("(" + Pattern.quote(existing) + ".*|" + Pattern.quote(PATH) + ".*)");
            }
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

    String extractOperationName(String path) {
        String withoutBase = path.replaceFirst("^" + basePath, "");
        if (withoutBase.startsWith("/")) {
            withoutBase = withoutBase.substring(1);
        }
        String segment = withoutBase.contains("?") ? withoutBase.substring(0, withoutBase.indexOf('?')) : withoutBase;
        return kebabToCamel(segment);
    }

    private String kebabToCamel(String kebab) {
        var result = new StringBuilder();
        boolean capitalizeNext = false;

        for (char c : kebab.toCharArray()) {
            if (c == '-') {
                capitalizeNext = true;
            } else {
                result.append(capitalizeNext ? Character.toUpperCase(c) : c);
                capitalizeNext = false;
            }
        }

        return result.toString();
    }

    private Outcome handleOperation(Exchange exc, String operationName) {
        if (!exc.getRequest().isPOSTRequest()) {
            exc.setResponse(statusCode(405)
                    .header("Allow", "POST")
                    .body("Method not allowed. Use POST.")
                    .build());
            return RETURN;
        }

        try {
            Json2SoapTransformer requestTransformer = new Json2SoapTransformer(definitions, operationName);
            byte[] soapRequest = requestTransformer.transform(exc.getRequest().getBodyAsStringDecoded());

            exc.getRequest().setBodyContent(soapRequest);
            exc.getRequest().getHeader().setContentType(TEXT_XML);
            exc.getRequest().getHeader().setSOAPAction(getSOAPAction(operationName));

            exc.setProperty("wsdl2openapi.operation", operationName);

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

    public List<OperationConfig> getOperations() {
        return operations;
    }

    /**
     * @description List of operations to expose via OpenAPI
     */
    @MCChildElement
    public void setOperations(List<OperationConfig> operations) {
        this.operations = operations;
    }

    @Override
    public String getShortDescription() {
        return "Exposes WSDL service at %s as HTTP/JSON/OpenAPI".formatted(wsdl);
    }
}
