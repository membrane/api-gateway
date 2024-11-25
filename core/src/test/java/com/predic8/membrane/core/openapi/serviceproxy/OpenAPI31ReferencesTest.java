/*
 *  Copyright 2023 predic8 GmbH, www.predic8.com
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

package com.predic8.membrane.core.openapi.serviceproxy;

import com.predic8.membrane.core.Router;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.Request;
import com.predic8.membrane.core.interceptor.Outcome;
import com.predic8.membrane.core.util.URIFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.predic8.membrane.core.openapi.util.TestUtils.createProxy;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class OpenAPI31ReferencesTest {

    OpenAPIInterceptor interceptor;

    OpenAPISpec referencesTest;

    Exchange exc = new Exchange(null);

    @BeforeEach
    public void setUp() throws Exception {
        Router router = new Router();
        router.setUriFactory(new URIFactory());

        referencesTest = new OpenAPISpec();
        referencesTest.location = "src/test/resources/openapi/specs/oas31/request-reference.yaml";

        exc.setRequest(new Request.Builder().method("GET").build());

        interceptor = new OpenAPIInterceptor(createProxy(router, referencesTest), router);
        interceptor.init(router);
    }

    @Test
    void simple() throws Exception {
        exc.getRequest().setUri("/users");
        System.out.println(exc);
        assertEquals(Outcome.RETURN, interceptor.handleRequest(exc));
    }
}
