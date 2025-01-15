/* Copyright 2025 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */
package com.predic8.membrane.core.interceptor.flow;

import com.predic8.membrane.core.Router;
import com.predic8.membrane.core.exchangestore.ForgetfulExchangeStore;
import com.predic8.membrane.core.interceptor.flow.choice.Case;
import com.predic8.membrane.core.interceptor.flow.choice.ChoiceInterceptor;
import com.predic8.membrane.core.interceptor.flow.choice.Otherwise;
import com.predic8.membrane.core.interceptor.templating.StaticInterceptor;
import com.predic8.membrane.core.proxies.ServiceProxy;
import com.predic8.membrane.core.proxies.ServiceProxyKey;
import com.predic8.membrane.core.transport.http.HttpTransport;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.not;

public class ChoiceInterceptorTest {

    private static Router router;

    @BeforeAll
    public static void setup() throws Exception {
        router = new Router();
        router.setHotDeploy(false);
        router.setExchangeStore(new ForgetfulExchangeStore());
        router.setTransport(new HttpTransport());
        router.getRuleManager().addProxyAndOpenPortIfNew(new ServiceProxy(
                new ServiceProxyKey("localhost", "*", ".*", 2000), null, 0) {{
            setInterceptors(List.of(new RequestInterceptor() {{
                setInterceptors(List.of(new ChoiceInterceptor() {{
                    setCases(List.of(
                            new Case() {{
                                setInterceptors(List.of(new StaticInterceptor() {{setTextTemplate("CaseA");}}));
                                setTest("headers['X-Case'] == 'a'");
                            }},
                            new Case() {{
                                setInterceptors(List.of(new StaticInterceptor() {{setTextTemplate("CaseB");}}));
                                setTest("headers['X-Case'] == 'b'");
                            }}
                    ));
                    setOtherwise(new Otherwise() {{
                        setInterceptors(List.of(new StaticInterceptor() {{setTextTemplate("CaseDefault");}}));
                    }});
            }}));
            }}, new ReturnInterceptor()));
        }});
        router.start();
    }

    @Test
    void caseA() {
        // @formatter:off
        given()
            .header("X-Case", "a")
        .when()
            .get("http://localhost:2000")
        .then()
            .body(containsString("CaseA"))
            .body(not(containsString("CaseB")))
            .body(not(containsString("CaseDefault")));
        // @formatter:on
    }

    @Test
    void caseB() {
        // @formatter:off
        given()
            .header("X-Case", "b")
        .when()
            .get("http://localhost:2000")
        .then()
            .body(containsString("CaseB"))
            .body(not(containsString("CaseA")))
            .body(not(containsString("CaseDefault")));
        // @formatter:on
    }

    @Test
    void caseDefault() {
        // @formatter:off
        given().when()
            .get("http://localhost:2000")
        .then()
            .body(containsString("CaseDefault"))
            .body(not(containsString("CaseB")))
            .body(not(containsString("CaseB")));
        // @formatter:on
    }

    @AfterAll
    public static void shutdown() {
        router.shutdown();
    }
}
