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

import com.predic8.membrane.core.*;
import com.predic8.membrane.core.exchange.*;
import com.predic8.membrane.core.interceptor.*;
import com.predic8.membrane.core.openapi.model.*;
import com.predic8.membrane.core.openapi.serviceproxy.*;
import com.predic8.membrane.core.util.*;
import org.junit.jupiter.api.*;

import java.util.*;

import static com.predic8.membrane.core.openapi.serviceproxy.OpenAPISpec.YesNoOpenAPIOption.*;
import static com.predic8.membrane.core.openapi.util.TestUtils.*;
import static org.junit.jupiter.api.Assertions.*;


public class SecurityTest extends AbstractValidatorTest {

    @Override
    String getOpenAPIFileName() {
        return "/openapi/specs/security.yml";
    }

    @Test
    void simple() {
        ValidationErrors errors = validator.validate(Request.get().path("/v1/finance").scopes("finance","read"));
        System.out.println("errors = " + errors);
        assertEquals(0,errors.size());
    }

    @Test
    void two() {
        ValidationErrors errors = validator.validate(Request.get().path("/v1/finance-and-write"));
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

        Exchange exc = com.predic8.membrane.core.http.Request.get("/v1/finance").buildExchange();
        exc.setOriginalRequestUri("/v1/finance");

        Map<String,Object> jwt = new HashMap<>();
        jwt.put("scp","read write finance");

        exc.setProperty("jwt",jwt);

        Outcome outcome = interceptor.handleRequest(exc);
        assertEquals(Outcome.RETURN, outcome);
        System.out.println("exc = " + exc.getResponse().getStatusCode());
        System.out.println("exc = " + exc.getResponse().getBodyAsStringDecoded());
    }

}