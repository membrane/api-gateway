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
public class PasswordGrantTest extends RequestParameterizedTest {

    @Before
    public void setUp() throws Exception{
        super.setUp();
        exc = OAuth2AuthorizationServerInterceptorNormalTest.getMockPasswordRequestExchange().call();
    }

    @Parameterized.Parameters(name = "{0}")
    public static Collection<Object[]> data() {
        return Arrays.asList(new Object[][] {
                testSuccessfulPasswordRequest(),
//                testUsernameMissing(),
//                testPasswordMissing(),
//                testClientIdMissing(),
//                testClientSecretMissing(),
//                testUnauthorizedClient(),
//                testUnauthorizedUser()
        });
    }

    private static Object[] testSuccessfulPasswordRequest() {
        return new Object[]{"testSuccessfulPasswordRequest", doNothing(),200, getBool(true),getBool(true)};
    }

    private static Callable<Object> doNothing(){
        return new Callable<Object>() {
            @Override
            public Object call() throws Exception {
                return this;
            }
        };
    }
}
