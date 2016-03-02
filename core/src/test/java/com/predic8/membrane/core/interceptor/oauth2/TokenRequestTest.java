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

import org.junit.Before;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.*;

import java.util.Arrays;
import java.util.Collection;
import java.util.concurrent.Callable;

@RunWith(Parameterized.class)
public class TokenRequestTest extends RequestParameterizedTest {

    @Before
    public void setUp() throws Exception{
        super.setUp();
        oasit.testGoodGrantedAuthCode();
        exc = oasit.getMockTokenRequest();
    }

    @Parameters(name = "{0}")
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
        return new Object[]{"testRedirectUriNotEquals", replaceValueFromRequestBody(getExchange(),"redirect_uri=http://localhost:2001/oauth2callback","redirect_uri=http://localhost:2001/oauth2callback2"),400,getInvalidRequestJson(), getResponseBody(getExchange())};
    }

    private static Object[] testRedirectUriNotAbsolute() {
        return new Object[]{"testRedirectUriNotAbsolute", replaceValueFromRequestBody(getExchange(),"redirect_uri=http://localhost:2001/oauth2callback","redirect_uri=localhost:2001/oauth2callback"),400,getInvalidRequestJson(), getResponseBody(getExchange())};
    }

    private static Object[] testUnauthorizedClient() {
        return new Object[]{"testUnauthorizedClient", replaceValueFromRequestBody(getExchange(),"&client_secret=def", "&client_secret=123456789"),400,getUnauthorizedClientJson(), getResponseBody(getExchange())};
    }

    private static Object[] testInvalidClient() {
        return new Object[]{"testInvalidClient", replaceValueFromRequestBody(getExchange(),"&client_id=abc", "&client_id=123456789"),400,getInvalidClientJson(), getResponseBody(getExchange())};
    }

    private static Object[] testNoSessionForCode() {
        return new Object[]{"testNoSessionForCode", replaceValueFromRequestBodyLazy(getExchange(),getCodeQuery(), getWrongCodeQuery()),400,getInvalidRequestJson(), getResponseBody(getExchange())};
    }

    private static Object[] testRedirectUriMissing() {
        return new Object[]{"testRedirectUriMissing", removeValueFromRequestBody(getExchange(),"&redirect_uri=http://localhost:2001/oauth2callback"),400,getInvalidRequestJson(), getResponseBody(getExchange())};
    }

    private static Object[] testClientSecretMissing() {
        return new Object[]{"testClientSecretMissing", removeValueFromRequestBody(getExchange(),"&client_secret=def"),400,getInvalidRequestJson(), getResponseBody(getExchange())};
    }

    private static Object[] testClientIdMissing() {
        return new Object[]{"testClientIdMissing", removeValueFromRequestBody(getExchange(),"&client_id=abc"),400,getInvalidRequestJson(), getResponseBody(getExchange())};
    }

    private static Object[] testCodeMissing() {
        return new Object[]{"testCodeMissing", removeValueFromRequestBodyLazy(getExchange(),getCodeQuery()),400,getInvalidRequestJson(), getResponseBody(getExchange())};
    }

    private static Callable<String> getCodeQuery(){
        return new Callable<String>() {
            @Override
            public String call() throws Exception {
                return "code=" + oasit.afterCodeGenerationCode;
            }
        };
    }

    private static Callable<String> getWrongCodeQuery(){
        return new Callable<String>() {
            @Override
            public String call() throws Exception {
                return "code=" + 123456789;
            }
        };
    }

}
