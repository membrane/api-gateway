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
import org.junit.jupiter.api.Named;

import java.util.Arrays;
import java.util.Collection;
import java.util.concurrent.Callable;
import java.util.function.Supplier;
import java.util.stream.Stream;

public class RevocationRequestTest extends RequestParameterizedTest{
    @BeforeEach
    public void setUp() throws Exception{
        super.setUp();
        oasit.runUntilGoodTokenRequest().run();
        exc = OAuth2AuthorizationServerInterceptorNormalTest.getMockRevocationRequest().call();
    }

    public static Stream<Named<RequestTestData>> data() {
        return Stream.of(testTokenMissing(),
                testClientIdMissing(),
                testClientSecretMissing(),
                testWrongToken(),
                testWrongCredentialsClientId(),
                testWrongCredentialsClientSecret()
        ).map(data -> Named.of(data.testName(), data));
    }

    private static RequestTestData testWrongCredentialsClientSecret() {
        return new RequestTestData("testWrongCredentialsClientId", replaceValueFromRequestBody("&client_secret=def","&client_secret=123456789"),400,getInvalidGrantJson(), getResponseBody());
    }

    private static RequestTestData testWrongCredentialsClientId() {
        return new RequestTestData("testWrongCredentialsClientId", replaceValueFromRequestBody("client_id=abc","client_id=123456789"),400,getInvalidGrantJson(), getResponseBody());
    }

    private static RequestTestData testWrongToken() {
        return new RequestTestData("testWrongToken", replaceValueFromRequestBodyLazy(getTokenQuery(),getWrongTokenQuery()),200,getEmptyBody(), getResponseBody());
    }

    private static RequestTestData testClientSecretMissing() {
        return new RequestTestData("testClientSecretMissing", removeValueFromRequestBody("&client_secret=def"),200,getEmptyBody(), getResponseBody());
    }

    private static RequestTestData testClientIdMissing() {
        return new RequestTestData("testClientIdMissing", removeValueFromRequestBody("&client_id=abc"),200,getEmptyBody(), getResponseBody());
    }

    private static RequestTestData testTokenMissing() {
        return new RequestTestData("testTokenMissing", removeValueFromRequestBodyLazy(getTokenQuery()),400,getInvalidRequestJson(), getResponseBody());
    }

    private static Callable<String> getTokenQuery(){
        return () -> "token=" + oasit.afterTokenGenerationToken + "&";
    }

    private static Callable<String> getWrongTokenQuery(){
        return () -> "token=" + 123456789 + "&";
    }

    private static Supplier<Object> getEmptyBody() {
        return () -> "";
    }


}
