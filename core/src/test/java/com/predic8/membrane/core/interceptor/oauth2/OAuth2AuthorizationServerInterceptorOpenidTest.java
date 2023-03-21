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
import com.predic8.membrane.core.http.Response;
import com.predic8.membrane.core.interceptor.authentication.session.SessionManager;
import com.predic8.membrane.core.util.Util;
import com.predic8.membrane.core.util.functionalInterfaces.Consumer;
import org.jose4j.jwt.consumer.InvalidJwtException;
import org.junit.jupiter.api.BeforeEach;

import javax.mail.internet.ParseException;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

public class OAuth2AuthorizationServerInterceptorOpenidTest extends OAuth2AuthorizationServerInterceptorBase {

    @BeforeEach
    public void setUp() throws Exception{
        super.setUp();
    }

    public static Collection<Object[]> data() throws Exception {
        return Arrays.asList(testGoodAuthRequest(),
                testGoodGrantedAuthCode(),
                testGoodTokenRequest(),
                testGoodUserinfoRequest());
    }

    private static Object[] testGoodUserinfoRequest() throws Exception {
        return new Object[]{"testGoodUserinfoRequest", runUntilGoodTokenOpenidRequest(),getMockUserinfoRequest(),200,userinfoOpenidRequestPostprocessing()};
    }

    private static Object[] testGoodTokenRequest() throws Exception {
        return new Object[]{"testGoodTokenRequest", runUntilGoodGrantedAuthCodeOpenid(),getMockTokenRequest(),200, validateIdTokenAndGetTokenAndTokenTypeFromResponse()};
    }

    private static Object[] testGoodGrantedAuthCode() throws Exception {
        return new Object[]{"testGoodGrantedAuthCode", runUntilGoodAuthOpenidRequest(), getMockEmptyEndpointRequest(), 307, getCodeFromResponse()};
    }

    private static Object[] testGoodAuthRequest() {
        return new Object[]{"testGoodAuthRequest", noPreprocessing(), getMockAuthOpenidRequestExchange(),307, loginAsJohnOpenid()};
    }

    private static Consumer<Exchange> loginAsJohnOpenid() {
        return exc -> {
            loginAsJohn().call(exc);
            SessionManager.Session s = oasi.getSessionManager().getOrCreateSession(exc);
            Map<String, String> userAttributes = s.getUserAttributes();
            synchronized (userAttributes) {
                userAttributes.put(ParamNames.SCOPE, "openid");
                userAttributes.put(ParamNames.CLAIMS, OAuth2TestUtil.getMockClaims());
                userAttributes.put("consent", "true");
            }
        };
    }

    public static Callable<Exchange> getMockAuthOpenidRequestExchange() {
        return () -> {
            Exchange exc = getMockAuthRequestExchange().call();
            exc.getRequest().setUri(exc.getRequest().getUri() + "&claims=" + OAuth2Util.urlencode(OAuth2TestUtil.getMockClaims()));
            exc.getRequest().setUri(exc.getRequest().getUri().replaceFirst(Pattern.quote("scope=profile"), "scope=" + OAuth2Util.urlencode("openid")));
            exc.getRequest().getHeader().add("Cookie", oasi.getSessionManager().getCookieName() + "=" + OAuth2TestUtil.sessionId);
            return exc;
        };
    }

    private static Consumer<Exchange> validateIdTokenAndGetTokenAndTokenTypeFromResponse() {
        return exc -> {
            assertEquals(true, idTokenIsValid(exc.getResponse()));
            getTokenAndTokenTypeFromResponse().call(exc);
        };
    }

    private static boolean idTokenIsValid(Response response) throws IOException, ParseException {
        // TODO: currently only checks if signature is valid -> also check if requested claims are in it
        HashMap<String, String> json = Util.parseSimpleJSONResponse(response);
        try {
            oasi.getJwtGenerator().getClaimsFromSignedIdToken(json.get(ParamNames.ID_TOKEN), oasi.getIssuer(), "abc");
            return true;
        } catch (InvalidJwtException e) {
            return false;
        }
    }

    private static Consumer<Exchange> userinfoOpenidRequestPostprocessing() throws IOException, ParseException {
        return exchange -> {
            HashMap<String, String> json = Util.parseSimpleJSONResponse(exc.getResponse());
            assertEquals("e@mail.com", json.get("email"));
        };
    }
}
