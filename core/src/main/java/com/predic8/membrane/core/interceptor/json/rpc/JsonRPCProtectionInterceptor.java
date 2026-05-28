package com.predic8.membrane.core.interceptor.json.rpc;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
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
import static com.predic8.membrane.core.jsonrpc.JSONRPCResponse.ERR_INVALID_REQUEST;
import static com.predic8.membrane.core.jsonrpc.JSONRPCResponse.ERR_METHOD_NOT_FOUND;
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

        String body = null;
        try {
            body = exc.getRequest().getBodyAsStringDecoded();
            if (body == null || body.isBlank()) {
                return CONTINUE;
            }

            PayloadType payloadType = getPayloadType(body);
            JsonNode root = OM.readTree(body);
            ValidationError error = validate(root, payloadType);
            if (error == null) {
                return CONTINUE;
            }

            log.info("Rejected JSON-RPC request: {}", error.message());
            exc.setResponse(createErrorResponse(error));
            return RETURN;
        } catch (JsonProcessingException e) {
            exc.setResponse(createErrorResponse(new ValidationError(
                    getPayloadType(body),
                    null,
                    400,
                    ERR_INVALID_REQUEST,
                    "Invalid JSON-RPC payload: " + e.getOriginalMessage()
            )));
            return RETURN;
        } catch (RuntimeException e) {
            log.debug("Rejected JSON-RPC request", e);
            exc.setResponse(createErrorResponse(new ValidationError(
                    PayloadType.SINGLE,
                    null,
                    400,
                    ERR_INVALID_REQUEST,
                    "Invalid JSON-RPC payload: " + e.getMessage()
            )));
            return RETURN;
        }
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

    private ValidationError validate(JsonNode root, PayloadType payloadType) {
        if (root == null) {
            return null;
        }
        if (root.isObject()) {
            return validateSingle(root);
        }
        if (root.isArray()) {
            return validateBatch(root);
        }
        return new ValidationError(payloadType, null, 400, ERR_INVALID_REQUEST, "JSON-RPC payload must be an object or batch array.");
    }

    private ValidationError validateSingle(JsonNode node) {
        try {
            return validateMethod(parseRequest(node), PayloadType.SINGLE);
        } catch (IOException e) {
            return new ValidationError(PayloadType.SINGLE, null, 400, ERR_INVALID_REQUEST, "Invalid JSON-RPC request: " + e.getMessage());
        }
    }

    private ValidationError validateBatch(JsonNode batch) {
        if (!batchRule.isEnabled()) {
            return new ValidationError(PayloadType.BATCH, null, 400, ERR_INVALID_REQUEST, "Batch requests are disabled.");
        }
        if (batch.isEmpty()) {
            return new ValidationError(PayloadType.BATCH, null, 400, ERR_INVALID_REQUEST, "Batch requests must not be empty.");
        }
        if (batch.size() > batchRule.getMaxSize()) {
            return new ValidationError(
                    PayloadType.BATCH,
                    null,
                    400,
                    ERR_INVALID_REQUEST,
                    "Batch request exceeds maxSize of " + batchRule.getMaxSize() + "."
            );
        }

        for (JsonNode requestNode : batch) {
            if (!requestNode.isObject()) {
                return new ValidationError(PayloadType.BATCH, null, 400, ERR_INVALID_REQUEST, "Each batch entry must be a JSON-RPC request object.");
            }

            try {
                ValidationError error = validateMethod(parseRequest(requestNode), PayloadType.BATCH);
                if (error != null) {
                    return error;
                }
            } catch (IOException e) {
                return new ValidationError(PayloadType.BATCH, null, 400, ERR_INVALID_REQUEST, "Invalid JSON-RPC request in batch: " + e.getMessage());
            }
        }
        return null;
    }

    private ValidationError validateMethod(JSONRPCRequest request, PayloadType payloadType) {
        for (Rule rule : rules) {
            if (!rule.matches(request.getMethod())) {
                continue;
            }
            if (rule.permits()) {
                return null;
            }
            return new ValidationError(
                    payloadType,
                    request,
                    403,
                    ERR_METHOD_NOT_FOUND,
                    "JSON-RPC method '%s' is not allowed.".formatted(request.getMethod())
            );
        }
        return null;
    }

    private JSONRPCRequest parseRequest(JsonNode node) throws IOException {
        return JSONRPCRequest.parse(OM.writeValueAsString(node));
    }

    private Response createErrorResponse(ValidationError error) {
        try {
            if (error.payloadType() == PayloadType.BATCH) {
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

    private PayloadType getPayloadType(String body) {
        if (body == null) {
            return PayloadType.SINGLE;
        }
        return body.trim().startsWith("[") ? PayloadType.BATCH : PayloadType.SINGLE;
    }

    private enum PayloadType {
        SINGLE,
        BATCH
    }

    private record ValidationError(
            PayloadType payloadType,
            JSONRPCRequest request,
            int httpStatus,
            int code,
            String message
    ) {
    }

}
