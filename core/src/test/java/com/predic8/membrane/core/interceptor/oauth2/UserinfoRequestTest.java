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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Named;

import java.util.Arrays;
import java.util.Collection;
import java.util.concurrent.Callable;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.fail;

public class UserinfoRequestTest extends RequestParameterizedTest {
    @BeforeEach
    public void setUp() throws Exception{
        super.setUp();
        oasit.runUntilGoodTokenRequest().run();
        exc = OAuth2AuthorizationServerInterceptorNormalTest.getMockUserinfoRequest().call();
    }

    public static Stream<Named<RequestTestData>> data() {
        return Stream.of(testAuthorizationHeaderMissing(),
                testEmptyAuthorizationHeader(),
                testWrongTokenInAuthorizationHeader()
        ).map(data -> Named.of(data.testName(), data));
    }

    private static RequestTestData testWrongTokenInAuthorizationHeader() {
        return new RequestTestData("testWrongTokenInAuthorizationHeader", changeTokenInAuthorizationHeaderInRequestHeader(getExchange(),"123456789"),401,getBool(true), responseWwwAuthenticateHeaderContainsValue(getExchange(),"error=\"invalid_token\""));
    }

    private static RequestTestData testEmptyAuthorizationHeader() {
        return new RequestTestData("testEmptyAuthorizationHeader", changeValueOfAuthorizationHeaderInRequestHeader(getExchange(),""),401,getBool(true), responseWwwAuthenticateHeaderContainsValue(getExchange(),"error=\"invalid_token\""));
    }

    private static RequestTestData testAuthorizationHeaderMissing() {
        return new RequestTestData("testAuthorizationHeaderMissing", removeAuthorizationHeaderInRequestHeader(getExchange()),400,getBool(true), responseWwwAuthenticateHeaderContainsValue(getExchange(),"error=\"invalid_request\""));
    }

    public static Callable<Object> removeAuthorizationHeaderInRequestHeader(final Callable<Exchange> exc){
        return new Callable<>() {
            @Override
            public Object call() throws Exception {
                exc.call().getRequest().getHeader().removeFields("Authorization");
                return this;
            }
        };
    }

    public static Callable<Object> changeTokenInAuthorizationHeaderInRequestHeader(final Callable<Exchange> exc, final String newTokenValue) {
        return () -> changeValueOfAuthorizationHeaderInRequestHeader(exc, new TokenAuthorizationHeader(exc.call().getRequest()).getTokenType() + " " + newTokenValue).call();
    }

    public static Callable<Object> changeValueOfAuthorizationHeaderInRequestHeader(final Callable<Exchange> exc, final String newValue){
        return new Callable<>() {
            @Override
            public Object call() throws Exception {
                exc.call().getRequest().getHeader().removeFields("Authorization");
                exc.call().getRequest().getHeader().setValue("Authorization", newValue);
                return this;
            }
        };
    }

    public static Supplier<Object> responseWwwAuthenticateHeaderContainsValue(final Callable<Exchange> exc, final String value){
        return () -> {
            Exchange call = null;
            try {
                call = exc.call();
            } catch (Exception e) {
                fail();
            }
            return call.getResponse().getHeader().getWwwAuthenticate().contains(value);
        };
    }

}
