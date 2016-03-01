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
import com.predic8.membrane.core.rules.NullRule;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runners.Parameterized;

import java.util.concurrent.Callable;
import java.util.regex.Pattern;

public class RequestParameterizedTest {
    static OAuth2AuthorizationServerInterceptorTest oasit;
    static Exchange exc;

    @Before
    public void setUp() throws Exception{
        oasit = new OAuth2AuthorizationServerInterceptorTest();
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

    public static Callable<Object> removeValueFromRequestUri(final Callable<Exchange> exc, final String value){
        return new Callable<Object>() {
            @Override
            public Object call() throws Exception {
                return replaceValueFromRequestUri(exc,value,"").call();
            }
        };
    }

    public static Callable<Object> removeValueFromRequestUriLazy(final Callable<Exchange> exc,final Callable<String> lazyValue){
        return new Callable<Object>() {
            @Override
            public Object call() throws Exception {
                return replaceValueFromRequestUri(exc,lazyValue.call(),"").call();
            }
        };
    }

    public static Callable<Object> replaceValueFromRequestUri(final Callable<Exchange> exc, final String value, final String replacement){
        return new Callable<Object>(){
            @Override
            public Object call() throws Exception {
                exc.call().getRequest().setUri(exc.call().getRequest().getUri().replaceFirst(Pattern.quote(value),replacement));
                makeExchangeValid(exc);
                return this;
            }
        };
    }

    public static Callable<Object> responseContainsValueInLocationHeader(final Callable<Exchange> exc, final String value){
        return new Callable<Object>() {
            @Override
            public Object call() throws Exception {
                return exc.call().getResponse().getHeader().getFirstValue("Location").contains(value);
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


    public static void makeExchangeValid(Callable<Exchange> exc) throws Exception {
        exc.call().setOriginalRequestUri(exc.call().getRequest().getUri());
        exc.call().setRule(new NullRule());
    }

    public static Callable<Object> addValueToRequestUri(final Callable<Exchange> exc, final String value) {
        return new Callable<Object>() {
            @Override
            public Object call() throws Exception {
                exc.call().getRequest().setUri(exc.call().getRequest().getUri() + "&" + value);
                makeExchangeValid(exc);
                return this;
            }
        };
    }

    public static Callable<Object> getResponseBody(final Callable<Exchange> exc){
        return new Callable<Object>() {
            @Override
            public Object call() throws Exception {
                return exc.call().getResponse().getBodyAsStringDecoded();
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
}
