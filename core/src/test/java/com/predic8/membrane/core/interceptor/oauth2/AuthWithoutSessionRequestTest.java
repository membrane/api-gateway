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
import org.junit.runners.Parameterized.Parameters;

import java.util.Arrays;
import java.util.Collection;

@RunWith(Parameterized.class)
public class AuthWithoutSessionRequestTest extends RequestParameterizedTest{

    @Before
    public void setUp() throws Exception{
        super.setUp();
        exc = oasit.getAuthRequestExchange();
    }

    @Parameters(name = "{0}")
    public static Collection<Object[]> data() {
        return Arrays.asList(new Object[][] {
                testClientIdMissing(),
                testRedirectUriMissing(),
                testResponseTypeMissing(),
                testScopeMissing(),
                testInvalidClientId(),
                testRedirectUriNotAbsolute(),
                testRedirectUriNotEqauls(),
                testPromptIsNone(),
                testUnsupportedResponseType(),
                testEmptyScopeList()
        });
    }

    private static Object[] testPromptIsNone() {
        return new Object[]{"testPromptIsNone",addValueToRequestUri(getExchange(),"prompt=none"),307,getBool(true),responseContainsValueInLocationHeader(getExchange(),"error=login_required")};
    }

    private static Object[] testEmptyScopeList() {
        return new Object[]{"testEmptyScopeList",replaceValueFromRequestUri(getExchange(),"scope=profile","scope=123456789"),307,getBool(true),responseContainsValueInLocationHeader(getExchange(),"error=invalid_scope")};
    }

    private static Object[] testUnsupportedResponseType() {
        return new Object[]{"testUnsupportedResponseType",replaceValueFromRequestUri(getExchange(),"response_type=code","response_type=code123456789"),307,getBool(true),responseContainsValueInLocationHeader(getExchange(),"error=unsupported_response_type")};
    }

    private static Object[] testRedirectUriNotEqauls() {
        return new Object[]{"testRedirectUriNotEqauls",replaceValueFromRequestUri(getExchange(),"redirect_uri=http://localhost:2001/oauth2callback","redirect_uri=http://localhost:2001/oauth2callback2"),400,getInvalidRequestJson(), getResponseBody(getExchange())};
    }

    private static Object[] testRedirectUriNotAbsolute() {
        return new Object[]{"testRedirectUriNotAbsolute",replaceValueFromRequestUri(getExchange(),"redirect_uri=http://localhost:2001/oauth2callback","redirect_uri=localhost:2001/oauth2callback"),400,getInvalidRequestJson(), getResponseBody(getExchange())};
    }

    private static Object[] testResponseTypeMissing() {
        return new Object[]{"testResponseTypeMissing",removeValueFromRequestUri(getExchange(),"&response_type=code"),307,getBool(true),responseContainsValueInLocationHeader(getExchange(),"error=invalid_request")};
    }

    private static Object[] testRedirectUriMissing() {
        return new Object[]{"testRedirectUriMissing",removeValueFromRequestUri(getExchange(),"&redirect_uri=http://localhost:2001/oauth2callback"),400,getInvalidRequestJson(), getResponseBody(getExchange())};
    }

    private static Object[] testInvalidClientId() {
        return new Object[]{"testInvalidClientId",replaceValueFromRequestUri(getExchange(),"client_id=abc","client_id=cba"),400,getUnauthorizedClientJson(), getResponseBody(getExchange())};
    }

    private static Object[] testClientIdMissing() {
        return new Object[]{"testClientIdMissing",removeValueFromRequestUri(getExchange(),"client_id=abc&"),400,getInvalidRequestJson(), getResponseBody(getExchange())};
    }

    private static Object[] testScopeMissing() {
        return new Object[]{"testScopeMissing",removeValueFromRequestUri(getExchange(),"&scope=profile"),307,getBool(true),responseContainsValueInLocationHeader(getExchange(),"error=invalid_request")};
    }
}
