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

import com.predic8.membrane.core.*;
import com.predic8.membrane.core.exchange.*;
import com.predic8.membrane.core.http.*;
import com.predic8.membrane.core.interceptor.*;
import com.predic8.membrane.core.openapi.serviceproxy.*;
import com.predic8.membrane.core.security.*;
import org.junit.jupiter.api.*;

import static com.predic8.membrane.core.http.Request.METHOD_POST;
import static com.predic8.membrane.core.interceptor.Outcome.*;
import static com.predic8.membrane.core.openapi.serviceproxy.OpenAPISpec.YesNoOpenAPIOption.*;
import static com.predic8.membrane.core.openapi.util.TestUtils.*;
import static com.predic8.membrane.core.security.ApiKeySecurityScheme.In.*;
import static com.predic8.membrane.core.security.OAuth2SecurityScheme.CLIENT_CREDENTIALS;
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
        assertEquals(403, exc.getResponse().getStatusCode());
    }

    @Test
    void rightFlowAndScopes() throws Exception {
        Exchange exc = getExchange(METHOD_POST, "/write-pet", CLIENT_CREDENTIALS.scopes("write:pets","read:pets"));


        Outcome actual = interceptor.handleRequest(exc);


        System.out.println("exc.getResponse().getBodyAsStringDecoded() = " + exc.getResponse().getBodyAsStringDecoded());

        assertEquals(CONTINUE, actual);

        assertEquals(0, exc.getResponse().getStatusCode());
    }

    @Test
    void inHeader() throws Exception {
        // Check ignore case
        assertEquals(CONTINUE, interceptor.handleRequest(getExchange("/in-header",new ApiKeySecurityScheme(HEADER,"X-Api-KEY"))));
    }

}