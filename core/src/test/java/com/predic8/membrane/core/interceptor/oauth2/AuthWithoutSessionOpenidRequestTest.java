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

import java.util.Arrays;
import java.util.Collection;
import java.util.concurrent.Callable;

@RunWith(Parameterized.class)
public class AuthWithoutSessionOpenidRequestTest extends RequestParameterizedTest{
    @Before
    public void setUp() throws Exception{
        super.setUp();
        exc = oasit.getMockAuthOpenidRequestExchange();
    }

    @Parameterized.Parameters(name = "{0}")
    public static Collection<Object[]> data() {
        return Arrays.asList(new Object[][] {
                testOpenidScopeButIsTokenResponseType(),
                testHasClaimsButIsNotOpenid(),
        });
    }

    private static Object[] testHasClaimsButIsNotOpenid() {
        return new Object[]{"testHasClaimsButIsNotOpenid", replaceValueFromRequestUri("scope=openid","scope=profile"),307, getBool(true), sessionHasNoClaimsParam()};
    }

    private static Object[] testOpenidScopeButIsTokenResponseType() {
        return new Object[]{"testOpenidScopeButIsTokenResponseType",replaceValueFromRequestUri("response_type=code","response_type=token"),307,getBool(true),responseContainsValueInLocationHeader("error=invalid_request")};
    }

    private static Callable<Object> sessionHasNoClaimsParam() {
        return new Callable<Object>() {
            @Override
            public Object call() throws Exception {
                return !oasit.oasi.getSessionManager().getSession("123").getUserAttributes().containsKey(ParamNames.CLAIMS);
            }
        };
    }
}
