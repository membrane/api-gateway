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

package com.predic8.membrane.core.openapi.validators;

import com.predic8.membrane.core.Router;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.interceptor.Outcome;
import com.predic8.membrane.core.openapi.model.Request;
import com.predic8.membrane.core.openapi.serviceproxy.OpenAPIInterceptor;
import com.predic8.membrane.core.openapi.serviceproxy.OpenAPISpec;
import com.predic8.membrane.core.util.URIFactory;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static com.predic8.membrane.core.http.Request.get;
import static com.predic8.membrane.core.openapi.serviceproxy.OpenAPISpec.YesNoOpenAPIOption.YES;
import static com.predic8.membrane.core.openapi.util.TestUtils.createProxy;
import static org.junit.jupiter.api.Assertions.assertEquals;


public class SecurityTest extends AbstractValidatorTest {

    @Override
    String getOpenAPIFileName() {
        return "/openapi/specs/security.yml";
    }

    @Test
    void simpleHasScopes() {
        ValidationErrors errors = validator.validate(Request.get().path("/v1/finance").scopes("finance","read"));
        System.out.println("errors = " + errors);
        assertEquals(0,errors.size());
    }

    @Test
    void simpleMissingAllScopes() {
        ValidationErrors errors = validator.validate(Request.get().path("/v1/finance-and-write"));
        System.out.println("errors = " + errors);
        assertEquals(3,errors.size());
    }

    @Test
    void simpleMissingTwoScopes() {
        ValidationErrors errors = validator.validate(Request.get().path("/v1/finance-and-write").scopes("read"));
        System.out.println("errors = " + errors);
        assertEquals(2,errors.size());
    }

    @Test
    void simpleIgnoreAdditionalScopes() {
        ValidationErrors errors = validator.validate(Request.get().path("/v1/finance").scopes("read", "finance", "development"));
        System.out.println("errors = " + errors);
        assertEquals(0,errors.size());
    }

    @Test
    void fromInterceptor() throws Exception {
        Router router = new Router();
        router.setUriFactory(new URIFactory());

        OpenAPISpec spec = new OpenAPISpec();
        spec.location = "src/test/resources/openapi/specs/security.yml";
        spec.validateRequests = YES;

        OpenAPIInterceptor interceptor = new OpenAPIInterceptor(createProxy(router, spec));
        interceptor.init(router);

        Exchange exc = get("/v1/finance").buildExchange();
        exc.setOriginalRequestUri("/v1/finance");

        Map<String,Object> jwt = new HashMap<>();
        jwt.put("scp","read write finance");

        exc.setProperty("jwt",jwt);

        Outcome outcome = interceptor.handleRequest(exc);
        assertEquals(Outcome.RETURN, outcome);
        System.out.println("exc = " + exc.getResponse().getStatusCode());
        // TODO ASSERT validationErrors empty
        System.out.println("exc = " + exc.getResponse().getBodyAsStringDecoded());
    }

    @Test
    void fromInterceptorEmptyScopes() throws Exception {
        Router router = new Router();
        router.setUriFactory(new URIFactory());

        OpenAPISpec spec = new OpenAPISpec();
        spec.location = "src/test/resources/openapi/specs/security.yml";
        spec.validateRequests = YES;

        OpenAPIInterceptor interceptor = new OpenAPIInterceptor(createProxy(router, spec));
        interceptor.init(router);

        Exchange exc = get("/v1/finance").buildExchange();
        exc.setOriginalRequestUri("/v1/finance");

        Map<String,Object> jwt = new HashMap<>();
        exc.setProperty("jwt",jwt);

        Outcome outcome = interceptor.handleRequest(exc);
        assertEquals(Outcome.RETURN, outcome);
        System.out.println("exc = " + exc.getResponse().getStatusCode());
        System.out.println("exc = " + exc.getResponse().getBodyAsStringDecoded());
    }
}