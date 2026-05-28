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
import com.predic8.membrane.core.jsonrpc.JSONRPCRequest;

import java.io.IOException;
import java.util.List;

import static com.predic8.membrane.core.jsonrpc.JSONRPCResponse.ERR_INVALID_REQUEST;
import static com.predic8.membrane.core.jsonrpc.JSONRPCResponse.ERR_METHOD_NOT_FOUND;

public class JsonRPCValidator {

    private static final ObjectMapper OM = new ObjectMapper();

    private final BatchRule batchRule;
    private final List<Rule> rules;

    public JsonRPCValidator(BatchRule batchRule, List<Rule> rules) {
        this.batchRule = batchRule == null ? new BatchRule() : batchRule;
        this.rules = rules == null ? List.of() : List.copyOf(rules);
    }

    public ValidationError validate(String body) {
        if (body == null || body.isBlank()) {
            return null;
        }

        try {
            PayloadType payloadType = getPayloadType(body);
            JsonNode root = OM.readTree(body);
            return validate(root, payloadType);
        } catch (JsonProcessingException e) {
            return new ValidationError(
                    getPayloadType(body),
                    null,
                    400,
                    ERR_INVALID_REQUEST,
                    "Invalid JSON-RPC payload: " + e.getOriginalMessage()
            );
        } catch (RuntimeException e) {
            return new ValidationError(
                    PayloadType.SINGLE,
                    null,
                    400,
                    ERR_INVALID_REQUEST,
                    "Invalid JSON-RPC payload: " + e.getMessage()
            );
        }
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

    private PayloadType getPayloadType(String body) {
        if (body == null) {
            return PayloadType.SINGLE;
        }
        return body.trim().startsWith("[") ? PayloadType.BATCH : PayloadType.SINGLE;
    }

    public enum PayloadType {
        SINGLE,
        BATCH
    }

    public record ValidationError(
            PayloadType payloadType,
            JSONRPCRequest request,
            int httpStatus,
            int code,
            String message
    ) {
    }
}
