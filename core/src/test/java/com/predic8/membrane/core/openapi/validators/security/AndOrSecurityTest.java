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
import com.predic8.membrane.core.openapi.serviceproxy.*;
import com.predic8.membrane.core.security.*;
import org.junit.jupiter.api.*;

import java.util.*;

import static com.predic8.membrane.core.exchange.Exchange.SECURITY_SCHEMES;
import static com.predic8.membrane.core.interceptor.Outcome.*;
import static com.predic8.membrane.core.openapi.serviceproxy.OpenAPISpec.YesNoOpenAPIOption.*;
import static com.predic8.membrane.core.openapi.util.TestUtils.*;
import static com.predic8.membrane.core.security.ApiKeySecurityScheme.In.*;
import static com.predic8.membrane.core.security.BasicHttpSecurityScheme.*;
import static org.junit.jupiter.api.Assertions.*;


public class AndOrSecurityTest extends AbstractSecurityTest {

    private OpenAPIInterceptor interceptor;

    @BeforeEach
    void setUpSpec() throws Exception {
        OpenAPISpec spec = new OpenAPISpec();
        spec.location = "src/test/resources/openapi/specs/security/and-or.yml";
        spec.validateRequests = YES;

        Router router = getRouter();
        interceptor = new OpenAPIInterceptor(createProxy(router, spec));
        interceptor.init(router);
    }

    @Test
    void orBasic() throws Exception {
        assertEquals(CONTINUE, interceptor.handleRequest(getExchange("/one",new BasicHttpSecurityScheme())));
    }

    @Test
    void orApiKey() throws Exception {
        assertEquals(CONTINUE, interceptor.handleRequest(getExchange("/one",new ApiKeySecurityScheme(HEADER,"X-API-KEY"))));
    }

    @Test
    void orBothKey() throws Exception {
        Exchange exc = Request.get("/one").buildExchange();
        exc.setOriginalRequestUri("/one");
        exc.setProperty(SECURITY_SCHEMES, List.of(new ApiKeySecurityScheme(HEADER,"X-API-KEY"), BASIC));
        assertEquals(CONTINUE, interceptor.handleRequest(exc));
    }

    @Test
    void bothWithOneBasic() throws Exception {
        assertEquals(RETURN, interceptor.handleRequest(getExchange("/both",BASIC)));
    }

    @Test
    void bothWithOneApiKey() throws Exception {
        assertEquals(RETURN, interceptor.handleRequest(getExchange("/both",new ApiKeySecurityScheme(HEADER,"API-KEY"))));
    }
}