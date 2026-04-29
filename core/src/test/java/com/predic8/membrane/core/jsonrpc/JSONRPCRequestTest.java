package com.predic8.membrane.core.jsonrpc;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.params.provider.Arguments.of;

class JSONRPCRequestTest {

    @ParameterizedTest(name = "{0}")
    @MethodSource("validRequests")
    void parsesValidRequests(String name, String json, JSONRPCRequest expected) throws IOException {
        JSONRPCRequest parsed = JSONRPCRequest.parse(json);

        assertEquals(expected, parsed);
        assertEquals(expected.getParams(), parsed.getParams());
        assertEquals(expected.isNotification(), parsed.isNotification());
        assertEquals(expected.hasNamedParams(), parsed.hasNamedParams());
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("requestsForRoundTrip")
    void roundTripsViaStringAndInputStream(String name, JSONRPCRequest request) throws IOException {
        String json = request.toJson();

        assertEquals(request, JSONRPCRequest.parse(json));
        assertEquals(request, JSONRPCRequest.parse(input(json)));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("normalizedIds")
    void normalizesSupportedIds(String name, Object rawId, Object expectedId) {
        JSONRPCRequest request = new JSONRPCRequest(rawId, "sum", List.of());

        assertEquals(expectedId, request.getId());
        assertTrue(request.isIdPresent());
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidIds")
    void rejectsUnsupportedIds(String name, Object rawId, String messageFragment) {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> new JSONRPCRequest(rawId, "sum", List.of())
        );

        assertMessageContains(exception, messageFragment);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidRequests")
    void rejectsInvalidRequests(String name, String json, String messageFragment) {
        IOException exception = assertThrows(IOException.class, () -> JSONRPCRequest.parse(json));

        assertMessageContains(exception, messageFragment);
    }

    @Test
    void switchingParamsRepresentationClearsTheOtherOne() {
        JSONRPCRequest request = new JSONRPCRequest("req-1", "tools/call", Map.of("name", "echo"));

        request.setParamsList(List.of("echo", Map.of("value", 1)));

        assertNull(request.getParamsMap());
        assertEquals(List.of("echo", Map.of("value", 1)), request.getParamsList());

        request.setParamsMap(Map.of("name", "echo"));

        assertEquals(Map.of("name", "echo"), request.getParamsMap());
        assertNull(request.getParamsList());
    }

    private static Stream<Arguments> validRequests() {
        return Stream.of(
                of(
                        "named params request",
                        """
                        {"jsonrpc":"2.0","id":"req-1","method":"tools/list","params":{"cursor":"abc","limit":10}}
                        """,
                        new JSONRPCRequest("req-1", "tools/list", Map.of("cursor", "abc", "limit", 10))
                ),
                of(
                        "positional params request",
                        """
                        {"jsonrpc":"2.0","id":7,"method":"sum","params":[1,2,3]}
                        """,
                        new JSONRPCRequest(7, "sum", List.of(1, 2, 3))
                ),
                of(
                        "notification without id",
                        """
                        {"jsonrpc":"2.0","method":"notifications/initialized"}
                        """,
                        notification("notifications/initialized")
                ),
                of(
                        "request with explicit null id",
                        """
                        {"jsonrpc":"2.0","id":null,"method":"shutdown"}
                        """,
                        requestWithNullId("shutdown")
                )
        );
    }

    private static Stream<Arguments> requestsForRoundTrip() {
        return Stream.of(
                of("named params round trip", new JSONRPCRequest("req-1", "tools/list", Map.of("cursor", "abc", "limit", 10))),
                of("positional params round trip", new JSONRPCRequest(7, "sum", List.of(1, 2, 3))),
                of("notification round trip", notification("notifications/initialized")),
                of("explicit null id round trip", new JSONRPCRequest(null, true, "shutdown", (List<Object>) null))
        );
    }

    private static Stream<Arguments> normalizedIds() {
        return Stream.of(
                of("string id stays string", "req-1", "req-1"),
                of("integer id becomes long", 7, 7L),
                of("short id becomes long", (short) 3, 3L),
                of("big integer in range becomes long", BigInteger.valueOf(11), 11L),
                of("null id stays null", null, null)
        );
    }

    private static Stream<Arguments> invalidIds() {
        return Stream.of(
                of("decimal id", 1.5d, "id must be String, Integer, or null"),
                of("out of range big integer id", BigInteger.valueOf(Long.MAX_VALUE).add(BigInteger.ONE), "id is out of long range"),
                of("arbitrary object id", new Object(), "id must be String, Integer, or null")
        );
    }

    private static Stream<Arguments> invalidRequests() {
        return Stream.of(
                of("root must be object", "[]", "expected JSON object"),
                of("jsonrpc version is required", """
                        {"method":"sum"}
                        """, "Unsupported or missing jsonrpc version in request"),
                of("method must be textual", """
                        {"jsonrpc":"2.0","method":1}
                        """, "'method' must be a string"),
                of("method must not be blank", """
                        {"jsonrpc":"2.0","method":"   "}
                        """, "method must not be blank"),
                of("id must be integral when numeric", """
                        {"jsonrpc":"2.0","id":1.5,"method":"sum"}
                        """, "'id' must be string, integer, or null"),
                of("params must be object or array", """
                        {"jsonrpc":"2.0","id":"req-1","method":"sum","params":true}
                        """, "'params' must be array or object")
        );
    }

    private static JSONRPCRequest notification(String method) {
        return new JSONRPCRequest(null, false, method, (Map<String, Object>) null);
    }

    private static JSONRPCRequest requestWithNullId(String method) {
        return new JSONRPCRequest(null, true, method, (Map<String, Object>) null);
    }

    private static ByteArrayInputStream input(String json) {
        return new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8));
    }

    private static void assertMessageContains(Throwable exception, String messageFragment) {
        assertTrue(exception.getMessage().contains(messageFragment));
    }
}
