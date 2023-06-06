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
import java.util.stream.Stream;

public class AuthWithoutSessionRequestTest extends RequestParameterizedTest{

    @BeforeEach
    public void setUp() throws Exception{
        super.setUp();
        exc = oasit.getMockAuthRequestExchange().call();
    }

    public static Stream<Named<RequestTestData>> data() {
        return Stream.of(testClientIdMissing(),
                testRedirectUriMissing(),
                testResponseTypeMissing(),
                testScopeMissing(),
                testInvalidClientId(),
                testRedirectUriNotAbsolute(),
                testRedirectUriNotEqauls(),
                testPromptIsNone(),
                testUnsupportedResponseType(),
                testEmptyScopeList()
        ).map(data -> Named.of(data.testName(), data));
    }

    private static RequestTestData testPromptIsNone() {
        return new RequestTestData("testPromptIsNone",addValueToRequestUri("prompt=none"),307,getBool(true),responseContainsValueInLocationHeader("error=login_required"));
    }

    private static RequestTestData testEmptyScopeList() {
        return new RequestTestData("testEmptyScopeList",replaceValueFromRequestUri("scope=profile","scope=123456789"),307,getBool(true),responseContainsValueInLocationHeader("error=invalid_scope"));
    }

    private static RequestTestData testUnsupportedResponseType() {
        return new RequestTestData("testUnsupportedResponseType",replaceValueFromRequestUri("response_type=code","response_type=code123456789"),307,getBool(true),responseContainsValueInLocationHeader("error=unsupported_response_type"));
    }

    private static RequestTestData testRedirectUriNotEqauls() {
        return new RequestTestData("testRedirectUriNotEqauls",replaceValueFromRequestUri("redirect_uri=http://localhost:2001/oauth2callback","redirect_uri=http://localhost:2001/oauth2callback2"),400,getInvalidRequestJson(), getResponseBody());
    }

    private static RequestTestData testRedirectUriNotAbsolute() {
        return new RequestTestData("testRedirectUriNotAbsolute",replaceValueFromRequestUri("redirect_uri=http://localhost:2001/oauth2callback","redirect_uri=localhost:2001/oauth2callback"),400,getInvalidRequestJson(), getResponseBody());
    }

    private static RequestTestData testResponseTypeMissing() {
        return new RequestTestData("testResponseTypeMissing",removeValueFromRequestUri("&response_type=code"),307,getBool(true),responseContainsValueInLocationHeader("error=invalid_request"));
    }

    private static RequestTestData testRedirectUriMissing() {
        return new RequestTestData("testRedirectUriMissing",removeValueFromRequestUri("&redirect_uri=http://localhost:2001/oauth2callback"),400,getInvalidRequestJson(), getResponseBody());
    }

    private static RequestTestData testInvalidClientId() {
        return new RequestTestData("testInvalidClientId",replaceValueFromRequestUri("client_id=abc","client_id=cba"),400,getUnauthorizedClientJson(), getResponseBody());
    }

    private static RequestTestData testClientIdMissing() {
        return new RequestTestData("testClientIdMissing",removeValueFromRequestUri("client_id=abc&"),400,getInvalidRequestJson(), getResponseBody());
    }

    private static RequestTestData testScopeMissing() {
        return new RequestTestData("testScopeMissing",removeValueFromRequestUri("&scope=profile"),307,getBool(true),responseContainsValueInLocationHeader("error=invalid_request"));
    }
}
