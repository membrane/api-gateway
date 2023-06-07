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
import com.predic8.membrane.core.interceptor.authentication.session.SessionManager;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Map;
import java.util.concurrent.Callable;
import java.util.function.Supplier;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;

public abstract class RequestParameterizedTest {

    protected static OAuth2AuthorizationServerInterceptorBase oasit;
    protected static Exchange exc;

    protected record RequestTestData(
            String testName,
            Callable<Object> modification,
            int expectedStatusCode,
            Supplier<Object> expectedResult,
            Supplier<Object> actualResult) {}



    @BeforeEach
    public void setUp() throws Exception{
        oasit = new OAuth2AuthorizationServerInterceptorNormalTest();
        oasit.setUp();
    }

    @ParameterizedTest
    @MethodSource("data")
    public void test(RequestTestData data) throws Exception {
        data.modification().call();
        OAuth2TestUtil.makeExchangeValid(exc);
        oasit.oasi.handleRequest(exc);

        assertEquals(data.expectedStatusCode(),exc.getResponse().getStatusCode());
        assertEquals(data.expectedResult().get(),data.actualResult().get());
    }

    static Callable<Exchange> getExchange(){
        return () -> exc;
    }

    public static Callable<Object> removeValueFromRequestUri(final String value){
        return () -> replaceValueFromRequestUri(value, "").call();
    }

    public static Callable<Object> replaceValueFromRequestUri(final String value, final String replacement){
        return new Callable<>() {
            @Override
            public Object call() throws Exception {
                exc.getRequest().setUri(exc.getRequest().getUri().replaceFirst(Pattern.quote(value), replacement));
                return this;
            }
        };
    }

    public static Callable<Object> replaceValueFromRequestBody(final String value, final String replacement){
        return new Callable<>() {
            @Override
            public Object call() throws Exception {
                exc.getRequest().setBodyContent(exc.getRequest().getBodyAsStringDecoded().replaceFirst(Pattern.quote(value), replacement).getBytes());
                return this;
            }
        };
    }

    public static Callable<Object> replaceValueFromRequestBodyLazy(final Callable<String> value, final Callable<String> replacement){
        return new Callable<>() {
            @Override
            public Object call() throws Exception {
                exc.getRequest().setBodyContent(exc.getRequest().getBodyAsStringDecoded().replaceFirst(Pattern.quote(value.call()), replacement.call()).getBytes());
                return this;
            }
        };
    }

    public static Callable<Object> removeValueFromRequestBody(final String value){
        return () -> replaceValueFromRequestBody(value, "").call();
    }

    public static Callable<Object> removeValueFromRequestBodyLazy(final Callable<String> value){
        return () -> replaceValueFromRequestBody(value.call(), "").call();
    }

    public static Supplier<Object> responseContainsValueInLocationHeader(final String value){
        return () -> exc.getResponse().getHeader().getFirstValue("Location").contains(value);
    }

    public static Supplier<Object> getInvalidRequestJson(){
        return () -> "{\"error\":\"invalid_request\"}";
    }

    public static Supplier<Object> getUnauthorizedClientJson(){
        return () -> "{\"error\":\"unauthorized_client\"}";
    }

    public static Supplier<Object> getInvalidClientJson(){
        return () -> "{\"error\":\"invalid_client\"}";
    }

    public static Callable<Object> addValueToRequestUri(final String value) {
        return new Callable<>() {
            @Override
            public Object call() {
                exc.getRequest().setUri(exc.getRequest().getUri() + "&" + value);
                return this;
            }
        };
    }

    public static Supplier<Object> getResponseBody(){
        return () -> exc.getResponse().getBodyAsStringDecoded();
    }

    public static Supplier<Object> getBool(final boolean bool){
        return () -> bool;
    }

    public static Supplier<Object> getLoginRequiredJson(){
        return () -> "{\"error\":\"login_required\"}";
    }

    public static Supplier<Object> getInvalidGrantJson(){
        return () -> "{\"error\":\"invalid_grant\"}";
    }

    public static Supplier<Object> getUnsupportedResponseTypeJson(){
        return () -> "{\"error\":\"unsupported_response_type\"}";
    }

    public static Supplier<Object> getConsentRequiredJson(){
        return () -> "{\"error\":\"consent_required\"}";
    }

    public static void modifySessionAttributes(String name, String value){
        SessionManager.Session s = oasit.oasi.getSessionManager().getOrCreateSession(exc);
        Map<String, String> userAttributes = s.getUserAttributes();
        synchronized (userAttributes) {
            userAttributes.put(name, value);
        }
    }
}
