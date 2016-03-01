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
        oasit.testGoodAuthRequest();
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
        return new Object[0];
    }

    private static Object[] testRedirectUriNotAbsolute() {
        return new Object[0];
    }

    private static Object[] testUnauthorizedClient() {
        return new Object[0];
    }

    private static Object[] testInvalidClient() {
        return new Object[0];
    }

    private static Object[] testNoSessionForCode() {
        return new Object[0];
    }

    private static Object[] testRedirectUriMissing() {
        return new Object[0];
    }

    private static Object[] testClientSecretMissing() {
        return new Object[0];
    }

    private static Object[] testClientIdMissing() {
        return new Object[0];
    }

    private static Object[] testCodeMissing() {
        return new Object[]{"testCodeMissing",removeValueFromRequestUriLazy(getExchange(),getCodeQuery()),400,getInvalidRequestJson(), getResponseBody(getExchange())};
    }

    private static Callable<String> getCodeQuery(){
        return new Callable<String>() {
            @Override
            public String call() throws Exception {
                return "code=" + oasit.afterCodeGenerationCode;
            }
        };
    }

}
