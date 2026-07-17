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
import com.predic8.membrane.core.http.*;
import com.predic8.membrane.core.interceptor.*;
import com.predic8.membrane.core.proxies.*;
import com.predic8.membrane.core.resolver.*;
import com.predic8.membrane.core.util.*;
import com.predic8.membrane.core.util.wsdl.parser.*;
import groovy.text.*;
import org.slf4j.*;

import java.io.*;
import java.util.*;

import static com.predic8.membrane.core.exceptions.ProblemDetails.*;
import static com.predic8.membrane.core.http.MimeType.*;
import static com.predic8.membrane.core.interceptor.Outcome.*;
import static com.predic8.membrane.core.openapi.util.Utils.*;

/**
 * @description <p>
 * The <i>wsdl2openapi</i> interceptor exposes SOAP/WSDL services via HTTP/JSON/OpenAPI.
 * It automatically converts JSON requests to SOAP XML and SOAP XML responses back to JSON.
 * This is NOT REST - it's OpenAPI as a Remote Procedure Call mechanism.
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

    private static final String API_DOCS_PATH = "/api-docs";
    private static final String SPEC_PATH = "/api-docs/spec.yaml";

    private String wsdl;
    private Definitions definitions;
    private String basePath;
    private List<OperationConfig> operations = new ArrayList<>();
    private Map<String, OperationConfig> operationsByName = new LinkedHashMap<>();
    private Template swaggerUiTemplate;

    public Wsdl2OpenApiInterceptor() {
        name = "wsdl2openapi";
    }

    @Override
    public void init() {
        super.init();

        if (wsdl == null) {
            throw new ConfigurationException("<wsdl2openapi> requires a 'wsdl' attribute");
        }

        // Determine base path from proxy
        basePath = getBasePath();

        // Parse WSDL
        try {
            ResolverMap resolverMap = router.getResolverMap();
            String resolvedWsdl = ResolverMap.combine(router.getConfiguration().getUriFactory(), getBeanBaseLocation(), wsdl);
            definitions = Definitions.parse(resolverMap, resolvedWsdl);
        } catch (Exception e) {
            throw new ConfigurationException("Cannot parse WSDL '%s': %s".formatted(wsdl, e.getMessage()));
        }

        // Load Swagger UI template
        try {
            swaggerUiTemplate = new StreamingTemplateEngine().createTemplate(
                    new InputStreamReader(getResourceAsStream(this, "/openapi/swagger-ui.html")));
        } catch (Exception e) {
            throw new ConfigurationException("Cannot load Swagger UI template: " + e.getMessage());
        }

        // Build lookup map from operation list
        for (OperationConfig op : operations) {
            if (op.getName() != null) {
                operationsByName.put(op.getName(), op);
            }
        }

        log.info("Loaded WSDL from {} with {} services", wsdl, definitions.getServices().size());
    }

    private String getBasePath() {
        if (proxy instanceof ServiceProxy sp) {
            var path = sp.getPath();
            if (path != null) {
                return path.getUri();
            }
        }
        return "/";
    }

    @Override
    public Outcome handleRequest(Exchange exc) {
        String requestPath = exc.getRequest().getUri();

        // Serve Swagger UI
        if (requestPath.endsWith(API_DOCS_PATH)) {
            return serveSwaggerUi(exc);
        }

        // Serve OpenAPI spec YAML
        if (requestPath.endsWith(SPEC_PATH)) {
            return serveOpenApiSpec(exc);
        }

        // Route to operation handler
        String operationName = extractOperationName(requestPath);
        if (operationName != null && operationsByName.containsKey(operationName)) {
            return handleOperation(exc, operationName);
        }

        return CONTINUE;
    }

    private Outcome serveSwaggerUi(Exchange exc) {
        String specUrl = basePath + SPEC_PATH;
        String title = definitions.getServices().isEmpty() ? "SOAP Service" : definitions.getServices().getFirst().getName();

        Map<String, Object> ctx = new HashMap<>();
        ctx.put("openApiUrl", specUrl);
        ctx.put("openApiTitle", title);

        exc.setResponse(Response.ok()
                .contentType(TEXT_HTML_UTF8)
                .body(swaggerUiTemplate.make(ctx).toString())
                .build());
        return RETURN;
    }

    private Outcome serveOpenApiSpec(Exchange exc) {
        OpenApiGenerator generator = new OpenApiGenerator(definitions, basePath, operationsByName);
        String yaml = generator.generateYaml();

        exc.setResponse(Response.ok()
                .yaml()
                .body(yaml)
                .build());
        return RETURN;
    }

    private String extractOperationName(String path) {
        // Extract operation name from path like /purchasing/get-orders -> getOrders
        String withoutBase = path.replaceFirst("^" + basePath, "");
        if (withoutBase.startsWith("/")) {
            withoutBase = withoutBase.substring(1);
        }

        // Convert kebab-case to camelCase
        return kebabToCamel(withoutBase);
    }

    private String kebabToCamel(String kebab) {
        StringBuilder result = new StringBuilder();
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
            exc.setResponse(Response.statusCode(405)
                    .header("Allow", "POST")
                    .body("Method not allowed. Use POST.")
                    .build());
            return RETURN;
        }

        try {
            // Transform JSON to SOAP
            Json2SoapTransformer requestTransformer = new Json2SoapTransformer(definitions, operationName);
            byte[] soapRequest = requestTransformer.transform(exc.getRequest().getBodyAsStringDecoded());

            // Update request for SOAP backend
            exc.getRequest().setBodyContent(soapRequest);
            exc.getRequest().getHeader().setContentType(TEXT_XML);
            exc.getRequest().getHeader().setSOAPAction(getSOAPAction(operationName));

            // Store operation name for response transformation
            exc.setProperty("wsdl2openapi.operation", operationName);

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

    @Override
    public Outcome handleResponse(Exchange exc) {
        String operationName = (String) exc.getProperty("wsdl2openapi.operation");
        if (operationName == null) {
            return CONTINUE;
        }

        try {
            // Transform SOAP response to JSON
            Soap2JsonTransformer responseTransformer = new Soap2JsonTransformer(definitions, operationName);
            String jsonResponse = responseTransformer.transform(exc.getResponse().getBodyAsStringDecoded());

            exc.getResponse().setBodyContent(jsonResponse.getBytes("UTF-8"));
            exc.getResponse().getHeader().setContentType(APPLICATION_JSON);

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

    private String getSOAPAction(String operationName) {
        // Try to find the SOAP action from the binding
        return definitions.getBindings().stream()
                .flatMap(b -> b.getBindingOperations().stream())
                .filter(op -> op.getName().equals(operationName))
                .findFirst()
                .map(BindingOperation::getSoapAction)
                .orElse("");
    }

    public String getWsdl() {
        return wsdl;
    }

    /**
     * @description The WSDL (URL or file).
     * @example http://backend-service/service.wsdl
     */
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
