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

public class RevocationRequestTest extends RequestParameterizedTest{
    @BeforeEach
    public void setUp() throws Exception{
        super.setUp();
        oasit.runUntilGoodTokenRequest().run();
        exc = OAuth2AuthorizationServerInterceptorNormalTest.getMockRevocationRequest().call();
    }

    public static Collection<Object[]> data() {
        return Arrays.asList(new Object[][] {
                testTokenMissing(),
                testClientIdMissing(),
                testClientSecretMissing(),
                testWrongToken(),
                testWrongCredentialsClientId(),
                testWrongCredentialsClientSecret()
        });
    }

    private static Object[] testWrongCredentialsClientSecret() {
        return new Object[]{"testWrongCredentialsClientId", replaceValueFromRequestBody("&client_secret=def","&client_secret=123456789"),400,getInvalidGrantJson(), getResponseBody()};
    }

    private static Object[] testWrongCredentialsClientId() {
        return new Object[]{"testWrongCredentialsClientId", replaceValueFromRequestBody("client_id=abc","client_id=123456789"),400,getInvalidGrantJson(), getResponseBody()};
    }

    private static Object[] testWrongToken() {
        return new Object[]{"testWrongToken", replaceValueFromRequestBodyLazy(getTokenQuery(),getWrongTokenQuery()),200,getEmptyBody(), getResponseBody()};
    }

    private static Object[] testClientSecretMissing() {
        return new Object[]{"testClientSecretMissing", removeValueFromRequestBody("&client_secret=def"),200,getEmptyBody(), getResponseBody()};
    }

    private static Object[] testClientIdMissing() {
        return new Object[]{"testClientIdMissing", removeValueFromRequestBody("&client_id=abc"),200,getEmptyBody(), getResponseBody()};
    }

    private static Object[] testTokenMissing() {
        return new Object[]{"testTokenMissing", removeValueFromRequestBodyLazy(getTokenQuery()),400,getInvalidRequestJson(), getResponseBody()};
    }

    private static Callable<String> getTokenQuery(){
        return new Callable<String>() {
            @Override
            public String call() throws Exception {
                return "token=" + oasit.afterTokenGenerationToken + "&";
            }
        };
    }

    private static Callable<String> getWrongTokenQuery(){
        return new Callable<String>() {
            @Override
            public String call() throws Exception {
                return "token=" + 123456789 + "&";
            }
        };
    }

    private static Callable<Object> getEmptyBody() {
        return new Callable<Object>() {
            @Override
            public Object call() throws Exception {
                return "";
            }
        };
    }


}
