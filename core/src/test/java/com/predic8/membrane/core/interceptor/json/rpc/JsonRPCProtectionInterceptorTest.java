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
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.predic8.membrane.core.http.MimeType.APPLICATION_JSON;
import static com.predic8.membrane.core.interceptor.Outcome.CONTINUE;
import static com.predic8.membrane.core.interceptor.Outcome.RETURN;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
        var interceptor = interceptor(List.of());
        BatchRule batchRule = new BatchRule();
        batchRule.setEnabled(false);
        interceptor.setBatch(batchRule);

        var exc = exchange("""
                [{"jsonrpc":"2.0","id":1,"method":"rpc.health"}]
                """);

        assertEquals(RETURN, interceptor.handleRequest(exc));
        assertBatchError(exc.getResponse(), 400, "Batch requests are disabled.");
    }

    @Test
    void batchSizeIsLimited() throws Exception {
        var interceptor = interceptor(List.of());
        BatchRule batchRule = new BatchRule();
        batchRule.setMaxSize(1);
        interceptor.setBatch(batchRule);

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
        assertThrows(ConfigurationException.class, () -> allow.setMethod("[*"));
    }

    @Test
    void paramsValidation() throws Exception {
        JsonRPCParams params = new JsonRPCParams();
        params.setParams(java.util.Map.of(
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

    private JsonRPCProtectionInterceptor interceptor(List<Rule> rules) {
        return interceptor(rules, new JsonRPCParams());
    }

    private JsonRPCProtectionInterceptor interceptor(List<Rule> rules, JsonRPCParams params) {
        var interceptor = new JsonRPCProtectionInterceptor();
        interceptor.setRules(rules);
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

    private Allow allow(String method) {
        Allow allow = new Allow();
        allow.setMethod(method);
        return allow;
    }

    private Deny deny(String method) {
        Deny deny = new Deny();
        deny.setMethod(method);
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
