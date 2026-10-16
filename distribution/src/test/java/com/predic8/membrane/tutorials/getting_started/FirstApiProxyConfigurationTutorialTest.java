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
package com.predic8.membrane.tutorials.getting_started;

import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.interceptor.AbstractInterceptor;
import com.predic8.membrane.core.interceptor.Outcome;
import com.predic8.membrane.core.proxies.ProxyRule;
import com.predic8.membrane.core.proxies.ProxyRuleKey;
import com.predic8.membrane.core.router.DefaultRouter;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static com.predic8.membrane.core.interceptor.Outcome.CONTINUE;
import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A Membrane forward proxy stands in for the reader's proxy on the address the tutorial configures,
 * so the test can check that the call to the public API really goes through it.
 */
public class FirstApiProxyConfigurationTutorialTest extends AbstractGettingStartedTutorialTest {

    private static final int PROXY_PORT = 3128;

    private final List<String> proxiedRequests = new CopyOnWriteArrayList<>();

    private DefaultRouter proxy;

    @Override
    protected String getTutorialYaml() {
        return "11-First-API-Proxy-Configuration.yaml";
    }

    @Override
    protected void startMembrane() throws Exception {
        startProxy();
        super.startMembrane();
    }

    @Override
    protected void stopMembrane() {
        super.stopMembrane();
        if (proxy != null)
            proxy.stop();
    }

    @Test
    void requestGoesThroughProxy() {
        // @formatter:off
        given()
        .when()
            .get("http://localhost:2000")
        .then()
            .statusCode(200)
            .body(containsString("Shop API Showcase"));
        // @formatter:on

        assertEquals(List.of("CONNECT api.predic8.de:443"), proxiedRequests);
    }

    private void startProxy() throws Exception {
        final var rule = new ProxyRule(new ProxyRuleKey(PROXY_PORT));
        rule.getFlow().add(new AbstractInterceptor() {
            @Override
            public Outcome handleRequest(Exchange exc) {
                proxiedRequests.add("%s %s".formatted(exc.getRequest().getMethod(), exc.getRequest().getUri()));
                return CONTINUE;
            }
        });

        proxy = new DefaultRouter();
        proxy.add(rule);
        proxy.start();
    }
}
