/* Copyright 2024 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */
package com.predic8.membrane.core.interceptor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.predic8.membrane.core.Router;
import com.predic8.membrane.core.RuleManager;
import com.predic8.membrane.core.config.Path;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.Request.Builder;
import com.predic8.membrane.core.openapi.serviceproxy.APIProxy;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.text.ParseException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ApisJsonInterceptorTest {
    private static final String RESPONSE_JSON = "{\"aid\":\"example.com:1234\",\"name\":\"API Collection\",\"description\":\"API Collection Description\",\"url\":\"http://example.com/apis.json\",\"created\":\"2024-07-15\",\"modified\":\"2024-07-15\",\"specificationVersion\":\"0.18\",\"apis\":[{\"aid\":\"example.com:/\",\"name\":\":80\",\"description\":\"N/A (Available only internally in APIProxy)\",\"humanUrl\":\"WIP\",\"baseUrl\":\"/\",\"image\":\"N/A (Available only internally in APIProxy)\",\"version\":\"N/A (Available only internally in APIProxy)\"}]}";
    private static ApisJsonInterceptor interceptor;

    @BeforeAll
    static void setUp() throws ParseException {
        interceptor = new ApisJsonInterceptor() {{
            setRootDomain("example.com");
            setCollectionId("1234");
            setCollectionName("API Collection");
            setDescription("API Collection Description");
            setApisJsonUrl("http://example.com/apis.json");
            setCreated("2024-07-15");
            setModified("2024-07-15");
        }};
    }

    @Test
    void responseTest() throws Exception {
        Exchange exc = new Builder().buildExchange();
        Router r = mock(Router.class);
        RuleManager rm = mock(RuleManager.class);
        when(r.getRuleManager()).thenReturn(rm);
        when(rm.getRules()).thenReturn(List.of(new APIProxy()));
        interceptor.init(r);
        interceptor.handleRequest(exc);
        assertEquals(RESPONSE_JSON, exc.getResponse().getBodyAsStringDecoded());
    }

    @Test
    void safePathAsStringTest() {
        assertEquals("/foo", interceptor.safePathAsString(new Path(false, "/foo")));
        assertEquals("/", interceptor.safePathAsString(null));
    }

    @Test
    void jsonNodeFromApiProxyTest() {
        APIProxy apiProxy = new APIProxy() {{
            setPath(new Path(false, "/baz"));
            setName("Demo API");
        }};

        JsonNode apiNode = interceptor.jsonNodeFromApiProxy(apiProxy);

        assertEquals("example.com" + ":/baz", apiNode.get("aid").asText());
        assertEquals("Demo API", apiNode.get("name").asText());
        assertEquals("N/A (Available only internally in APIProxy)", apiNode.get("description").asText());
        assertEquals("WIP", apiNode.get("humanUrl").asText());
        assertEquals("/baz", apiNode.get("baseUrl").asText());
        assertEquals("N/A (Available only internally in APIProxy)", apiNode.get("image").asText());
        assertEquals("N/A (Available only internally in APIProxy)", apiNode.get("version").asText());
    }
}