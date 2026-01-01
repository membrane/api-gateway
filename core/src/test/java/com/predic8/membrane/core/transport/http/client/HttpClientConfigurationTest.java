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

package com.predic8.membrane.core.transport.http.client;

import com.predic8.membrane.core.interceptor.*;
import com.predic8.membrane.core.proxies.*;
import com.predic8.membrane.core.router.*;
import org.jetbrains.annotations.*;
import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Base for refactorings of the HttpClientConfiguration and Router.
 * <p>
 * <p>
 * Also tests Router.initFromXMLString
 */
class HttpClientConfigurationTest {

    DefaultRouter router;

    HttpClientConfiguration configuration;

    String empty = """
            <spring:beans xmlns="http://membrane-soa.org/proxies/1/"
            xmlns:spring="http://www.springframework.org/schema/beans"
            xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
            xsi:schemaLocation="http://www.springframework.org/schema/beans http://www.springframework.org/schema/beans/spring-beans-4.2.xsd
                                http://membrane-soa.org/proxies/1/ http://membrane-soa.org/schemas/proxies-1.xsd">
                <router/>
            </spring:beans>
            """;

    String globalHcc = """
            <spring:beans xmlns="http://membrane-soa.org/proxies/1/"
            	xmlns:spring="http://www.springframework.org/schema/beans"
            	xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
            	xsi:schemaLocation="http://www.springframework.org/schema/beans http://www.springframework.org/schema/beans/spring-beans-4.2.xsd
            					    http://membrane-soa.org/proxies/1/ http://membrane-soa.org/schemas/proxies-1.xsd">
            
            	<router>
            
                    <httpClientConfig useExperimentalHttp2="true">
                        <proxy id="myhost">
            
                        </proxy>
                    </httpClientConfig>
            
                    <api port="2000" name="API1">
                        <httpClient/>
                    </api>
            
                </router>
            
            </spring:beans>
            """;

    String hccOutsideOfRouter = """
            <spring:beans xmlns="http://membrane-soa.org/proxies/1/"
                          xmlns:spring="http://www.springframework.org/schema/beans"
                          xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                          xsi:schemaLocation="http://www.springframework.org/schema/beans http://www.springframework.org/schema/beans/spring-beans-4.2.xsd
            					    http://membrane-soa.org/proxies/1/ http://membrane-soa.org/schemas/proxies-1.xsd">
            
                <httpClientConfig useExperimentalHttp2="true">
                    <proxy id="myhost"/>
                </httpClientConfig>
            
                <httpClient>
                    <httpClientConfig/>
                </httpClient>
            
                <router>
            
                    <api port="2000" name="API1">
                        <httpClient/>
                    </api>
            
                </router>
            
            </spring:beans>
            """;

    @BeforeEach
    void setUp() {
        configuration = new HttpClientConfiguration();
    }

    @AfterEach
    void tearDown() {
        if (router == null) return;
        router.stop();
    }

    @Test
    void maxRetries() {
        assertEquals(2, configuration.getRetryHandler().getRetries());

        RetryHandler rh = new RetryHandler();
        configuration.setRetryHandler(rh);
        assertEquals(2, configuration.getRetryHandler().getRetries());

        rh.setRetries(10);
        assertEquals(10, configuration.getRetryHandler().getRetries());
    }

    @Test
    void startWithSimpleConfig() {
        setupRouter(empty);
    }

    @Test
    void inGlobal() {
        setupRouter(globalHcc);
        Proxy api = getApi1();
        Interceptor i = api.getFlow().getFirst();
        if (i instanceof HTTPClientInterceptor hci) {
            var hcc = hci.getHttpClientConfig();
            assertNotNull(hcc);
        }
    }

    @Test
    void outsideRouter() {
        setupRouter(hccOutsideOfRouter);
        Proxy api = getApi1();
        Interceptor i = api.getFlow().getFirst();
        if (i instanceof HTTPClientInterceptor hci) {
            var hcc = hci.getHttpClientConfig();
            assertNotNull(hcc);
        }
    }

    private @NotNull Proxy getApi1() {
        Proxy api1 = router.getRuleManager().getRuleByName("API1", Proxy.class);
        assertNotNull(api1);
        return api1;
    }

    private void setupRouter(String globalHcc) {
        router = RouterXmlBootstrap.initFromXMLString(globalHcc);
        assertNotNull(router.getHttpClientConfig());
        assertNotNull(router.getResolverMap().getHTTPSchemaResolver().getHttpClientConfig());
    }
}