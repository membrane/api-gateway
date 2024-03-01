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
import com.predic8.membrane.core.util.*;
import org.jetbrains.annotations.*;
import org.junit.jupiter.api.*;

import java.net.*;
import java.util.*;

import static com.predic8.membrane.core.exchange.Exchange.*;
import static com.predic8.membrane.core.interceptor.Outcome.*;
import static com.predic8.membrane.core.openapi.serviceproxy.OpenAPISpec.YesNoOpenAPIOption.*;
import static com.predic8.membrane.core.openapi.util.TestUtils.*;
import static com.predic8.membrane.core.security.ApiKeySecurityScheme.In.*;
import static org.junit.jupiter.api.Assertions.*;


public class ApiKeySecurityTest extends AbstractSecurityTest {

    private OpenAPIInterceptor interceptor;

    @BeforeEach
    void setUpSpec() throws Exception {
        OpenAPISpec spec = new OpenAPISpec();
        spec.location = "src/test/resources/openapi/specs/security/api-key.yml";
        spec.validateRequests = YES;

        Router router = getRouter();
        interceptor = new OpenAPIInterceptor(createProxy(router, spec));
        interceptor.init(router);
    }

    @Test
    void noHeader() throws Exception {
        assertEquals(RETURN, interceptor.handleRequest(getExchange("/in-header",null)));
    }

    @Test
    void inHeader() throws Exception {
        // Check ignore case
        assertEquals(CONTINUE, interceptor.handleRequest(getExchange("/in-header",new ApiKeySecurityScheme(HEADER,"X-Api-KEY"))));
    }

    @Test
    void inQuery() throws Exception {
        // Check ignore case
        assertEquals(CONTINUE, interceptor.handleRequest(getExchange("/in-query",new ApiKeySecurityScheme(QUERY,"api-key"))));
    }

    @Test
    void inCookie() throws Exception {
        // Check ignore case
        assertEquals(CONTINUE, interceptor.handleRequest(getExchange("/in-cookie",new ApiKeySecurityScheme(COOKIE,"API-KEY"))));
    }

    @Test
    void inHeaderWrongName() throws Exception {
        assertEquals(RETURN, interceptor.handleRequest(getExchange("/in-header", new ApiKeySecurityScheme(HEADER,"X-APIKEY"))));
    }

    @Test
    void inHeaderWrongIn() throws Exception {
        Exchange exc = getExchange("/in-header", new ApiKeySecurityScheme(QUERY, "X-APIKEY"));
        assertEquals(RETURN, interceptor.handleRequest(exc));
        assertEquals(403,exc.getResponse().getStatusCode());
        dumpResponseBody(exc);
    }
}