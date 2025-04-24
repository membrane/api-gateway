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

import com.predic8.membrane.core.exchange.*;
import com.predic8.membrane.core.http.*;
import com.predic8.membrane.core.util.*;
import com.predic8.membrane.core.util.functionalInterfaces.*;
import org.junit.jupiter.api.*;

import java.util.*;
import java.util.concurrent.*;

import static com.predic8.membrane.core.Constants.*;
import static com.predic8.membrane.core.http.Header.*;
import static com.predic8.membrane.core.http.MimeType.*;
import static org.junit.jupiter.api.Assertions.*;

public class OAuth2AuthorizationServerInterceptorNormalTest extends OAuth2AuthorizationServerInterceptorBase {

    @BeforeEach
    public void setUp() throws Exception{
        super.setUp();
    }

    public static Collection<Object[]> data() throws Exception {
        return Arrays.asList(testBadRequest(),
                testGoodAuthRequest(),
                testGoodGrantedAuthCode(),
                testGoodTokenRequest(),
                testGoodUserinfoRequest(),
                testGoodRefreshRequest(),
                testBadRefreshWithAccessTokenRequest(),
                testBadUserInfoWithRefreshTokenRequest(),
                testGoodRevocationRequest());
    }

    private static Object[] testGoodRevocationRequest() {
        return new Object[]{"testGoodRevocationRequest", runUntilGoodTokenRequest(),getMockRevocationRequest(),200,noPostprocessing()};
    }

    private static Object[] testGoodUserinfoRequest() {
        return new Object[]{"testGoodUserinfoRequest", runUntilGoodTokenRequest(),getMockUserinfoRequest(),200,userinfoRequestPostprocessing()};
    }

    private static Object[] testGoodRefreshRequest() {
        return new Object[]{"testGoodRefreshRequest", runUntilGoodTokenRequest(),getMockRefreshRequest(),200,refreshTokenRequestPostprocessing()};
    }

    private static Object[] testBadRefreshWithAccessTokenRequest() {
        return new Object[]{"testBadRefreshWithAccessTokenRequest", runUntilGoodTokenRequest(),getMockInvalidRefreshWithAccessTokenRequest(),400,noPostprocessing()};
    }

    private static Object[] testBadUserInfoWithRefreshTokenRequest() {
        return new Object[]{"testBadUserInfoWithRefreshTokenRequest", runUntilGoodTokenRequest(),getMockInvalidUserinfoWithRefreshTokenRequest(),401,noPostprocessing()};
    }

    private static Object[] testGoodTokenRequest() {
        return new Object[]{"testGoodTokenRequest", runUntilGoodGrantedAuthCode(),getMockTokenRequest(),200, getTokenAndTokenTypeFromResponse()};
    }

    private static Object[] testGoodGrantedAuthCode() {
        return new Object[]{"testGoodGrantedAuthCode", runUntilGoodAuthRequest(), getMockEmptyEndpointRequest(), 302, getCodeFromResponse()};
    }

    private static Object[] testGoodAuthRequest() {
        return new Object[]{"testGoodAuthRequest", noPreprocessing(), getMockAuthRequestExchange(),302, loginAsJohn()};
    }

    private static Object[] testBadRequest() {
        return new Object[]{"testBadRequest", noPreprocessing(), getMockBadRequestExchange(),400, noPostprocessing()};
    }

    public static Callable<Exchange> getMockBadRequestExchange() {
        return () -> new Request.Builder().get("/thisdoesntexist").buildExchange();
    }

    private static ExceptionThrowingConsumer<Exchange> userinfoRequestPostprocessing() {
        return exchange -> {
            HashMap<String, String> json = Util.parseSimpleJSONResponse(exc.getResponse());
            assertEquals("john", json.get("username"));
        };
    }

    public static Callable<Exchange> getMockRevocationRequest() {
        return () -> new Request.Builder().post(mas.getRevocationEndpoint())
                .header(CONTENT_TYPE, APPLICATION_X_WWW_FORM_URLENCODED)
                .header(USER_AGENT, USERAGENT)
                .body("token=" + afterTokenGenerationToken + "&client_id=" + mas.getClientId() + "&client_secret=" + mas.getClientSecret())
                .buildExchange();
    }

    public static Callable<Exchange> getMockPasswordRequestExchange() {
        return () -> new Request.Builder().post(mas.getTokenEndpoint())
                .header(CONTENT_TYPE, APPLICATION_X_WWW_FORM_URLENCODED)
                .body("grant_type=password&username=john&password=password&client_id=abc&client_secret=def")
                .buildExchange();
    }

    public static Callable<Exchange> getMockClientCredentialsRequestExchange() {
        return () -> new Request.Builder().post(mas.getTokenEndpoint())
                .header(CONTENT_TYPE, APPLICATION_X_WWW_FORM_URLENCODED)
                .body("grant_type=password&client_id=abc&client_secret=def")
                .buildExchange();
    }
}
