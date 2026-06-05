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

package com.predic8.membrane.core.interceptor.json.rpc;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.predic8.membrane.annot.MCChildElement;
import com.predic8.membrane.annot.MCElement;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.Response;
import com.predic8.membrane.core.interceptor.AbstractInterceptor;
import com.predic8.membrane.core.interceptor.Outcome;
import com.predic8.membrane.core.interceptor.json.rpc.JsonRPCValidator.RequestValidationResult;
import com.predic8.membrane.core.interceptor.json.rpc.JsonRPCValidator.ResponseValidationContext;
import com.predic8.membrane.core.interceptor.json.rpc.JsonRPCValidator.ValidationError;
import com.predic8.membrane.core.jsonrpc.JSONRPCResponse;
import com.predic8.membrane.core.util.config.allowdeny.Rule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.List;

import static com.predic8.membrane.core.http.MimeType.APPLICATION_JSON;
import static com.predic8.membrane.core.http.Response.statusCode;
import static com.predic8.membrane.core.interceptor.Interceptor.Flow.REQUEST;
import static com.predic8.membrane.core.interceptor.Interceptor.Flow.RESPONSE;
import static com.predic8.membrane.core.interceptor.Outcome.CONTINUE;
import static com.predic8.membrane.core.interceptor.Outcome.RETURN;
import static com.predic8.membrane.core.interceptor.json.rpc.JsonRPCValidator.PayloadType.BATCH;
import static com.predic8.membrane.core.interceptor.json.rpc.JsonRPCValidator.PayloadType.SINGLE;
import static java.util.EnumSet.of;
import static com.predic8.membrane.core.jsonrpc.JSONRPCResponse.ERR_INVALID_REQUEST;

/**
 * @topic 3. Security and Validation
 * @description
 * <p>Protects JSON-RPC endpoints by validating request structure, controlling batch usage,
 * applying ordered allow/deny rules to method names, and optionally validating
 * method parameters against JSON Schema documents.</p>
 *
 * <p>Method rules are evaluated in the configured order. The first matching rule decides
 * whether a method is allowed or denied.</p>
 *
 * <p>Parameter schemas are configured separately in the <code>params</code> child element. The
 * keys are exact JSON-RPC method names. The matching schema entry is used to validate the
 * <code>params</code> object or array. Schemas must be referenced by path or URL and cannot
 * be configured inline.</p>
 *
 * <p>Result schemas can be configured in the <code>result</code> child element using the same
 * method-to-schema mapping format. Successful JSON-RPC responses are then validated against the
 * configured schema for the originating request method.</p>
 *
 * @yaml
 * <pre><code>
 * - jsonRPCProtection:
 *     batch:
 *       enabled: true
 *       maxSize: 50
 *     methods:
 *       - allow: "^rpc\\.(health|echo)$"
 *       - deny: "^rpc\\..*$"
 *       - deny: * # Switch to default-deny behavior
 *     params:
 *       "rpc.echo": "classpath:/json/rpc/echo-params.schema.json"
 *     result:
 *       "rpc.echo": "classpath:/json/rpc/echo-result.schema.json"
 * </code></pre>
 */
@MCElement(name = "jsonRPCProtection")
public class JsonRPCProtectionInterceptor extends AbstractInterceptor {

    private static final Logger log = LoggerFactory.getLogger(JsonRPCProtectionInterceptor.class);
    private static final ObjectMapper OM = new ObjectMapper();
    private static final String RESPONSE_VALIDATION_CONTEXT = JsonRPCProtectionInterceptor.class.getName() + ".responseValidationContext";

    private BatchRule batchRule = new BatchRule();
    private List<Rule> methods = List.of();
    private JsonRPCParams params = new JsonRPCParams();
    private JsonRPCResult result = new JsonRPCResult();
    private JsonRPCValidator validator;

    private JsonRPCSchemaValidation schemaValidation;

    public JsonRPCProtectionInterceptor() {
        name = "JSON-RPC protection";
        setAppliedFlow(of(REQUEST, RESPONSE));
    }

    @Override
    public void init() {
        super.init();
        params.init(router.getResolverMap(), router.getConfiguration().getUriFactory(), getBeanBaseLocation());
        result.init(router.getResolverMap(), router.getConfiguration().getUriFactory(), getBeanBaseLocation());
        validator = createValidator();
    }

    @Override
    public Outcome handleRequest(Exchange exc) {
        if (!exc.getRequest().isPOSTRequest()) {
            return CONTINUE;
        }

        if (exc.getRequest().isBodyEmpty()) {
            return CONTINUE;
        }

        if (!exc.getRequest().isJSON()) {
            return rejectRequest(exc, new ValidationError(
                    payloadType(exc.getRequest().getBodyAsStringDecoded()),
                    null,
                    415,
                    ERR_INVALID_REQUEST,
                    "Content-Type %s is not supported. Expected application/json.".formatted(exc.getRequest().getHeader().getContentType())
            ));
        }

        RequestValidationResult validation = getValidator().validateRequest(exc.getRequest().getBodyAsStringDecoded());
        if (validation.responseValidationContext() != null) {
            exc.setProperty(RESPONSE_VALIDATION_CONTEXT, validation.responseValidationContext());
        }
        return rejectRequest(exc, validation.error());
    }

    @Override
    public Outcome handleResponse(Exchange exc) {
        if (exc.getResponse() == null || result.getMappings().isEmpty()) {
            return CONTINUE;
        }

        ResponseValidationContext context = exc.getProperty(RESPONSE_VALIDATION_CONTEXT, ResponseValidationContext.class);
        if (context == null) {
            return CONTINUE;
        }

        return rejectResponse(exc, getValidator().validateResponse(exc.getResponse().getBodyAsStringDecoded(), context));
    }

    private Outcome rejectRequest(Exchange exc, ValidationError error) {
        if (error == null) {
            return CONTINUE;
        }
        log.info("Rejected JSON-RPC request: {}", error.message());
        exc.setResponse(createErrorResponse(error));
        return RETURN;
    }

    private Outcome rejectResponse(Exchange exc, ValidationError error) {
        if (error == null) {
            return CONTINUE;
        }
        log.info("Rejected JSON-RPC response: {}", error.message());
        exc.setResponse(createErrorResponse(error));
        return RETURN;
    }

    /**
     * @description Configures whether JSON-RPC batch requests are allowed and how many request objects one batch may contain.
     */
    @MCChildElement(order = 0)
    public void setBatch(BatchRule batchRule) {
        this.batchRule = batchRule;
    }

    /**
     * @description
     * <p>Configures ordered allow/deny rules for JSON-RPC method names.</p>
     *
     * <p>The first matching rule decides whether the method is allowed or denied. Methods that do not match any configured rule are allowed. To switch to default-deny behavior, add a final deny rule such as <code>deny: .*</code>.</p>
     */
    @MCChildElement(order = 1)
    public void setMethods(List<Rule> methods) {
        this.methods = methods;
    }



    @MCChildElement(order = 4)
    public void setSchemaValidation(JsonRPCSchemaValidation schemaValidation) {
        this.schemaValidation = schemaValidation;
    }

    public JsonRPCSchemaValidation getSchemaValidation() {
        return schemaValidation;
    }


    /**
     * @description
     * <p>Configures JSON Schema files for validating <code>params</code> per method name.</p>
     *
     * <p>The keys are exact JSON-RPC method names. Values must be schema paths or URLs; inline
     * schemas are not supported.</p>
     */
    @MCChildElement(order = 2)
    public void setParams(JsonRPCParams params) {
        this.params = params;
    }

    /**
     * @description
     * <p>Configures JSON Schema files for validating successful JSON-RPC <code>result</code>
     * payloads per method name.</p>
     *
     * <p>The keys are exact JSON-RPC method names. Values must be schema paths or URLs; inline
     * schemas are not supported.</p>
     */
    @MCChildElement(order = 3)
    public void setResult(JsonRPCResult result) {
        this.result = result;
    }

    public BatchRule getBatch() {
        return batchRule;
    }

    public List<Rule> getMethods() {
        return methods;
    }

    public JsonRPCParams getParams() {
        return params;
    }

    public JsonRPCResult getResult() {
        return result;
    }

    private JsonRPCValidator getValidator() {
        if (validator == null) {
            validator = createValidator();
        }
        return validator;
    }

    private JsonRPCValidator createValidator() {
        params.init(router.getResolverMap(), router.getConfiguration().getUriFactory(), getBeanBaseLocation());
        result.init(router.getResolverMap(), router.getConfiguration().getUriFactory(), getBeanBaseLocation());
        return new JsonRPCValidator(batchRule, methods, params, result);
    }

    private Response createErrorResponse(ValidationError error) {
        try {
            if (error.payloadType() == BATCH) {
                return statusCode(error.httpStatus())
                        .contentType(APPLICATION_JSON)
                        .body(OM.writeValueAsString(List.of(JSONRPCResponse.error(error.responseId(), error.code(), error.message()))))
                        .build();
            }

            return statusCode(error.httpStatus())
                    .contentType(APPLICATION_JSON)
                    .body(JSONRPCResponse.error(error.responseId(), error.code(), error.message()).toJson())
                    .build();
        } catch (IOException e) {
            throw new RuntimeException("Could not create JSON-RPC error response", e);
        }
    }

    private JsonRPCValidator.PayloadType payloadType(String body) {
        if (body == null) {
            return SINGLE;
        }
        return body.trim().startsWith("[") ? BATCH : SINGLE;
    }
}
