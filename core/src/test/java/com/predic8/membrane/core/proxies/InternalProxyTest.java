/* Copyright 2021 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */
package com.predic8.membrane.core.proxies;

import com.predic8.membrane.core.*;
import com.predic8.membrane.core.openapi.serviceproxy.*;
import org.hamcrest.*;
import org.junit.jupiter.api.*;

import static com.predic8.membrane.core.interceptor.flow.invocation.FlowTestInterceptors.*;
import static io.restassured.RestAssured.*;

class InternalProxyTest {

    private static Router router;

    @BeforeAll
    public static void setup() throws Exception {
        router = new HttpRouter();

        router.add(new APIProxy() {{
            key = new APIProxyKey(2001);
            interceptors.add(E);
            interceptors.add(ECHO);
        }});

        router.add(new APIProxy() {{
            key = new APIProxyKey(2000);
            key.setPath("/to-external");
            interceptors.add(C);
            target = new Target() {{
                setUrl("internal://to-external");
            }};
        }});

        router.add(new InternalProxy() {{
            name = "to-external";
            interceptors.add(D);
            target = new Target("localhost",2001);
        }});

        router.add(new APIProxy() {{
            key = new APIProxyKey(2000);
            key.setUsePathPattern(false);
            target = new Target() {{
                setUrl("internal://a");
            }};
        }});

        router.add(new InternalProxy() {{
            name = "a";
            interceptors.add(A);
            target = new Target() {{
                setUrl("internal://b");
            }};
        }});

        router.add(new InternalProxy() {{
            name = "b";
            interceptors.add(B);
            interceptors.add(RETURN);
        }});

        router.init();

    }

    @AfterAll
    public static void teardown() {
        router.shutdown();
    }

    @Test
    void internalCallsInternal() {
        // @formatter:off
        when()
            .get("http://localhost:2000/foo")
        .then()
            .body(Matchers.equalTo(">a>b<b<a"));
        // @formatter:on
    }

    @Test
    void internalCallsExternal() {
        // @formatter:off
        given()
            .body("client")
        .when()
            .post("http://localhost:2000/to-external")
        .then()
            .body(Matchers.equalTo("client>c>d>e<e<d<c"));
        // @formatter:off
    }
}