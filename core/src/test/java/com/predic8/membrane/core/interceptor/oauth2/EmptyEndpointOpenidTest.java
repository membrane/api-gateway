/*
 * Copyright 2016 predic8 GmbH, www.predic8.com
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *    http://www.apache.org/licenses/LICENSE-2.0
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */

package com.predic8.membrane.core.interceptor.oauth2;

import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.interceptor.authentication.session.SessionManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Named;

import java.util.Arrays;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.stream.Stream;

public class EmptyEndpointOpenidTest extends RequestParameterizedTest{
    @BeforeEach
    public void setUp() throws Exception{
        super.setUp();
        oasit.runUntilGoodAuthOpenidRequest().run();
        exc = OAuth2AuthorizationServerInterceptorNormalTest.getMockEmptyEndpointRequest().call();
    }

    public static Stream<Named<RequestTestData>> data() {
        return Stream.of(testConsentNotGiven(),
                testIdTokenTokenResponse()
        ).map(data -> Named.of(data.testName(), data));
    }

    private static RequestTestData testIdTokenTokenResponse() {
        return new RequestTestData ("testIdTokenTokenResponse", modifySessionToIdTokenTokenResponseType(),307,getBool(true),responseContainsValueInLocationHeader("id_token="));
    }

    private static RequestTestData testConsentNotGiven() {
        return new RequestTestData("testConsentNotGiven", setConsentToFalse(getExchange()),400,getConsentRequiredJson(),getResponseBody());
    }

    private static Callable<Object> setConsentToFalse(Callable<Exchange> exchange) {
        return new Callable<>() {
            @Override
            public Object call() throws Exception {
                SessionManager.Session s = oasit.oasi.getSessionManager().getSession(exc);
                Map<String, String> userAttributes = s.getUserAttributes();
                synchronized (userAttributes) {
                    userAttributes.put("consent", "false");
                }
                return this;
            }
        };
    }

    private static Callable<Object> modifySessionToIdTokenTokenResponseType() {
        return new Callable<>() {
            @Override
            public Object call() throws Exception {
                modifySessionAttributes("response_type", "id_token token");
                return this;
            }
        };
    }
}
