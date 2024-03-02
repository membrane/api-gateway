/*
 *  Copyright 2022 predic8 GmbH, www.predic8.com
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */

package com.predic8.membrane.core.openapi.validators.security;

import com.fasterxml.jackson.databind.*;
import com.predic8.membrane.core.*;
import com.predic8.membrane.core.exchange.*;
import com.predic8.membrane.core.interceptor.*;
import com.predic8.membrane.core.openapi.serviceproxy.*;
import org.junit.jupiter.api.*;

import static com.predic8.membrane.core.http.Request.*;
import static com.predic8.membrane.core.interceptor.Outcome.*;
import static com.predic8.membrane.core.openapi.serviceproxy.OpenAPISpec.YesNoOpenAPIOption.*;
import static com.predic8.membrane.core.openapi.util.TestUtils.*;
import static com.predic8.membrane.core.security.OAuth2SecurityScheme.*;
import static org.junit.jupiter.api.Assertions.*;


public class OAuth2SecurityTest extends AbstractSecurityTest {

    private OpenAPIInterceptor interceptor;

    @BeforeEach
    void setUpSpec() throws Exception {
        OpenAPISpec spec = new OpenAPISpec();
        spec.location = "src/test/resources/openapi/specs/security/oauth2.yml";
        spec.validateRequests = YES;

        Router router = getRouter();
        interceptor = new OpenAPIInterceptor(createProxy(router, spec));
        interceptor.init(router);
    }

    @Test
    void noOAuth2Authentication() throws Exception {
        Exchange exc = getExchange("/get-pet", null);
        assertEquals(RETURN, interceptor.handleRequest(exc));
        assertEquals(401, exc.getResponse().getStatusCode());
    }

    @Test
    void wrongFlow() throws Exception {
        Exchange exc = getExchange(METHOD_POST, "/write-pet", PASSWORD.scopes("write:pets","read:pets"));

        Outcome actual = interceptor.handleRequest(exc);

        assertEquals(RETURN, actual);

        assertEquals(401, exc.getResponse().getStatusCode());

        ObjectMapper om = new ObjectMapper();

        JsonNode root = om.readTree(exc.getResponse().getBody().getContentAsStream());

        JsonNode errors = root.get("validationErrors").get("REQUEST/");
        assertEquals(2,errors.size()); // If optimized one should be right
        assertTrue(errors.get(0).get("message").asText().contains("'Client Credentials' is required"));
    }

    @Test
    void globalAndOperationRightFlowAndScopes() throws Exception {
        assertEquals(CONTINUE, interceptor.handleRequest(getExchange(METHOD_POST, "/write-pet", CLIENT_CREDENTIALS.scopes("write:pets","read:pets"))));
    }

    @Test
    void globalRightFlowAndScopes() throws Exception {
        assertEquals(CONTINUE, interceptor.handleRequest(getExchange(METHOD_GET, "/get-pet", CLIENT_CREDENTIALS.scopes("read:pets"))));
    }

}