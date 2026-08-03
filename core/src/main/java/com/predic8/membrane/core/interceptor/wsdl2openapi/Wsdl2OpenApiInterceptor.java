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

import com.predic8.membrane.annot.MCAttribute;
import com.predic8.membrane.annot.MCChildElement;
import com.predic8.membrane.annot.MCElement;
import com.predic8.membrane.annot.Required;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.interceptor.AbstractInterceptor;
import com.predic8.membrane.core.interceptor.Interceptor;
import com.predic8.membrane.core.interceptor.Outcome;
import com.predic8.membrane.core.openapi.OpenAPIValidator;
import com.predic8.membrane.core.openapi.serviceproxy.APIProxyKey;
import com.predic8.membrane.core.openapi.serviceproxy.OpenAPIPublisherInterceptor;
import com.predic8.membrane.core.openapi.serviceproxy.OpenAPIRecord;
import com.predic8.membrane.core.openapi.serviceproxy.OpenAPISpec;
import com.predic8.membrane.core.openapi.validators.ValidationErrors;
import com.predic8.membrane.core.proxies.AbstractRuleKey;
import com.predic8.membrane.core.proxies.RuleKey;
import com.predic8.membrane.core.proxies.ServiceProxy;
import com.predic8.membrane.core.resolver.ResolverMap;
import com.predic8.membrane.core.util.ConfigurationException;
import com.predic8.membrane.core.util.wsdl.parser.BindingOperation;
import com.predic8.membrane.core.util.wsdl.parser.Definitions;
import com.predic8.membrane.core.util.wsdl.parser.Operation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import static com.predic8.membrane.core.exceptions.ProblemDetails.internal;
import static com.predic8.membrane.core.exceptions.ProblemDetails.problemDetails;
import static com.predic8.membrane.core.exceptions.ProblemDetails.user;
import static com.predic8.membrane.core.http.MimeType.APPLICATION_JSON;
import static com.predic8.membrane.core.http.MimeType.TEXT_XML;
import static com.predic8.membrane.core.http.Response.statusCode;
import static com.predic8.membrane.core.interceptor.Interceptor.Flow.REQUEST;
import static com.predic8.membrane.core.interceptor.Interceptor.Flow.RESPONSE;
import static com.predic8.membrane.core.interceptor.Outcome.*;
import static com.predic8.membrane.core.interceptor.wsdl2openapi.XsdDomUtil.camelToKebab;
import static com.predic8.membrane.core.openapi.serviceproxy.OpenAPIPublisherInterceptor.PATH;
import static com.predic8.membrane.core.openapi.util.OpenAPIUtil.getIdFromAPI;
import static com.predic8.membrane.core.openapi.util.Utils.getOpenapiValidatorRequest;
import static com.predic8.membrane.core.openapi.util.Utils.getOpenapiValidatorResponse;
import static com.predic8.membrane.core.openapi.validators.ValidationErrors.Direction.REQUEST;
import static com.predic8.membrane.core.resolver.ResolverMap.combine;
import static com.predic8.membrane.core.util.wsdl.parser.Definitions.parse;
import static com.predic8.membrane.core.util.wsdl.parser.Operation.Direction.OUTPUT;
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
    private boolean validateRequests = false;
    private boolean validateResponses = false;
    private boolean validationDetails = true;
    private boolean maskValues = false;
    private Definitions definitions;
    private XsdToSchema xsdToSchema;
    private String basePath;
    private List<OperationConfig> operations = new ArrayList<>();
    private final Map<String, OperationConfig> operationsByName = new LinkedHashMap<>();
    private final Map<String, String> kebabToOperation = new LinkedHashMap<>();
    private final Map<String, Json2SoapTransformer> requestTransformers = new LinkedHashMap<>();
    private OpenAPIPublisherInterceptor publisher;
    private OpenAPIValidator openApiValidator;

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

        for (OperationConfig opConfig : operations) {
            for (Interceptor i : opConfig.getFlow()) {
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
            OperationConfig opConfig = operationsByName.get(op.getName());
            String segment = (opConfig != null && opConfig.getPath() != null) ? opConfig.getPath() : camelToKebab(op.getName());
            kebabToOperation.put(segment, op.getName());
            requestTransformers.put(op.getName(), new Json2SoapTransformer(definitions, op.getName()));
        }

        var generator = new Wsdl2OpenApiConverter(definitions, basePath, operationsByName);
        var openApiModel = generator.generate();
        var record = new OpenAPIRecord(openApiModel, new OpenAPISpec());

        if (validateRequests || validateResponses) {
            openApiValidator = new OpenAPIValidator(router.getConfiguration().getUriFactory(), record);
        }

        publisher = proxy.getFlow().stream()
                .filter(i -> i instanceof Wsdl2OpenApiInterceptor w && w.publisher != null)
                .map(i -> ((Wsdl2OpenApiInterceptor) i).publisher)
                .findFirst()
                .orElseGet(() -> {
                    var p = new OpenAPIPublisherInterceptor(new LinkedHashMap<>());
                    p.init(router);
                    return p;
                });
        publisher.addRecord(getIdFromAPI(openApiModel), record);

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
                    .map(op -> op.getMessagesByDirection(OUTPUT))
                    .orElse(List.of());
            var responseSchema = xsdToSchema.convertMessageParts(outputMessages);

            Soap2JsonTransformer responseTransformer = new Soap2JsonTransformer();
            String jsonResponse = responseTransformer.transform(exc.getResponse().getBodyAsStringDecoded(), responseSchema);

            exc.getResponse().setBodyContent(jsonResponse.getBytes(UTF_8));
            exc.getResponse().getHeader().setContentType(APPLICATION_JSON);

            if (validateResponses) {
                var validationPlan = exc.getProperty("wsdl2openapi.validationPlan", OpenAPIValidator.ValidationPlan.class);
                if (validationPlan != null) {
                    var errors = validationPlan.validateResponse(getOpenapiValidatorResponse(exc));
                    if (errors.hasErrors()) {
                        log.info("Response validation failed for operation {}: {}", operationName, errors);
                        user(router.getConfiguration().isProduction(), getDisplayName())
                                .title("Response validation failed")
                                .addSubType("validation")
                                .status(500)
                                .flow(RESPONSE)
                                .topLevel("validation", errors.getErrorMessage(ValidationErrors.Direction.RESPONSE, maskValues))
                                .buildAndSetResponse(exc);
                        return ABORT;
                    }
                }
            }

            OperationConfig opConfig = operationsByName.get(operationName);
            if (opConfig != null && !opConfig.getFlow().isEmpty()) {
                return router.getFlowController().invokeResponseHandlers(exc, opConfig.getFlow());
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
        String withoutBase = path.replaceFirst("^" + Pattern.quote(basePath), "");
        if (withoutBase.startsWith("/")) {
            withoutBase = withoutBase.substring(1);
        }
        String segment = withoutBase.contains("?") ? withoutBase.substring(0, withoutBase.indexOf('?')) : withoutBase;
        return kebabToOperation.getOrDefault(segment, segment);
    }

    private Outcome handleOperation(Exchange exc, String operationName) {
        OperationConfig opConfig = operationsByName.get(operationName);
        String expectedMethod = opConfig != null ? opConfig.getMethod().toUpperCase() : "POST";
        if (!exc.getRequest().getMethod().equalsIgnoreCase(expectedMethod)) {
            exc.setResponse(statusCode(405)
                    .header("Allow", expectedMethod)
                    .body("Method not allowed. Use " + expectedMethod + ".")
                    .build());
            return RETURN;
        }

        try {
            if (openApiValidator != null) {
                var validatorRequest = getOpenapiValidatorRequest(exc);
                var validationPlan = openApiValidator.prepareValidation(validatorRequest);
                exc.setProperty("wsdl2openapi.validationPlan", validationPlan);
                if (validateRequests) {
                    var errors = validationPlan.validateRequest(validatorRequest);
                    if (errors.hasErrors()) {
                        user(router.getConfiguration().isProduction(), getDisplayName())
                                .title("Request validation failed")
                                .addSubType("validation")
                                .status(errors.get(0).getContext().getStatusCode())
                                .flow(Flow.REQUEST)
                                .topLevel("validation", errors.getErrorMessage(ValidationErrors.Direction.REQUEST, maskValues))
                                .buildAndSetResponse(exc);
                        return RETURN;
                    }
                }
            }

            if (opConfig != null && !opConfig.getFlow().isEmpty()) {
                Outcome outcome = router.getFlowController().invokeRequestHandlers(exc, opConfig.getFlow());
                if (outcome != CONTINUE) return outcome;
            }

            byte[] soapRequest = requestTransformers.get(operationName).transform(exc.getRequest().getBodyAsStringDecoded());

            exc.getRequest().setBodyContent(soapRequest);
            exc.getRequest().setMethod("POST");
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

    public boolean isValidateRequests() {
        return validateRequests;
    }

    /**
     * @description Validate JSON requests against the generated OpenAPI schema. Defaults to false.
     * @example true
     */
    @MCAttribute
    public void setValidateRequests(boolean validateRequests) {
        this.validateRequests = validateRequests;
    }

    public boolean isValidateResponses() {
        return validateResponses;
    }

    /**
     * @description Validate JSON responses against the generated OpenAPI schema. Defaults to false.
     * @example true
     */
    @MCAttribute
    public void setValidateResponses(boolean validateResponses) {
        this.validateResponses = validateResponses;
    }

    public boolean isValidationDetails() {
        return validationDetails;
    }

    /**
     * @description Include validation details in error responses. Defaults to true.
     * @example false
     */
    @MCAttribute
    public void setValidationDetails(boolean validationDetails) {
        this.validationDetails = validationDetails;
    }

    public boolean isMaskValues() {
        return maskValues;
    }

    /**
     * @description Mask values in validation error messages. Defaults to false.
     * @example true
     */
    @MCAttribute
    public void setMaskValues(boolean maskValues) {
        this.maskValues = maskValues;
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
