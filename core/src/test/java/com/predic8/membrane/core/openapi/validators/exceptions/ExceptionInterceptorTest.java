/*
 *  Copyright 2024 predic8 GmbH, www.predic8.com
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

package com.predic8.membrane.core.openapi.validators.exceptions;

import com.fasterxml.jackson.databind.*;
import com.predic8.membrane.core.*;
import com.predic8.membrane.core.exchange.*;
import com.predic8.membrane.core.http.*;
import com.predic8.membrane.core.openapi.serviceproxy.*;
import com.predic8.membrane.core.openapi.validators.security.*;
import org.junit.jupiter.api.*;
import org.springframework.http.*;

import static com.predic8.membrane.core.interceptor.Outcome.*;
import static com.predic8.membrane.core.openapi.serviceproxy.OpenAPISpec.YesNoOpenAPIOption.*;
import static com.predic8.membrane.core.openapi.util.TestUtils.*;
import static org.junit.jupiter.api.Assertions.*;

public class ExceptionInterceptorTest extends AbstractSecurityValidatorTest {

    private final ObjectMapper om = new ObjectMapper();

    private OpenAPIInterceptor interceptor;


    @BeforeEach
    void setUpSpec() throws Exception {
        OpenAPISpec spec = new OpenAPISpec();
        spec.location = "src/test/resources/openapi/specs/exceptions/error-in-spec.yml";
        spec.validateRequests = YES;
        spec.validateResponses = YES;

        Router router = getRouter();
        interceptor = new OpenAPIInterceptor(createProxy(router, spec),router);
        interceptor.init(router);
    }

    @Test
    void paramWithNoType() throws Exception {
        Exchange exc = getExchange("/param-with-no-type?bar=7", null);
        assertEquals(RETURN, interceptor.handleRequest(exc));
        assertEquals(500,exc.getResponse().getStatusCode());
        JsonNode json = om.readTree(exc.getResponse().getBodyAsStream());
        assertEquals("https://membrane-api.io/error/internal",json.get("type").asText());
    }

    @Test
    void unknownType() throws Exception {
        Exchange exc = new Request.Builder().get("/unknown-type").buildExchange();
        exc.setResponse(Response.ok().body("{}").contentType(MediaType.APPLICATION_JSON.toString()).build());
        exc.setOriginalRequestUri("/unknown-type");

        assertEquals(CONTINUE, interceptor.handleRequest(exc));
        assertEquals(RETURN, interceptor.handleResponse(exc));
        assertEquals(500,exc.getResponse().getStatusCode());
    }

    @Test
    void nowhere() throws Exception {
        Exchange exc = new Request.Builder().get("/nowhere").buildExchange();
        exc.setResponse(Response.ok().body("{}").contentType(MediaType.APPLICATION_JSON.toString()).build());
        exc.setOriginalRequestUri("/nowhere");

        assertEquals(CONTINUE, interceptor.handleRequest(exc));
        assertEquals(RETURN, interceptor.handleResponse(exc));

        assertEquals(500,exc.getResponse().getStatusCode());
        JsonNode json = om.readTree(exc.getResponse().getBodyAsStream());
        assertEquals("https://membrane-api.io/error/internal",json.get("type").asText());
    }



}