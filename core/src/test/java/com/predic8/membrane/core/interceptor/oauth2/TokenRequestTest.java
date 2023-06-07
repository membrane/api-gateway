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
import java.util.stream.Stream;

public class TokenRequestTest extends RequestParameterizedTest {

    @BeforeEach
    public void setUp() throws Exception{
        super.setUp();
        oasit.runUntilGoodGrantedAuthCode().run();
        exc = OAuth2AuthorizationServerInterceptorNormalTest.getMockTokenRequest().call();
    }

    public static Stream<Named<RequestTestData>> data() {
        return Stream.of(testCodeMissing(),
                testClientIdMissing(),
                testClientSecretMissing(),
                testRedirectUriMissing(),
                testNoSessionForCode(),
                testInvalidClient(),
                testUnauthorizedClient(),
                testRedirectUriNotAbsolute(),
                testRedirectUriNotEquals()
        ).map(data -> Named.of(data.testName(), data));
    }

    private static RequestTestData testRedirectUriNotEquals() {
        return new RequestTestData("testRedirectUriNotEquals", replaceValueFromRequestBody("redirect_uri=http://localhost:2001/oauth2callback","redirect_uri=http://localhost:2001/oauth2callback2"),400,getInvalidRequestJson(), getResponseBody());
    }

    private static RequestTestData testRedirectUriNotAbsolute() {
        return new RequestTestData("testRedirectUriNotAbsolute", replaceValueFromRequestBody("redirect_uri=http://localhost:2001/oauth2callback","redirect_uri=localhost:2001/oauth2callback"),400,getInvalidRequestJson(), getResponseBody());
    }

    private static RequestTestData testUnauthorizedClient() {
        return new RequestTestData("testUnauthorizedClient", replaceValueFromRequestBody("&client_secret=def", "&client_secret=123456789"),400,getUnauthorizedClientJson(), getResponseBody());
    }

    private static RequestTestData testInvalidClient() {
        return new RequestTestData("testInvalidClient", replaceValueFromRequestBody("&client_id=abc", "&client_id=123456789"),400,getInvalidClientJson(), getResponseBody());
    }

    private static RequestTestData testNoSessionForCode() {
        return new RequestTestData("testNoSessionForCode", replaceValueFromRequestBodyLazy(getCodeQuery(), getWrongCodeQuery()),400,getInvalidRequestJson(), getResponseBody());
    }

    private static RequestTestData testRedirectUriMissing() {
        return new RequestTestData("testRedirectUriMissing", removeValueFromRequestBody("&redirect_uri=http://localhost:2001/oauth2callback"),400,getInvalidRequestJson(), getResponseBody());
    }

    private static RequestTestData testClientSecretMissing() {
        return new RequestTestData("testClientSecretMissing", removeValueFromRequestBody("&client_secret=def"),400,getInvalidRequestJson(), getResponseBody());
    }

    private static RequestTestData testClientIdMissing() {
        return new RequestTestData("testClientIdMissing", removeValueFromRequestBody("&client_id=abc"),400,getInvalidRequestJson(), getResponseBody());
    }

    private static RequestTestData testCodeMissing() {
        return new RequestTestData("testCodeMissing", removeValueFromRequestBodyLazy(getCodeQuery()),400,getInvalidRequestJson(), getResponseBody());
    }

    private static Callable<String> getCodeQuery(){
        return () -> "code=" + oasit.afterCodeGenerationCode;
    }

    private static Callable<String> getWrongCodeQuery(){
        return () -> "code=" + 123456789;
    }

}
