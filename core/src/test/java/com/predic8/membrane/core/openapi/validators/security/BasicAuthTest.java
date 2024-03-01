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
import com.predic8.membrane.core.http.Request;
import com.predic8.membrane.core.interceptor.*;
import com.predic8.membrane.core.interceptor.authentication.*;
import com.predic8.membrane.core.interceptor.authentication.session.*;
import com.predic8.membrane.core.openapi.serviceproxy.*;
import com.predic8.membrane.core.util.*;
import org.junit.jupiter.api.*;

import java.util.*;

import static com.predic8.membrane.core.interceptor.Outcome.*;
import static com.predic8.membrane.core.openapi.util.TestUtils.*;
import static org.junit.jupiter.api.Assertions.*;


public class BasicAuthTest {

    private OpenAPIInterceptor oasInterceptor;
    private BasicAuthenticationInterceptor baInterceptor;
    private Router router;

    @BeforeEach
    void setUpSpec() throws Exception {
        router = new Router();
        router.setUriFactory(new URIFactory());

        OpenAPISpec spec = new OpenAPISpec();
        spec.location = "src/test/resources/openapi/specs/security/http-basic.yml";
        spec.validateRequests = OpenAPISpec.YesNoOpenAPIOption.YES;

        oasInterceptor = new OpenAPIInterceptor(createProxy(router, spec));
        oasInterceptor.init(router);

        baInterceptor = new BasicAuthenticationInterceptor();
        ArrayList<StaticUserDataProvider.User> users = new ArrayList<>();
        StaticUserDataProvider.User user = new StaticUserDataProvider.User();
        user.setUsername("alice");
        user.setPassword("secret");
        users.add(user);
        baInterceptor.setUsers(users);

    }

    @Test
    void noAuthHeader() throws Exception {

        Exchange exc = Request.get("/v1/foo").buildExchange();
        exc.setOriginalRequestUri("/v1/foo");

        assertEquals(ABORT, baInterceptor.handleRequest(exc));  // TODO Should we return RETURN instead. How is it in OAuth2?
    }

    @Test
    void withAuthorizationHeader() throws Exception {

        Exchange exc = Request.get("/v1/foo").authorization("alice","secret").buildExchange();
        exc.setOriginalRequestUri("/v1/foo");

        Outcome outcome = baInterceptor.handleRequest(exc);

        assertEquals(CONTINUE,outcome);

        outcome = oasInterceptor.handleRequest(exc);

        assertEquals(CONTINUE, outcome);

    }
}