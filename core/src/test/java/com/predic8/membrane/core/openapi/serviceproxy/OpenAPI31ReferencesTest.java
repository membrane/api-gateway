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
import com.predic8.membrane.core.util.*;
import io.restassured.*;
import io.restassured.response.*;
import org.junit.jupiter.api.*;

import java.util.*;

public class OpenAPI31ReferencesTest {

    @BeforeEach
    public void setUp() throws Exception {
        Router router = new HttpRouter();
        router.setUriFactory(new URIFactory());

        OpenAPISpec spec = new OpenAPISpec();
        spec.location = "src/test/resources/openapi/specs/oas31/request-reference.yaml";
        spec.setValidateRequests(OpenAPISpec.YesNoOpenAPIOption.YES);

        APIProxy api = new APIProxy();
        api.setPort(2000);
        api.setSpecs(List.of(spec));
     //   api.getInterceptors().add(new ReturnInterceptor());
        router.getRuleManager().addProxyAndOpenPortIfNew(api);
        router.init();

    }

    @Test
    void wrongPath() throws Exception {
        ValidatableResponse res = RestAssured.given().body("").post("http://localhost:2000/wrong").then();

        // Assertion

        System.out.println("res = " +  res.extract().asPrettyString());
    }

    @Test
    void foo() throws Exception {
        ValidatableResponse res = RestAssured.given().body("").post("http://localhost:2000/users").then();

        // Assertion

        System.out.println("res = " +  res.extract().asPrettyString());
    }
}
