package com.predic8.membrane.core.interceptor.json.rpc;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.predic8.membrane.annot.MCChildElement;
import com.predic8.membrane.annot.MCElement;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.Response;
import com.predic8.membrane.core.interceptor.AbstractInterceptor;
import com.predic8.membrane.core.interceptor.Outcome;
import com.predic8.membrane.core.interceptor.json.rpc.JsonRPCValidator.ValidationError;
import com.predic8.membrane.core.jsonrpc.JSONRPCRequest;
import com.predic8.membrane.core.jsonrpc.JSONRPCResponse;
import com.predic8.membrane.core.util.allowdeny.Rule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.List;

import static com.predic8.membrane.core.http.MimeType.APPLICATION_JSON;
import static com.predic8.membrane.core.http.Response.statusCode;
import static com.predic8.membrane.core.interceptor.Interceptor.Flow.REQUEST;
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
 * keys are regular expressions matched against the JSON-RPC method name. The first matching
 * schema entry is used to validate the <code>params</code> object or array. Schemas must be
 * referenced by path or URL and cannot be configured inline.</p>
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
 *     params:
 *       "^rpc\\.echo$": "classpath:/json/rpc/echo-params.schema.json"
 * </code></pre>
 */
@MCElement(name = "jsonRPCProtection")
public class JsonRPCProtectionInterceptor extends AbstractInterceptor {

    private static final Logger log = LoggerFactory.getLogger(JsonRPCProtectionInterceptor.class);
    private static final ObjectMapper OM = new ObjectMapper();

    private BatchRule batchRule = new BatchRule();
    private List<Rule> methods = List.of();
    private JsonRPCParams params = new JsonRPCParams();
    private JsonRPCValidator validator;

    public JsonRPCProtectionInterceptor() {
        name = "JSON-RPC protection";
        setAppliedFlow(of(REQUEST));
    }

    @Override
    public void init() {
        super.init();
        params.init(router.getResolverMap(), router.getConfiguration().getUriFactory(), getBeanBaseLocation());
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
            return reject(exc, new ValidationError(
                    payloadType(exc.getRequest().getBodyAsStringDecoded()),
                    null,
                    415,
                    ERR_INVALID_REQUEST,
                    "Content-Type %s is not supported. Expected application/json.".formatted(exc.getRequest().getHeader().getContentType())
            ));
        }

        return reject(exc, getValidator().validate(exc.getRequest().getBodyAsStringDecoded()));
    }

    private Outcome reject(Exchange exc, ValidationError error) {
        if (error == null) {
            return CONTINUE;
        }
        log.info("Rejected JSON-RPC request: {}", error.message());
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

    /**
     * @description
     * <p>Configures JSON Schema files for validating <code>params</code> per method name.</p>
     *
     * <p>The keys are regular expressions matched against the JSON-RPC method name in order.
     * The first matching entry is used. Values must be schema paths or URLs; inline schemas are not supported.</p>
     */
    @MCChildElement(order = 2)
    public void setParams(JsonRPCParams params) {
        this.params = params;
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

    private JsonRPCValidator getValidator() {
        if (validator == null) {
            validator = createValidator();
        }
        return validator;
    }

    private JsonRPCValidator createValidator() {
        params.init(router.getResolverMap(), router.getConfiguration().getUriFactory(), getBeanBaseLocation());
        return new JsonRPCValidator(batchRule, methods, params);
    }

    private Response createErrorResponse(ValidationError error) {
        try {
            if (error.payloadType() == BATCH) {
                return statusCode(error.httpStatus())
                        .contentType(APPLICATION_JSON)
                        .body(OM.writeValueAsString(List.of(JSONRPCResponse.error(responseId(error.request()), error.code(), error.message()))))
                        .build();
            }

            return statusCode(error.httpStatus())
                    .contentType(APPLICATION_JSON)
                    .body(JSONRPCResponse.error(responseId(error.request()), error.code(), error.message()).toJson())
                    .build();
        } catch (IOException e) {
            throw new RuntimeException("Could not create JSON-RPC error response", e);
        }
    }

    private Object responseId(JSONRPCRequest request) {
        if (request == null || request.isNotification()) {
            return null;
        }
        return request.getId();
    }

    private JsonRPCValidator.PayloadType payloadType(String body) {
        if (body == null) {
            return SINGLE;
        }
        return body.trim().startsWith("[") ? BATCH : SINGLE;
    }
}
