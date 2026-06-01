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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.predic8.membrane.core.http.Request;
import com.predic8.membrane.core.http.Response;
import com.predic8.membrane.core.router.DefaultRouter;
import com.predic8.membrane.core.util.ConfigurationException;
import com.predic8.membrane.core.util.allowdeny.Allow;
import com.predic8.membrane.core.util.allowdeny.Deny;
import com.predic8.membrane.core.util.allowdeny.Rule;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.predic8.membrane.core.http.MimeType.APPLICATION_JSON;
import static com.predic8.membrane.core.http.MimeType.TEXT_PLAIN;
import static com.predic8.membrane.core.interceptor.Outcome.CONTINUE;
import static com.predic8.membrane.core.interceptor.Outcome.RETURN;
import static org.junit.jupiter.api.Assertions.*;

public class JsonRPCProtectionInterceptorTest {

    private static final ObjectMapper OM = new ObjectMapper();

    @Test
    void allowRuleWinsBeforeDenyRule() throws Exception {
        var interceptor = interceptor(List.of(
                allow("^rpc\\.health$"),
                deny("^rpc\\..*$")
        ));

        var exc = exchange("""
                {"jsonrpc":"2.0","id":1,"method":"rpc.health"}
                """);

        assertEquals(CONTINUE, interceptor.handleRequest(exc));
    }

    @Test
    void firstMatchingDenyRuleRejectsRequest() throws Exception {
        var interceptor = interceptor(List.of(
                deny("^rpc\\..*$"),
                allow("^rpc\\.health$")
        ));

        var exc = exchange("""
                {"jsonrpc":"2.0","id":1,"method":"rpc.health"}
                """);

        assertEquals(RETURN, interceptor.handleRequest(exc));
        assertError(exc.getResponse(), 403, "JSON-RPC method 'rpc.health' is not allowed.");
    }

    @Test
    void batchRequestsCanBeDisabled() throws Exception {
        BatchRule batchRule = new BatchRule();
        batchRule.setEnabled(false);
        var interceptor = interceptor(List.of(), new JsonRPCParams(), batchRule);

        var exc = exchange("""
                [{"jsonrpc":"2.0","id":1,"method":"rpc.health"}]
                """);

        assertEquals(RETURN, interceptor.handleRequest(exc));
        assertBatchError(exc.getResponse(), 400, "Batch requests are disabled.");
    }

    @Test
    void batchSizeIsLimited() throws Exception {
        BatchRule batchRule = new BatchRule();
        batchRule.setMaxSize(1);
        var interceptor = interceptor(List.of(), new JsonRPCParams(), batchRule);

        var exc = exchange("""
                [
                  {"jsonrpc":"2.0","id":1,"method":"rpc.one"},
                  {"jsonrpc":"2.0","id":2,"method":"rpc.two"}
                ]
                """);

        assertEquals(RETURN, interceptor.handleRequest(exc));
        assertBatchError(exc.getResponse(), 400, "Batch request exceeds maxSize of 1.");
    }

    @Test
    void invalidRegexIsRejected() {
        Allow allow = new Allow();
        assertThrows(ConfigurationException.class, () -> allow.setPattern("[*"));
    }

    @Test
    void nonJsonContentTypeIsRejected() throws Exception {
        var interceptor = interceptor(List.of());

        var exc = Request.post("/")
                .contentType(TEXT_PLAIN)
                .body("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"rpc.health\"}")
                .buildExchange();

        assertEquals(RETURN, interceptor.handleRequest(exc));
        assertError(exc.getResponse(), 415, "Content-Type text/plain is not supported. Expected application/json.");
    }

    @Test
    void paramsValidation() throws Exception {
        JsonRPCParams params = new JsonRPCParams();
        params.setParams(Map.of(
                "rpc.echo", "classpath:/json/rpc/echo-params.schema.json"
        ));
        var interceptor = interceptor(List.of(), params);

        var exc = exchange("""
                {"jsonrpc":"2.0","id":1,"method":"rpc.echo","params":{"message":"hello"}}
                """);

        assertEquals(CONTINUE, interceptor.handleRequest(exc));

        var exc2 = exchange("""
                {"jsonrpc":"2.0","id":1,"method":"rpc.echo","params":{}}
                """);

        assertEquals(RETURN, interceptor.handleRequest(exc2));
        assertErrorContains(exc2.getResponse(), 400, "Invalid params for method 'rpc.echo'");
    }

    @Test
    void paramsValidationSupportsRegexMethodPatterns() throws Exception {
        JsonRPCParams params = new JsonRPCParams();
        params.setParams(Map.of(
                "^rpc\\..*$", "classpath:/json/rpc/generic-rpc-params.schema.json"
        ));
        var interceptor = interceptor(List.of(), params);

        var exc = exchange("""
                {"jsonrpc":"2.0","id":1,"method":"rpc.health","params":{"code":1}}
                """);

        assertEquals(CONTINUE, interceptor.handleRequest(exc));
    }

    @Test
    void paramsValidationUsesFirstMatchingRegexPattern() throws Exception {
        JsonRPCParams params = new JsonRPCParams();
        LinkedHashMap<String, String> map = new LinkedHashMap<>();
        map.put("^rpc\\.echo$", "classpath:/json/rpc/echo-params.schema.json");
        map.put("^rpc\\..*$", "classpath:/json/rpc/generic-rpc-params.schema.json");
        params.setParams(map);
        var interceptor = interceptor(List.of(), params);

        var exc = exchange("""
                {"jsonrpc":"2.0","id":1,"method":"rpc.echo","params":{"code":1}}
                """);

        assertEquals(RETURN, interceptor.handleRequest(exc));
        assertErrorContains(exc.getResponse(), 400, "Invalid params for method 'rpc.echo'");
    }

    @Test
    void xmlStyleParamMappingsAreSupported() throws Exception {
        JsonRPCParams params = new JsonRPCParams();
        params.setParamMappings(List.of(
                new JsonRPCParams.Param("^rpc\\.echo$", "classpath:/json/rpc/echo-params.schema.json")
        ));
        var interceptor = interceptor(List.of(), params);

        var exc = exchange("""
                {"jsonrpc":"2.0","id":1,"method":"rpc.echo","params":{"message":"hello"}}
                """);

        assertEquals(CONTINUE, interceptor.handleRequest(exc));
    }

    private JsonRPCProtectionInterceptor interceptor(List<Rule> rules) {
        return interceptor(rules, new JsonRPCParams());
    }

    private JsonRPCProtectionInterceptor interceptor(List<Rule> rules, JsonRPCParams params) {
        return interceptor(rules, params, new BatchRule());
    }

    private JsonRPCProtectionInterceptor interceptor(List<Rule> rules, JsonRPCParams params, BatchRule batchRule) {
        var interceptor = new JsonRPCProtectionInterceptor();
        interceptor.setBatch(batchRule);
        interceptor.setMethods(rules);
        interceptor.setParams(params);
        interceptor.init(new DefaultRouter());
        return interceptor;
    }

    private com.predic8.membrane.core.exchange.Exchange exchange(String body) throws Exception {
        return Request.post("/")
                .contentType(APPLICATION_JSON)
                .body(body)
                .buildExchange();
    }

    private Allow allow(String pattern) {
        Allow allow = new Allow();
        allow.setPattern(pattern);
        return allow;
    }

    private Deny deny(String pattern) {
        Deny deny = new Deny();
        deny.setPattern(pattern);
        return deny;
    }

    private void assertError(Response response, int statusCode, String message) throws Exception {
        assertEquals(statusCode, response.getStatusCode());
        JsonNode node = OM.readTree(response.getBodyAsStringDecoded());
        assertEquals(message, node.path("error").path("message").asText());
    }

    private void assertErrorContains(Response response, int statusCode, String messagePart) throws Exception {
        assertEquals(statusCode, response.getStatusCode());
        JsonNode node = OM.readTree(response.getBodyAsStringDecoded());
        assertTrue(node.path("error").path("message").asText().contains(messagePart));
    }

    private void assertBatchError(Response response, int statusCode, String message) throws Exception {
        assertEquals(statusCode, response.getStatusCode());
        JsonNode node = OM.readTree(response.getBodyAsStringDecoded());
        assertTrue(node.isArray());
        assertEquals(1, node.size());
        assertEquals(message, node.get(0).path("error").path("message").asText());
    }
}
