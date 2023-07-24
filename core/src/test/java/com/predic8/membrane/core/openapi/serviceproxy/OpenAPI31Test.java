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

import com.predic8.membrane.core.*;
import com.predic8.membrane.core.exchange.*;
import com.predic8.membrane.core.http.*;
import com.predic8.membrane.core.interceptor.*;
import com.predic8.membrane.core.util.*;
import org.junit.jupiter.api.*;

import static com.predic8.membrane.core.openapi.util.TestUtils.createProxy;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class OpenAPI31Test {

    OpenAPIInterceptor interceptor;

    OpenAPISpec petstore_v3_1;

    Exchange exc = new Exchange(null);

    @BeforeEach
    public void setUp() throws Exception {
        Router router = new Router();
        router.setUriFactory(new URIFactory());

        petstore_v3_1 = new OpenAPISpec();
        petstore_v3_1.location = "src/test/resources/openapi/specs/petstore-v3.1.json";

        exc.setRequest(new Request.Builder().method("GET").build());

        interceptor = new OpenAPIInterceptor(createProxy(router, petstore_v3_1));
        interceptor.init(router);
    }

    @Test
    void simple() throws Exception {
        exc.getRequest().setUri("/pets");
        assertEquals(Outcome.RETURN, interceptor.handleRequest(exc));
    }
}
