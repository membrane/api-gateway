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
public class EmptyEndpointTest extends RequestParameterizedTest{

    @Before
    public void setUp() throws Exception{
        super.setUp();
        oasit.testGoodAuthRequest();
        exc = oasit.getMockEmptyEndpointRequest();
    }

    @Parameterized.Parameters(name = "{0}")
    public static Collection<Object[]> data() {
        return Arrays.asList(new Object[][] {
                testCodeResponse(),
                testTokenResponse(),
                testUnsupportedResponseType()
        });
    }

    private static Object[] testUnsupportedResponseType() {
        return new Object[]{"testUnsupportedResponseType", modifySessionToUnsupportedType(),400,getUnsupportedResponseTypeJson(),getResponseBody(getExchange())};
    }

    private static Object[] testTokenResponse() {
        return new Object[]{"testTokenResponse", modifySessionToTokenResponseType(),307,getBool(true),responseContainsValueInLocationHeader(getExchange(),"token=")};
    }

    private static Object[] testCodeResponse() {
        return new Object[]{"testCodeResponse", modifySessionToCodeResponseType(),307,getBool(true),responseContainsValueInLocationHeader(getExchange(),"code=")};
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

    private static void modifySessionAttributes(String name, String value){
        oasit.oasi.getSessionManager().getSession("123").getUserAttributes().put(name,value);
    }


}
