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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.NullNode;
import com.networknt.schema.Error;
import com.predic8.membrane.core.jsonrpc.JSONRPCRequest;
import com.predic8.membrane.core.jsonrpc.JSONRPCResponse;
import com.predic8.membrane.core.util.config.allowdeny.Rule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.predic8.membrane.core.interceptor.json.rpc.JsonRPCValidator.PayloadType.BATCH;
import static com.predic8.membrane.core.interceptor.json.rpc.JsonRPCValidator.PayloadType.SINGLE;
import static com.predic8.membrane.core.jsonrpc.JSONRPCResponse.*;

public class JsonRPCValidator {

    private static final Logger log = LoggerFactory.getLogger(JsonRPCValidator.class);
    private static final ObjectMapper om = new ObjectMapper();

    private final BatchRule batchRule;
    private final List<Rule> rules;
    private final JsonRPCParams params;
    private final JsonRPCResult result;

    public JsonRPCValidator(BatchRule batchRule, List<Rule> rules, JsonRPCParams params, JsonRPCResult result) {
        this.batchRule = batchRule;
        this.rules = rules;
        this.params = params;
        this.result = result;
    }

    public ValidationError validate(String body) {
        return validateRequest(body).error();
    }

    public RequestValidationResult validateRequest(String body) {
        if (body == null || body.isBlank()) {
            return new RequestValidationResult(null, null);
        }

        try {
            var payloadType = getPayloadType(body);
            var root = om.readTree(body);
            return validateRequest(root, payloadType);
        } catch (JsonProcessingException e) {
            log.debug("Invalid JSON-RPC request payload.", e);
            return invalidRequestResult(getPayloadType(body), "Invalid JSON-RPC payload");
        } catch (RuntimeException e) {
            log.debug("Invalid JSON-RPC request payload.", e);
            return invalidRequestResult(SINGLE, "Invalid JSON-RPC payload");
        }
    }

    public ValidationError validateResponse(String body, ResponseValidationContext context) {
        if (context == null || !context.expectsResponses() || result.isEmpty()) {
            return null;
        }
        if (body == null || body.isBlank()) {
            return invalidResponse(context.payloadType(), null, "JSON-RPC response must not be empty.");
        }

        try {
            var root = om.readTree(body);
            return validateResponse(root, context);
        } catch (Exception e) {
            log.debug("Invalid JSON-RPC response payload.", e);
            return invalidResponse(context.payloadType(), null, "Invalid JSON-RPC response payload");
        }
    }

    private RequestValidationResult validateRequest(JsonNode root, PayloadType payloadType) {
        if (root.isObject()) {
            return validateSingleRequest(root);
        }
        if (root.isArray()) {
            return validateBatchRequest(root);
        }
        return invalidRequestResult(payloadType, "JSON-RPC payload must be an object or batch array.");
    }

    private RequestValidationResult validateSingleRequest(JsonNode node) {
        try {
            JSONRPCRequest request = JSONRPCRequest.fromNode(node);
            ValidationError error = validateMethod(request, SINGLE);
            return new RequestValidationResult(error, createResponseValidationContext(SINGLE, request));
        } catch (IOException e) {
            return invalidRequestResult(SINGLE, "Invalid JSON-RPC request: " + e.getMessage());
        }
    }

    private RequestValidationResult validateBatchRequest(JsonNode batch) {
        if (!batchRule.isEnabled()) {
            return invalidRequestResult(BATCH, "Batch requests are disabled.");
        }
        if (batch.isEmpty()) {
            return invalidRequestResult(BATCH, "Batch requests must not be empty.");
        }
        if (batch.size() > batchRule.getMaxSize()) {
            return invalidRequestResult(BATCH, "Batch request exceeds maxSize of " + batchRule.getMaxSize() + ".");
        }

        Map<Object, String> methodsById = new LinkedHashMap<>();
        for (JsonNode requestNode : batch) {
            if (!requestNode.isObject()) {
                return invalidRequestResult(BATCH, "Each batch entry must be a JSON-RPC request object.");
            }

            try {
                JSONRPCRequest request = JSONRPCRequest.fromNode(requestNode);
                ValidationError error = validateMethod(request, BATCH);
                if (error != null) {
                    return new RequestValidationResult(error, null);
                }
                rememberResponseMethod(methodsById, request);
            } catch (IOException e) {
                return invalidRequestResult(BATCH, "Invalid JSON-RPC request in batch: " + e.getMessage());
            }
        }
        return new RequestValidationResult(null, createResponseValidationContext(BATCH, methodsById));
    }

    private ValidationError validateResponse(JsonNode root, ResponseValidationContext context) {
        if (context.payloadType() == SINGLE) {
            if (!root.isObject()) {
                return invalidResponse(SINGLE, null, "JSON-RPC response must be an object.");
            }
            return validateSingleResponse(root, context, SINGLE);
        }

        if (!root.isArray()) {
            return invalidResponse(BATCH, null, "JSON-RPC batch response must be an array.");
        }
        return validateBatchResponse(root, context);
    }

    private ValidationError validateSingleResponse(JsonNode node, ResponseValidationContext context, PayloadType payloadType) {
        JSONRPCResponse response;
        try {
            response = parse(node.toString());
        } catch (IOException e) {
            return invalidResponse(payloadType, null, "Invalid JSON-RPC response: " + e.getMessage());
        }

        if (response.isError()) {
            return null;
        }

        String methodName = context.methodFor(response.getId());
        if (methodName == null) {
            return invalidResponse(payloadType, response.getId(), "JSON-RPC response id '%s' does not match any request.".formatted(response.getId()));
        }

        var schema = result.getSchema(methodName);
        if (schema == null) {
            return null;
        }

        var errors = schema.validate(getResultNode(response));
        if (errors.isEmpty()) {
            return null;
        }

        return invalidResponse(
                payloadType,
                response.getId(),
                "Invalid result for method '%s': %s".formatted(
                        methodName,
                        errors.stream().map(Error::getMessage).collect(Collectors.joining("; "))
                )
        );
    }

    private ValidationError validateBatchResponse(JsonNode batch, ResponseValidationContext context) {
        if (batch.isEmpty()) {
            return invalidResponse(BATCH, null, "Batch responses must not be empty.");
        }

        for (JsonNode responseNode : batch) {
            if (!responseNode.isObject()) {
                return invalidResponse(BATCH, null, "Each batch entry must be a JSON-RPC response object.");
            }

            ValidationError error = validateSingleResponse(responseNode, context, BATCH);
            if (error != null) {
                return error;
            }
        }
        return null;
    }

    private ValidationError validateMethod(JSONRPCRequest request, PayloadType payloadType) {
        for (var rule : rules) {
            if (!rule.matches(request.getMethod())) {
                continue;
            }
            if (rule.permits()) {
                break;
            }
            return new ValidationError(
                    payloadType,
                    responseId(request),
                    403,
                    ERR_METHOD_NOT_FOUND,
                    "JSON-RPC method '%s' is not allowed.".formatted(request.getMethod())
            );
        }
        return validateParams(request, payloadType);
    }

    private ValidationError validateParams(JSONRPCRequest request, PayloadType payloadType) {
        var schema = params.getSchema(request.getMethod());
        if (schema == null) {
            return null;
        }

        var errors = schema.validate(getParamsNode(request));
        if (errors.isEmpty()) {
            return null;
        }

        return new ValidationError(
                payloadType,
                responseId(request),
                400,
                ERR_INVALID_PARAMS,
                "Invalid params for method '%s': %s".formatted(
                        request.getMethod(),
                        errors.stream().map(Error::getMessage).collect(Collectors.joining("; "))
                )
        );
    }

    private RequestValidationResult invalidRequestResult(PayloadType payloadType, String message) {
        return new RequestValidationResult(new ValidationError(payloadType, null, 400, ERR_INVALID_REQUEST, message), null);
    }

    private ValidationError invalidResponse(PayloadType payloadType, Object responseId, String message) {
        return new ValidationError(payloadType, responseId, 500, ERR_INTERNAL_ERROR, message);
    }

    private ResponseValidationContext createResponseValidationContext(PayloadType payloadType, JSONRPCRequest request) {
        Map<Object, String> methodsById = new LinkedHashMap<>();
        rememberResponseMethod(methodsById, request);
        return createResponseValidationContext(payloadType, methodsById);
    }

    private ResponseValidationContext createResponseValidationContext(PayloadType payloadType, Map<Object, String> methodsById) {
        if (result.isEmpty() || methodsById.isEmpty()) {
            return null;
        }
        return new ResponseValidationContext(payloadType, Collections.unmodifiableMap(new LinkedHashMap<>(methodsById)));
    }

    private void rememberResponseMethod(Map<Object, String> methodsById, JSONRPCRequest request) {
        if (request == null || request.isNotification()) {
            return;
        }
        methodsById.putIfAbsent(request.getId(), request.getMethod());
    }

    private Object responseId(JSONRPCRequest request) {
        if (request == null || request.isNotification()) {
            return null;
        }
        return request.getId();
    }

    private JsonNode getParamsNode(JSONRPCRequest request) {
        Object params = request.getParams();
        if (params == null) {
            return NullNode.instance;
        }
        return om.valueToTree(params);
    }

    private JsonNode getResultNode(JSONRPCResponse response) {
        Object result = response.getResult();
        if (result == null) {
            return NullNode.instance;
        }
        return om.valueToTree(result);
    }

    private PayloadType getPayloadType(String body) {
        if (body == null) {
            return SINGLE;
        }
        return body.trim().startsWith("[") ? BATCH : SINGLE;
    }

    public enum PayloadType {
        SINGLE,
        BATCH
    }

    public record RequestValidationResult(
            ValidationError error,
            ResponseValidationContext responseValidationContext
    ) {
    }

    public record ResponseValidationContext(
            PayloadType payloadType,
            Map<Object, String> methodsById
    ) {
        public boolean expectsResponses() {
            return !methodsById.isEmpty();
        }

        public String methodFor(Object responseId) {
            return methodsById.get(responseId);
        }
    }

    public record ValidationError(
            PayloadType payloadType,
            Object responseId,
            int httpStatus,
            int code,
            String message
    ) {
    }
}
