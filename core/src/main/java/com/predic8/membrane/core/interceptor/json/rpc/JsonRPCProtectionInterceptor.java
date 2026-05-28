package com.predic8.membrane.core.interceptor.json.rpc;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.predic8.membrane.annot.MCChildElement;
import com.predic8.membrane.annot.MCElement;
import com.predic8.membrane.annot.MCOtherAttributes;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.Response;
import com.predic8.membrane.core.interceptor.AbstractInterceptor;
import com.predic8.membrane.core.interceptor.Outcome;
import com.predic8.membrane.core.jsonrpc.JSONRPCRequest;
import com.predic8.membrane.core.jsonrpc.JSONRPCResponse;
import com.predic8.membrane.core.interceptor.json.rpc.JsonRPCValidator.ValidationError;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.predic8.membrane.core.http.MimeType.APPLICATION_JSON;
import static com.predic8.membrane.core.http.Response.statusCode;
import static com.predic8.membrane.core.interceptor.Interceptor.Flow.REQUEST;
import static com.predic8.membrane.core.interceptor.Outcome.CONTINUE;
import static com.predic8.membrane.core.interceptor.Outcome.RETURN;
import static com.predic8.membrane.core.interceptor.json.rpc.JsonRPCValidator.PayloadType.*;
import static java.util.EnumSet.of;

@MCElement(name = "jsonRPCProtection")
public class JsonRPCProtectionInterceptor extends AbstractInterceptor {

    private static final Logger log = LoggerFactory.getLogger(JsonRPCProtectionInterceptor.class);
    private static final ObjectMapper OM = new ObjectMapper();

    private BatchRule batchRule = new BatchRule();
    private List<Rule> rules = List.of();
    private final Map<String, String> params = new LinkedHashMap<>();

    public JsonRPCProtectionInterceptor() {
        name = "json rpc protection";
        setAppliedFlow(of(REQUEST));
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

        return reject(exc, new JsonRPCValidator(batchRule, rules).validate(body));
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
    }

    @MCChildElement(order = 1)
    public void setRules(List<Rule> rules) {
        this.rules = rules == null ? List.of() : new ArrayList<>(rules);
    }

    @MCOtherAttributes
    public void setParams(Map<String, String> params) {
        this.params.putAll(params);
    }

    public BatchRule getBatch() {
        return batchRule;
    }

    public List<Rule> getRules() {
        return rules;
    }

    public Map<String, String> getParams() {
        return params;
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
