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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static com.predic8.membrane.core.http.MimeType.APPLICATION_JSON;
import static com.predic8.membrane.core.http.Response.statusCode;
import static com.predic8.membrane.core.interceptor.Interceptor.Flow.REQUEST;
import static com.predic8.membrane.core.interceptor.Outcome.CONTINUE;
import static com.predic8.membrane.core.interceptor.Outcome.RETURN;
import static com.predic8.membrane.core.interceptor.json.rpc.JsonRPCValidator.PayloadType.BATCH;
import static java.util.EnumSet.of;

@MCElement(name = "jsonRPCProtection")
public class JsonRPCProtectionInterceptor extends AbstractInterceptor {

    private static final Logger log = LoggerFactory.getLogger(JsonRPCProtectionInterceptor.class);
    private static final ObjectMapper OM = new ObjectMapper();

    private BatchRule batchRule = new BatchRule();
    private List<Rule> rules = List.of();
    private JsonRPCParams params = new JsonRPCParams();
    private JsonRPCValidator validator;

    public JsonRPCProtectionInterceptor() {
        name = "json rpc protection";
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
        if (!"POST".equals(exc.getRequest().getMethod())) {
            return CONTINUE;
        }

        String body = exc.getRequest().getBodyAsStringDecoded();
        if (body == null || body.isBlank()) {
            return CONTINUE;
        }

        return reject(exc, getValidator().validate(body));
    }

    private Outcome reject(Exchange exc, ValidationError error) {
        if (error == null) {
            return CONTINUE;
        }
        log.info("Rejected JSON-RPC request: {}", error.message());
        exc.setResponse(createErrorResponse(error));
        return RETURN;
    }

    @MCChildElement(order = 0)
    public void setBatch(BatchRule batchRule) {
        this.batchRule = batchRule;
        validator = null;
    }

    @MCChildElement(order = 1)
    public void setRules(List<Rule> rules) {
        this.rules = rules == null ? List.of() : new ArrayList<>(rules);
        validator = null;
    }

    @MCChildElement(order = 2)
    public void setParams(JsonRPCParams params) {
        this.params = params == null ? new JsonRPCParams() : params;
        validator = null;
    }

    public BatchRule getBatch() {
        return batchRule;
    }

    public List<Rule> getRules() {
        return rules;
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
        return new JsonRPCValidator(batchRule, rules, params);
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
}
