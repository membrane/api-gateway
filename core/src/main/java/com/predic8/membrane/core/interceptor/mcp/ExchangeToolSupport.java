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

package com.predic8.membrane.core.interceptor.mcp;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.predic8.membrane.core.exchange.AbstractExchange;
import com.predic8.membrane.core.mcp.MCPToolsCall;
import com.predic8.membrane.core.mcp.MCPToolsCallResponse;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;

import static com.predic8.membrane.core.interceptor.mcp.ExchangeUtils.matchesExchangeFilter;
import static com.predic8.membrane.core.interceptor.mcp.MCPUtil.*;
import static com.predic8.membrane.core.interceptor.mcp.McpSchemaBuilder.integer;
import static com.predic8.membrane.core.interceptor.mcp.McpSchemaBuilder.string;
import static java.lang.Integer.MAX_VALUE;

final class ExchangeToolSupport {

    static final String ARG_ID = "id";
    static final String ARG_LIMIT = "limit";
    static final String ARG_OFFSET = "offset";
    static final String ARG_INCLUDE_BODIES = "includeBodies";
    static final String ARG_HOST = "host";
    static final String ARG_PORT = "port";
    static final String ARG_PATH_PATTERN = "pathPattern";
    static final String ARG_MAX_RESPONSE_SIZE = "maxResponseSize";

    private static final ObjectMapper OM = new ObjectMapper();

    private final McpPayloadSanitizer payloadSanitizer;

    ExchangeToolSupport(McpPayloadSanitizer payloadSanitizer) {
        this.payloadSanitizer = payloadSanitizer;
    }

    ExchangeQuery parseQuery(MCPToolsCall call, int maxExchanges) {
        rejectUnexpectedArguments(call, Set.of(
                ARG_LIMIT,
                ARG_OFFSET,
                ARG_INCLUDE_BODIES,
                ARG_HOST,
                ARG_PORT,
                ARG_PATH_PATTERN,
                ARG_MAX_RESPONSE_SIZE
        ));

        return new ExchangeQuery(
                getOptionalStringArgument(call, ARG_HOST),
                getOptionalPort(call),
                getOptionalStringArgument(call, ARG_PATH_PATTERN),
                getOptionalIntArgument(call, ARG_OFFSET, 0, 0, MAX_VALUE),
                getOptionalIntArgument(call, ARG_LIMIT, maxExchanges, 1, maxExchanges),
                getOptionalBooleanArgument(call, ARG_INCLUDE_BODIES, false),
                getOptionalMaxResponseSize(call)
        );
    }

    ExchangeLookupQuery parseLookupQuery(MCPToolsCall call) {
        rejectUnexpectedArguments(call, Set.of(
                ARG_ID,
                ARG_INCLUDE_BODIES
        ));

        return new ExchangeLookupQuery(
                getRequiredLongArgument(call, ARG_ID),
                getOptionalBooleanArgument(call, ARG_INCLUDE_BODIES, false)
        );
    }

    Map<String, Object> getExchangesSchema(int maxExchanges) {
        return McpSchemaBuilder.object()
                .property(ARG_LIMIT, integer().minimum(1).maximum(maxExchanges))
                .property(ARG_OFFSET, integer().minimum(0).description("Number of newest matching exchanges to skip before collecting the page"))
                .property(ARG_INCLUDE_BODIES, McpSchemaBuilder.bool())
                .property(ARG_HOST, string())
                .property(ARG_PORT, integer().minimum(1).maximum(65535))
                .property(ARG_PATH_PATTERN, string().description("Matches by prefix or regex"))
                .property(ARG_MAX_RESPONSE_SIZE, integer().minimum(1).description("Maximum size in bytes of the final JSON-RPC response body returned by this tool"))
                .additionalProperties(false)
                .build();
    }

    Map<String, Object> getExchangeSchema() {
        return McpSchemaBuilder.object()
                .property(ARG_ID, integer().description("Exchange id"))
                .property(ARG_INCLUDE_BODIES, McpSchemaBuilder.bool())
                .required(ARG_ID)
                .additionalProperties(false)
                .build();
    }

    ExchangePage findPage(@Nullable List<AbstractExchange> allExchanges, ExchangeQuery query) {
        List<AbstractExchange> exchanges = allExchanges == null ? List.of() : allExchanges;
        List<AbstractExchange> page = new ArrayList<>(query.limit());
        int skipped = 0;
        boolean hasMore = false;

        for (int i = exchanges.size() - 1; i >= 0; i--) {
            AbstractExchange exchange = exchanges.get(i);
            if (exchange.getResponse() == null) {
                continue;
            }
            if (!matchesExchangeFilter(exchange, query.host(), query.port(), query.pathPattern())) {
                continue;
            }
            if (skipped < query.offset()) {
                skipped++;
                continue;
            }
            if (page.size() < query.limit()) {
                page.addFirst(exchange);
                continue;
            }

            hasMore = true;
            break;
        }

        return new ExchangePage(page, hasMore, query.offset());
    }

    MCPToolsCallResponse buildFullPageResponse(MCPToolsCall call, ExchangePage page, boolean includeBodies) {
        List<Map<String, Object>> describedExchanges = describeExchanges(page.exchanges(), includeBodies);
        return createExchangePageResponse(
                call,
                describedExchanges,
                page.hasMore(),
                nextOffset(page.offset(), describedExchanges.size(), page.hasMore())
        );
    }

    MCPToolsCallResponse buildSizedPageResponse(MCPToolsCall call, ExchangePage page, boolean includeBodies, int maxResponseSize) {
        TextResponseEnvelope responseEnvelope = measureTextResponseEnvelope(call);
        long maxResponseSizeLimit = maxResponseSize;
        long prefixBytes = measureEscapedJsonStringContentSize("{\"exchanges\":[");
        long separatorBytes = measureEscapedJsonStringContentSize(",");
        long minimumResponseSize = responseEnvelope.fixedBytes() + prefixBytes + measureExchangePageSuffixBytes(false, null);

        if (minimumResponseSize > maxResponseSizeLimit) throw new InvalidToolArgumentsException("Tool argument '" + ARG_MAX_RESPONSE_SIZE + "' must be at least " + minimumResponseSize + " bytes");

        List<Map<String, Object>> describedExchanges = new ArrayList<>();
        long exchangesBytes = 0;
        for (int i = page.exchanges().size() - 1; i >= 0; i--) {
            Map<String, Object> description = describeExchangeOrThrow(page.exchanges().get(i), includeBodies);
            long additionalExchangeBytes = measureExchangeBytes(description, describedExchanges.isEmpty(), separatorBytes);

            boolean hasMore = page.hasMore() || i > 0;
            Integer candidateNextOffset = nextOffset(page.offset(), describedExchanges.size() + 1, hasMore);
            long trackedSize = responseEnvelope.fixedBytes()
                    + prefixBytes
                    + exchangesBytes
                    + additionalExchangeBytes
                    + measureExchangePageSuffixBytes(hasMore, candidateNextOffset);
            if (trackedSize > maxResponseSizeLimit) {
                if (describedExchanges.isEmpty()) {
                    throw new InvalidToolArgumentsException("Tool argument '" + ARG_MAX_RESPONSE_SIZE + "' must be at least " + trackedSize + " bytes to return the next exchange page");
                }
                break;
            }

            describedExchanges.addFirst(description);
            exchangesBytes += additionalExchangeBytes;
        }

        boolean hasMore = page.hasMore() || describedExchanges.size() < page.exchanges().size();
        return createExchangePageResponse(
                call,
                describedExchanges,
                hasMore,
                nextOffset(page.offset(), describedExchanges.size(), hasMore)
        );
    }

    MCPToolsCallResponse buildSingleExchangeResponse(MCPToolsCall call, long exchangeId, @Nullable AbstractExchange exchange, boolean includeBodies) {
        if (exchange == null) {
            return MCPToolsCallResponse.toolError(call, "Exchange with id " + exchangeId + " was not found");
        }

        Map<String, Object> description = MCPUtil.describeExchange(exchange, includeBodies, payloadSanitizer);
        if (description == null) {
            return MCPToolsCallResponse.toolError(call, "Exchange with id " + exchangeId + " has no response yet");
        }

        return MCPToolsCallResponse.from(call)
                .withJson(Map.of("exchange", description));
    }

    private List<Map<String, Object>> describeExchanges(List<AbstractExchange> exchanges, boolean includeBodies) {
        return exchanges.stream()
                .map(exchange -> describeExchangeOrThrow(exchange, includeBodies))
                .toList();
    }

    private Map<String, Object> describeExchangeOrThrow(AbstractExchange exchange, boolean includeBodies) {
        Map<String, Object> description = MCPUtil.describeExchange(exchange, includeBodies, payloadSanitizer);
        if (description == null) {
            throw new IllegalStateException("Expected exchange response data to be present for paging");
        }
        return description;
    }

    private MCPToolsCallResponse createExchangePageResponse(MCPToolsCall call, List<Map<String, Object>> exchanges, boolean hasMore, @Nullable Integer nextOffset) {
        return MCPToolsCallResponse.from(call)
                .withJson(buildExchangePagePayload(exchanges, hasMore, nextOffset));
    }

    private Map<String, Object> buildExchangePagePayload(List<Map<String, Object>> exchanges, boolean hasMore, @Nullable Integer nextOffset) {
        LinkedHashMap<String, Object> payload = new LinkedHashMap<>();
        payload.put("exchanges", exchanges);
        payload.put("hasMore", hasMore);
        if (nextOffset != null) {
            payload.put("nextOffset", nextOffset);
        }
        return payload;
    }

    private @Nullable Integer nextOffset(int offset, int returnedCount, boolean hasMore) {
        return hasMore ? offset + returnedCount : null;
    }

    private long measureExchangeBytes(Map<String, Object> description, boolean firstExchange, long separatorBytes) {
        return measureEscapedJsonStringContentSize(serializeJson(description)) + (firstExchange ? 0 : separatorBytes);
    }

    // Measure the fixed JSON-RPC/MCP wrapper once with a placeholder so the byte limit
    // applies to the final serialized response body, not just the unescaped payload text.
    private TextResponseEnvelope measureTextResponseEnvelope(MCPToolsCall call) {
        String marker = "__MEMBRANE_MCP_TEXT_PLACEHOLDER_" + UUID.randomUUID() + "__";
        try {
            String responseJson = MCPToolsCallResponse.from(call).withText(marker).toJson();
            int markerIndex = responseJson.indexOf(marker);
            if (markerIndex < 0) {
                throw new IllegalStateException("Could not locate placeholder marker in serialized MCP response");
            }

            return new TextResponseEnvelope(
                    utf8Size(responseJson.substring(0, markerIndex)),
                    utf8Size(responseJson.substring(markerIndex + marker.length()))
            );
        } catch (IOException e) {
            throw new RuntimeException("Failed to serialize MCP response envelope", e);
        }
    }

    private long measureExchangePageSuffixBytes(boolean hasMore, @Nullable Integer nextOffset) {
        return measureEscapedJsonStringContentSize(buildExchangePageSuffix(hasMore, nextOffset));
    }

    private String buildExchangePageSuffix(boolean hasMore, @Nullable Integer nextOffset) {
        return "],\"hasMore\":" + hasMore + (nextOffset == null ? "" : ",\"nextOffset\":" + nextOffset) + "}";
    }

    private String serializeJson(Object value) {
        try {
            return OM.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize JSON value", e);
        }
    }

    private long measureEscapedJsonStringContentSize(String value) {
        try {
            return OM.writeValueAsBytes(value).length - 2;
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize JSON string content", e);
        }
    }

    private static long utf8Size(String value) {
        return value.getBytes(StandardCharsets.UTF_8).length;
    }

    private static @Nullable Integer getOptionalPort(MCPToolsCall call) {
        return call.getArgument(ARG_PORT) == null ? null : getOptionalIntArgument(call, ARG_PORT, -1, 1, 65535);
    }

    private static @Nullable Integer getOptionalMaxResponseSize(MCPToolsCall call) {
        return call.getArgument(ARG_MAX_RESPONSE_SIZE) == null ? null : getOptionalSizeArgument(call, ARG_MAX_RESPONSE_SIZE, -1, 1, MAX_VALUE);
    }

    record ExchangeQuery(
            @Nullable String host,
            @Nullable Integer port,
            @Nullable String pathPattern,
            int offset,
            int limit,
            boolean includeBodies,
            @Nullable Integer maxResponseSize
    ) {
    }

    record ExchangeLookupQuery(long id, boolean includeBodies) {
    }

    record ExchangePage(List<AbstractExchange> exchanges, boolean hasMore, int offset) {
    }

    private record TextResponseEnvelope(long prefixBytes, long suffixBytes) {
        private long fixedBytes() {
            return prefixBytes + suffixBytes;
        }
    }
}
