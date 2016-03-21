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

import com.predic8.membrane.core.exchange.Exchange;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runners.Parameterized;

import java.util.concurrent.Callable;
import java.util.regex.Pattern;

public abstract class RequestParameterizedTest {

    protected static OAuth2AuthorizationServerInterceptorBase oasit;
    protected static Exchange exc;



    @Before
    public void setUp() throws Exception{
        oasit = new OAuth2AuthorizationServerInterceptorNormalTest();
        oasit.setUp();
    }

    @Parameterized.Parameter
    public String testName;
    @Parameterized.Parameter(value = 1)
    public Callable<Object> modification;
    @Parameterized.Parameter(value = 2)
    public int expectedStatusCode;
    @Parameterized.Parameter(value = 3)
    public Callable<Object> expectedResult;
    @Parameterized.Parameter(value = 4)
    public Callable<Object> actualResult;

    @Test
    public void test() throws Exception {
        modification.call();
        OAuth2TestUtil.makeExchangeValid(exc);
        oasit.oasi.handleRequest(exc);
        Assert.assertEquals(expectedStatusCode,exc.getResponse().getStatusCode());
        Assert.assertEquals(expectedResult.call(),actualResult.call());
    }

    static Callable<Exchange> getExchange(){
        return new Callable<Exchange>() {
            @Override
            public Exchange call() throws Exception {
                return exc;
            }
        };
    }

    public static Callable<Object> removeValueFromRequestUri(final String value){
        return new Callable<Object>() {
            @Override
            public Object call() throws Exception {
                return replaceValueFromRequestUri(value,"").call();
            }
        };
    }

    public static Callable<Object> replaceValueFromRequestUri(final String value, final String replacement){
        return new Callable<Object>(){
            @Override
            public Object call() throws Exception {
                exc.getRequest().setUri(exc.getRequest().getUri().replaceFirst(Pattern.quote(value),replacement));
                return this;
            }
        };
    }

    public static Callable<Object> replaceValueFromRequestBody(final String value, final String replacement){
        return new Callable<Object>() {
            @Override
            public Object call() throws Exception {
                exc.getRequest().setBodyContent(exc.getRequest().getBodyAsStringDecoded().replaceFirst(Pattern.quote(value),replacement).getBytes());
                return this;
            }
        };
    }

    public static Callable<Object> replaceValueFromRequestBodyLazy(final Callable<String> value, final Callable<String> replacement){
        return new Callable<Object>() {
            @Override
            public Object call() throws Exception {
                exc.getRequest().setBodyContent(exc.getRequest().getBodyAsStringDecoded().replaceFirst(Pattern.quote(value.call()),replacement.call()).getBytes());
                return this;
            }
        };
    }

    public static Callable<Object> removeValueFromRequestBody(final String value){
        return new Callable<Object>() {
            @Override
            public Object call() throws Exception {
                return replaceValueFromRequestBody(value,"").call();
            }
        };
    }

    public static Callable<Object> removeValueFromRequestBodyLazy(final Callable<String> value){
        return new Callable<Object>() {
            @Override
            public Object call() throws Exception {
                return replaceValueFromRequestBody(value.call(),"").call();
            }
        };
    }

    public static Callable<Object> responseContainsValueInLocationHeader(final String value){
        return new Callable<Object>() {
            @Override
            public Object call() throws Exception {
                return exc.getResponse().getHeader().getFirstValue("Location").contains(value);
            }
        };

    }

    public static Callable<Object> getInvalidRequestJson(){
        return new Callable<Object>() {
            @Override
            public Object call() throws Exception {
                return "{\"error\":\"invalid_request\"}";
            }
        };
    }

    public static Callable<Object> getUnauthorizedClientJson(){
        return new Callable<Object>() {
            @Override
            public Object call() throws Exception {
                return "{\"error\":\"unauthorized_client\"}";
            }
        };
    }

    public static Callable<Object> getInvalidClientJson(){
        return new Callable<Object>() {
            @Override
            public Object call() throws Exception {
                return "{\"error\":\"invalid_client\"}";
            }
        };
    }

    public static Callable<Object> addValueToRequestUri(final String value) {
        return new Callable<Object>() {
            @Override
            public Object call() throws Exception {
                exc.getRequest().setUri(exc.getRequest().getUri() + "&" + value);
                return this;
            }
        };
    }

    public static Callable<Object> getResponseBody(){
        return new Callable<Object>() {
            @Override
            public Object call() throws Exception {
                return exc.getResponse().getBodyAsStringDecoded();
            }
        };
    }

    public static Callable<Boolean> getBool(final boolean bool){
        return new Callable<Boolean>() {
            @Override
            public Boolean call() throws Exception {
                return bool;
            }
        };
    }

    public static Callable<Object> getLoginRequiredJson(){
        return new Callable<Object>() {
            @Override
            public Object call() throws Exception {
                return "{\"error\":\"login_required\"}";
            }
        };
    }

    public static Callable<Object> getInvalidGrantJson(){
        return new Callable<Object>() {
            @Override
            public Object call() throws Exception {
                return "{\"error\":\"invalid_grant\"}";
            }
        };
    }

    public static Callable<Object> getUnsupportedResponseTypeJson(){
        return new Callable<Object>() {
            @Override
            public Object call() throws Exception {
                return "{\"error\":\"unsupported_response_type\"}";
            }
        };
    }

    public static Callable<Object> getConsentRequiredJson(){
        return new Callable<Object>() {
            @Override
            public Object call() throws Exception {
                return "{\"error\":\"consent_required\"}";
            }
        };
    }

    public static void modifySessionAttributes(String name, String value){
        oasit.oasi.getSessionManager().getSession(OAuth2TestUtil.sessionId).getUserAttributes().put(name,value);
    }
}
