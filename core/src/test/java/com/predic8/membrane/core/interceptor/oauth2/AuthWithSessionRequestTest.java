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

public class AuthWithSessionRequestTest extends RequestParameterizedTest {

    @BeforeEach
    public void setUp() throws Exception{
        super.setUp();
        oasit.runUntilGoodAuthRequest().run();
        exc = oasit.getMockAuthRequestExchange().call();
    }

    public static Collection<Object[]> data() {
        return Arrays.asList(testPromptEqualsLogin(),
                testPromptEqualsNone(),
                testPromptValueUnknown());
    }

    private static Object[] testPromptValueUnknown() {
        return new Object[]{"testPromptValueUnknown",addValueToRequestUri("prompt=123456789"),400,getLoginRequiredJson(),getResponseBody()};
    }

    private static Object[] testPromptEqualsNone() {
        return new Object[]{"testPromptEqualsNone",addValueToRequestUri("prompt=none"),400,getLoginRequiredJson(),getResponseBody()};
    }

    private static Object[] testPromptEqualsLogin() {
        return new Object[]{"testPromptEqualsLogin",addValueToRequestUri("prompt=login"),307,getBool(true),responseContainsValueInLocationHeader("/oauth2/auth")};
    }
}
