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

public class EmptyEndpointTest extends RequestParameterizedTest{

    @BeforeEach
    public void setUp() throws Exception{
        super.setUp();
        oasit.runUntilGoodAuthRequest().run();
        exc = OAuth2AuthorizationServerInterceptorNormalTest.getMockEmptyEndpointRequest().call();
    }

    public static Collection<Object[]> data() {
        return Arrays.asList(testCodeResponse(),
                testTokenResponse(),
                testUnsupportedResponseType());
    }

    private static Object[] testUnsupportedResponseType() {
        return new Object[]{"testUnsupportedResponseType", modifySessionToUnsupportedType(),400,getUnsupportedResponseTypeJson(),getResponseBody()};
    }

    private static Object[] testTokenResponse() {
        return new Object[]{"testTokenResponse", modifySessionToTokenResponseType(),307,getBool(true),responseContainsValueInLocationHeader("token=")};
    }

    private static Object[] testCodeResponse() {
        return new Object[]{"testCodeResponse", modifySessionToCodeResponseType(),307,getBool(true),responseContainsValueInLocationHeader("code=")};
    }

    private static Callable<Object> modifySessionToCodeResponseType() {
        return new Callable<Object>() {
            @Override
            public Object call() throws Exception {
                modifySessionAttributes("response_type","code");
                return this;
            }
        };
    }

    private static Callable<Object> modifySessionToTokenResponseType() {
        return new Callable<Object>() {
            @Override
            public Object call() throws Exception {
                modifySessionAttributes("response_type","token");
                return this;
            }
        };
    }

    private static Callable<Object> modifySessionToUnsupportedType(){
        return new Callable<Object>() {
            @Override
            public Object call() throws Exception {
                modifySessionAttributes("response_type","123456789");
                return this;
            };
        };
    }


}
