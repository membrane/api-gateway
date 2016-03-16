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
import org.junit.Before;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

import java.util.Arrays;
import java.util.Collection;
import java.util.concurrent.Callable;

@RunWith(Parameterized.class)
public class UserinfoRequestTest extends RequestParameterizedTest {
    @Before
    public void setUp() throws Exception{
        super.setUp();
        oasit.runUntilGoodTokenRequest().run();
        exc = OAuth2AuthorizationServerInterceptorNormalTest.getMockUserinfoRequest().call();
    }

    @Parameters(name = "{0}")
    public static Collection<Object[]> data() {
        return Arrays.asList(new Object[][] {
            testAuthorizationHeaderMissing(),
            testEmptyAuthorizationHeader(),
            testWrongTokenInAuthorizationHeader()
        });
    }

    private static Object[] testWrongTokenInAuthorizationHeader() {
        return new Object[]{"testWrongTokenInAuthorizationHeader", changeTokenInAuthorizationHeaderInRequestHeader(getExchange(),"123456789"),401,getBool(true), responseWwwAuthenticateHeaderContainsValue(getExchange(),"error=\"invalid_token\"")};
    }

    private static Object[] testEmptyAuthorizationHeader() {
        return new Object[]{"testEmptyAuthorizationHeader", changeValueOfAuthorizationHeaderInRequestHeader(getExchange(),""),401,getBool(true), responseWwwAuthenticateHeaderContainsValue(getExchange(),"error=\"invalid_token\"")};
    }

    private static Object[] testAuthorizationHeaderMissing() {
        return new Object[]{"testAuthorizationHeaderMissing", removeAuthorizationHeaderInRequestHeader(getExchange()),400,getBool(true), responseWwwAuthenticateHeaderContainsValue(getExchange(),"error=\"invalid_request\"")};
    }

    public static Callable<Object> removeAuthorizationHeaderInRequestHeader(final Callable<Exchange> exc){
        return new Callable<Object>() {
            @Override
            public Object call() throws Exception {
                exc.call().getRequest().getHeader().removeFields("Authorization");
                return this;
            }
        };
    }

    public static Callable<Object> changeTokenInAuthorizationHeaderInRequestHeader(final Callable<Exchange> exc, final String newTokenValue) {
        return new Callable<Object>() {
            @Override
            public Object call() throws Exception {
                return changeValueOfAuthorizationHeaderInRequestHeader(exc, new TokenAuthorizationHeader(exc.call().getRequest()).getTokenType() + " " + newTokenValue).call();
            }
        };
    }

    public static Callable<Object> changeValueOfAuthorizationHeaderInRequestHeader(final Callable<Exchange> exc, final String newValue){
        return new Callable<Object>() {
            @Override
            public Object call() throws Exception {
                exc.call().getRequest().getHeader().removeFields("Authorization");
                exc.call().getRequest().getHeader().setValue("Authorization", newValue);
                return this;
            }
        };
    }

    public static Callable<Boolean> responseWwwAuthenticateHeaderContainsValue(final Callable<Exchange> exc, final String value){
        return new Callable<Boolean>() {
            @Override
            public Boolean call() throws Exception {
                return exc.call().getResponse().getHeader().getWwwAuthenticate().contains(value);
            }
        };
    }

}
