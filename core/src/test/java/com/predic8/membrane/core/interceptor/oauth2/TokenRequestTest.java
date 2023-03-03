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

import org.junit.jupiter.api.BeforeEach;

import java.util.Arrays;
import java.util.Collection;
import java.util.concurrent.Callable;

public class TokenRequestTest extends RequestParameterizedTest {

    @BeforeEach
    public void setUp() throws Exception{
        super.setUp();
        oasit.runUntilGoodGrantedAuthCode().run();
        exc = OAuth2AuthorizationServerInterceptorNormalTest.getMockTokenRequest().call();
    }

    public static Collection<Object[]> data() {
        return Arrays.asList(new Object[][] {
            testCodeMissing(),
            testClientIdMissing(),
            testClientSecretMissing(),
            testRedirectUriMissing(),
            testNoSessionForCode(),
            testInvalidClient(),
            testUnauthorizedClient(),
            testRedirectUriNotAbsolute(),
            testRedirectUriNotEquals()
        });
    }

    private static Object[] testRedirectUriNotEquals() {
        return new Object[]{"testRedirectUriNotEquals", replaceValueFromRequestBody("redirect_uri=http://localhost:2001/oauth2callback","redirect_uri=http://localhost:2001/oauth2callback2"),400,getInvalidRequestJson(), getResponseBody()};
    }

    private static Object[] testRedirectUriNotAbsolute() {
        return new Object[]{"testRedirectUriNotAbsolute", replaceValueFromRequestBody("redirect_uri=http://localhost:2001/oauth2callback","redirect_uri=localhost:2001/oauth2callback"),400,getInvalidRequestJson(), getResponseBody()};
    }

    private static Object[] testUnauthorizedClient() {
        return new Object[]{"testUnauthorizedClient", replaceValueFromRequestBody("&client_secret=def", "&client_secret=123456789"),400,getUnauthorizedClientJson(), getResponseBody()};
    }

    private static Object[] testInvalidClient() {
        return new Object[]{"testInvalidClient", replaceValueFromRequestBody("&client_id=abc", "&client_id=123456789"),400,getInvalidClientJson(), getResponseBody()};
    }

    private static Object[] testNoSessionForCode() {
        return new Object[]{"testNoSessionForCode", replaceValueFromRequestBodyLazy(getCodeQuery(), getWrongCodeQuery()),400,getInvalidRequestJson(), getResponseBody()};
    }

    private static Object[] testRedirectUriMissing() {
        return new Object[]{"testRedirectUriMissing", removeValueFromRequestBody("&redirect_uri=http://localhost:2001/oauth2callback"),400,getInvalidRequestJson(), getResponseBody()};
    }

    private static Object[] testClientSecretMissing() {
        return new Object[]{"testClientSecretMissing", removeValueFromRequestBody("&client_secret=def"),400,getInvalidRequestJson(), getResponseBody()};
    }

    private static Object[] testClientIdMissing() {
        return new Object[]{"testClientIdMissing", removeValueFromRequestBody("&client_id=abc"),400,getInvalidRequestJson(), getResponseBody()};
    }

    private static Object[] testCodeMissing() {
        return new Object[]{"testCodeMissing", removeValueFromRequestBodyLazy(getCodeQuery()),400,getInvalidRequestJson(), getResponseBody()};
    }

    private static Callable<String> getCodeQuery(){
        return new Callable<>() {
            @Override
            public String call() throws Exception {
                return "code=" + oasit.afterCodeGenerationCode;
            }
        };
    }

    private static Callable<String> getWrongCodeQuery(){
        return new Callable<>() {
            @Override
            public String call() throws Exception {
                return "code=" + 123456789;
            }
        };
    }

}
