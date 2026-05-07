package com.predic8.membrane.core.jsonrpc;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.params.provider.Arguments.of;

class JSONRPCResponseTest {

    @ParameterizedTest(name = "{0}")
    @MethodSource("validResponses")
    void parsesValidResponses(String name, String json, JSONRPCResponse expected) throws IOException {
        JSONRPCResponse parsed = JSONRPCResponse.parse(json);

        assertEquals(expected, parsed);
        assertEquals(expected.isSuccess(), parsed.isSuccess());
        assertEquals(expected.isError(), parsed.isError());
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("responsesForRoundTrip")
    void roundTripsViaStringAndOutputStream(String name, JSONRPCResponse response) throws IOException {
        assertEquals(response, JSONRPCResponse.parse(response.toJson()));

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        response.writeTo(outputStream);

        assertEquals(response, JSONRPCResponse.parse(new ByteArrayInputStream(outputStream.toByteArray())));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("requestsWithResponseIds")
    void createsSuccessResponseFromRequest(String name, JSONRPCRequest request, Object expectedId) {
        JSONRPCResponse response = JSONRPCResponse.from(request, Map.of("ok", true));

        assertTrue(response.isSuccess());
        assertEquals(expectedId, response.getId());
        assertEquals(Map.of("ok", true), response.getResult());
    }

    @Test
    void rejectsCreatingResponseForNotification() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> JSONRPCResponse.from(notification("notifications/initialized"), Map.of("ok", true))
        );

        assertMessageContains(exception, "cannot create a JSON-RPC response for a notification");
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidIds")
    void rejectsUnsupportedIds(String name, Object rawId, String messageFragment) {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> JSONRPCResponse.success(rawId, "ok")
        );

        assertMessageContains(exception, messageFragment);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidResponses")
    void rejectsInvalidResponses(String name, String json, String messageFragment) {
        IOException exception = assertThrows(IOException.class, () -> JSONRPCResponse.parse(json));

        assertMessageContains(exception, messageFragment);
    }

    @Test
    void responseKindIsUndefinedUntilResultOrErrorIsSet() {
        JSONRPCResponse response = new JSONRPCResponse();

        IllegalStateException kindException = assertThrows(IllegalStateException.class, response::getResponseKind);
        IllegalStateException serializationException = assertThrows(IllegalStateException.class, response::toJson);

        assertMessageContains(kindException, "response kind is undefined");
        assertMessageContains(serializationException, "response kind is undefined");
    }

    @Test
    void parseSupportsInputStream() throws IOException {
        JSONRPCResponse parsed = JSONRPCResponse.parse(input("""
                {"jsonrpc":"2.0","id":"req-1","result":{"ok":true}}
                """));

        assertEquals(JSONRPCResponse.success("req-1", Map.of("ok", true)), parsed);
    }

    private static Stream<Arguments> validResponses() {
        return Stream.of(
                of("success response", """
                        {"jsonrpc":"2.0","id":"req-1","result":{"tools":["echo"]}}
                        """, JSONRPCResponse.success("req-1", Map.of("tools", List.of("echo")))),
                of("success response with null result", """
                        {"jsonrpc":"2.0","id":7,"result":null}
                        """, JSONRPCResponse.success(7, null)),
                of("error response with data", """
                        {"jsonrpc":"2.0","id":null,"error":{"code":-32602,"message":"Invalid params","data":{"field":"cursor"}}}
                        """, JSONRPCResponse.error(null, JSONRPCResponse.ERR_INVALID_PARAMS, "Invalid params", Map.of("field", "cursor")))
        );
    }

    private static Stream<Arguments> responsesForRoundTrip() {
        return Stream.of(
                of("success round trip", JSONRPCResponse.success("req-1", Map.of("tools", List.of("echo")))),
                of("success with null result round trip", JSONRPCResponse.success(7, null)),
                of("error round trip", JSONRPCResponse.error(null, JSONRPCResponse.ERR_INVALID_PARAMS, "Invalid params", Map.of("field", "cursor")))
        );
    }

    private static Stream<Arguments> requestsWithResponseIds() {
        return Stream.of(
                of("string id is echoed", new JSONRPCRequest("req-1", "tools/list", Map.of("cursor", "abc")), "req-1"),
                of("numeric id is normalized and echoed", new JSONRPCRequest(7, "sum", List.of(1, 2)), 7L),
                of("explicit null id is echoed", new JSONRPCRequest(null, true, "shutdown", (Map<String, Object>) null), null)
        );
    }

    private static Stream<Arguments> invalidIds() {
        return Stream.of(
                of("decimal id", 1.5d, "id must be String, Integer, or null"),
                of("out of range big integer id", BigInteger.valueOf(Long.MAX_VALUE).add(BigInteger.ONE), "id is out of long range"),
                of("arbitrary object id", new Object(), "id must be String, Integer, or null")
        );
    }

    private static Stream<Arguments> invalidResponses() {
        return Stream.of(
                of("root must be object", "[]", "expected JSON object"),
                of("jsonrpc version is required", """
                        {"id":"req-1","result":1}
                        """, "Unsupported or missing jsonrpc version in response"),
                of("id is required", """
                        {"jsonrpc":"2.0","result":1}
                        """, "'id' is required"),
                of("result and error are mutually exclusive", """
                        {"jsonrpc":"2.0","id":"req-1","result":1,"error":{"code":-32603,"message":"boom"}}
                        """, "'result' and 'error' are mutually exclusive"),
                of("either result or error must be present", """
                        {"jsonrpc":"2.0","id":"req-1"}
                        """, "either 'result' or 'error' must be present"),
                of("error must be object", """
                        {"jsonrpc":"2.0","id":"req-1","error":true}
                        """, "'error' must be a JSON object"),
                of("id must be integral when numeric", """
                        {"jsonrpc":"2.0","id":1.5,"result":1}
                        """, "'id' must be string, integer, or null"),
                of("error code must be integer", """
                        {"jsonrpc":"2.0","id":"req-1","error":{"code":"x","message":"boom"}}
                        """, "'code' must be an integer"),
                of("error message must be string", """
                        {"jsonrpc":"2.0","id":"req-1","error":{"code":-32603,"message":1}}
                        """, "'message' must be a string")
        );
    }

    private static JSONRPCRequest notification(String method) {
        return new JSONRPCRequest(null, false, method, (Map<String, Object>) null);
    }

    private static ByteArrayInputStream input(String json) {
        return new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8));
    }

    private static void assertMessageContains(Throwable exception, String messageFragment) {
        assertTrue(exception.getMessage().contains(messageFragment));
    }
}
