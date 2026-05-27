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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.Request;
import com.predic8.membrane.core.openapi.serviceproxy.OpenAPIInterceptor;
import com.predic8.membrane.core.openapi.serviceproxy.OpenAPISpec;
import com.predic8.membrane.core.openapi.validators.security.AbstractSecurityValidatorTest;
import com.predic8.membrane.core.router.Router;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.predic8.membrane.core.http.Request.get;
import static com.predic8.membrane.core.http.Response.ok;
import static com.predic8.membrane.core.interceptor.Outcome.CONTINUE;
import static com.predic8.membrane.core.interceptor.Outcome.RETURN;
import static com.predic8.membrane.core.openapi.serviceproxy.OpenAPISpec.YesNoOpenAPIOption.YES;
import static com.predic8.membrane.core.openapi.util.OpenAPITestUtils.createProxy;
import static com.predic8.membrane.test.TestUtil.getPathFromResource;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.http.MediaType.APPLICATION_JSON;

public class ExceptionInterceptorTest extends AbstractSecurityValidatorTest {

    private final ObjectMapper om = new ObjectMapper();

    private OpenAPIInterceptor interceptor;


    @BeforeEach
    void setUpSpec() {
        OpenAPISpec spec = new OpenAPISpec();
        spec.location = getPathFromResource("openapi/specs/exceptions/error-in-spec.yml");
        spec.validateRequests = YES;
        spec.validateResponses = YES;

        Router router = getRouter();
        interceptor = new OpenAPIInterceptor(createProxy(router, spec));
        interceptor.init(router);
    }

    @Test
    void paramWithNoSchema() throws Exception {
        assertEquals(CONTINUE, interceptor.handleRequest(getExchange("/param-with-no-type?bar=7", null)));
    }

    @Test
    void unknownType() throws Exception {
        Exchange exc = new Request.Builder().get("/unknown-type").buildExchange();
        exc.setResponse(ok().body("{}").contentType(APPLICATION_JSON.toString()).build());
        exc.setOriginalRequestUri("/unknown-type");

        assertEquals(CONTINUE, interceptor.handleRequest(exc));
        assertEquals(RETURN, interceptor.handleResponse(exc));
        assertEquals(500,exc.getResponse().getStatusCode());
    }

    @Test
    void nowhere() throws Exception {
        var exc = get("/nowhere").buildExchange();
        exc.setResponse(ok().body("{}").contentType(APPLICATION_JSON.toString()).build());
        exc.setOriginalRequestUri("/nowhere");

        assertEquals(CONTINUE, interceptor.handleRequest(exc));
        assertEquals(RETURN, interceptor.handleResponse(exc));

        assertEquals(500,exc.getResponse().getStatusCode());
        JsonNode json = om.readTree(exc.getResponse().getBodyAsStream());
        assertEquals("https://membrane-api.io/problems/user/openapi",json.get("type").asText());
    }



}
