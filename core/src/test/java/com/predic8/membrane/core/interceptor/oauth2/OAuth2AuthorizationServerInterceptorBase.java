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

import com.predic8.membrane.core.*;
import com.predic8.membrane.core.exchange.*;
import com.predic8.membrane.core.http.*;
import com.predic8.membrane.core.interceptor.authentication.session.*;
import com.predic8.membrane.core.interceptor.oauth2.authorizationservice.*;
import com.predic8.membrane.core.util.*;
import com.predic8.membrane.core.util.functionalInterfaces.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.*;
import org.junit.jupiter.params.provider.*;

import java.util.*;
import java.util.concurrent.*;
import java.util.regex.*;

import static com.predic8.membrane.core.Constants.*;
import static com.predic8.membrane.core.http.Header.*;
import static com.predic8.membrane.core.http.MimeType.*;
import static org.junit.jupiter.api.Assertions.*;

public abstract class OAuth2AuthorizationServerInterceptorBase {

    static Router router;
    static Exchange exc;
    static OAuth2AuthorizationServerInterceptor oasi;
    static MembraneAuthorizationService mas;
    static StaticUserDataProvider.User johnsUserData;
    static Map<String,String> afterLoginMockParams;
    static String afterCodeGenerationCode;
    static String afterTokenGenerationToken;
    static String afterTokenGenerationTokenType;

    static ExceptionThrowingConsumer<Exchange> noPostprocessing() {
        return exchange -> {};
    }

    static Runnable noPreprocessing() {
        return () -> {};
    }

    public static Callable<Exchange> getMockAuthRequestExchange() {
        return () -> {
            Exchange exc = new Request.Builder().get(mas.getLoginURL("123security","http://localhost:2001/oauth2callback", "/")).buildExchange();
            exc.getRequest().getHeader().add("Cookie",oasi.getSessionManager().getCookieName() + "=" + OAuth2TestUtil.sessionId);
            return exc;
        };
    }

    public static ExceptionThrowingConsumer<Exchange> loginAsJohn(){
        return exchange -> {
            SessionManager.Session session = oasi.getSessionManager().getOrCreateSession(exchange);

            // @TODO Split with Pattern -> extract Method, Test, maybe move to Util
            OAuth2TestUtil.sessionId = exchange.getResponse().getHeader().getFirstValue("Set-Cookie").split(Pattern.quote("="))[1].split(Pattern.quote(";"))[0];
            session.preAuthorize("john", afterLoginMockParams);
        };
    }

    public static Callable<Exchange> getMockEmptyEndpointRequest() {
        return () -> {
            Exchange exc = new Request.Builder().get("/").buildExchange();
            exc.getRequest().getHeader().add("Cookie", oasi.getSessionManager().getCookieName() + "=" + OAuth2TestUtil.sessionId);
            OAuth2TestUtil.makeExchangeValid(exc);
            return exc;
        };
    }

    static ExceptionThrowingConsumer<Exchange> getCodeFromResponse() {
        return exc -> {
            String loc = exc.getResponse().getHeader().getFirstValue("Location");
            for (String s1 : loc.split(Pattern.quote("?"))) {
                if (s1.startsWith("code=")) {
                    for (String s2 : s1.split("&")) {
                        if (s2.startsWith("code="))
                            afterCodeGenerationCode = s2.substring(5);
                        break;
                    }
                }
            }
        };
    }

    public static Callable<Exchange> getMockTokenRequest() {
        return () -> new Request.Builder()
                .post(mas.getTokenEndpoint())
                .contentType(APPLICATION_X_WWW_FORM_URLENCODED)
                .header(ACCEPT, APPLICATION_JSON)
                .header(USER_AGENT, USERAGENT)
                .body("code=" + afterCodeGenerationCode
                      + "&client_id=" + mas.getClientId()
                      + "&client_secret=" + mas.getClientSecret()
                      + "&redirect_uri=" + "http://localhost:2001/" + "oauth2callback"
                      + "&grant_type=authorization_code")
                .buildExchange();
    }

    static ExceptionThrowingConsumer<Exchange> getTokenAndTokenTypeFromResponse() {
        return exc -> {
            HashMap<String, String> json = Util.parseSimpleJSONResponse(exc.getResponse());
            afterTokenGenerationToken = json.get("access_token");
            afterTokenGenerationTokenType = json.get("token_type");
        };
    }

    public static Callable<Exchange> getMockUserinfoRequest() {
        return () -> new Request.Builder()
                .get(mas.getUserInfoEndpoint())
                .header("Authorization", afterTokenGenerationTokenType + " " + afterTokenGenerationToken)
                .header("User-Agent", USERAGENT)
                .header(ACCEPT, APPLICATION_JSON)
                .buildExchange();
    }

    public static Runnable runUntilGoodTokenRequest() {
        return runTest(OAuth2AuthorizationServerInterceptorNormalTest.class,"testGoodTokenRequest");
    }

    public static Runnable runUntilGoodGrantedAuthCode() {
        return runTest(OAuth2AuthorizationServerInterceptorNormalTest.class,"testGoodGrantedAuthCode");
    }

    public static Runnable runUntilGoodAuthRequest() {
        return runTest(OAuth2AuthorizationServerInterceptorNormalTest.class,"testGoodAuthRequest");
    }

    public static Runnable runUntilGoodTokenOpenidRequest() {
        return runTest(OAuth2AuthorizationServerInterceptorOpenidTest.class,"testGoodTokenRequest");
    }

    public static Runnable runUntilGoodGrantedAuthCodeOpenid() {
        return runTest(OAuth2AuthorizationServerInterceptorOpenidTest.class,"testGoodGrantedAuthCode");
    }

    public static Runnable runUntilGoodAuthOpenidRequest() {
        return runTest(OAuth2AuthorizationServerInterceptorOpenidTest.class,"testGoodAuthRequest");
    }

    @BeforeEach
    public void setUp() throws Exception{
        router = new HttpRouter();
        initOasi();
        initMas();
        initLoginMockParametersForJohn();
    }

    public static Collection<Object[]> data() throws Exception {
        return List.of();
    }

    @ParameterizedTest(name="{0}")
    @MethodSource("data")
    public void test(
            String testName,
            Runnable preprocessing,
            Callable<Exchange> preparedExchange,
            int expectedStatusCode,
            ExceptionThrowingConsumer<Exchange> postprocessing) throws Exception {
        preprocessing.run();
        exc = preparedExchange.call();
        OAuth2TestUtil.makeExchangeValid(exc);
        oasi.handleRequest(exc);
        assertEquals(expectedStatusCode,exc.getResponse().getStatusCode());
        postprocessing.accept(exc);
    }

    protected static <T>Runnable runTest(final Class<T> caller, final String name){
        return () -> {
            try {
                List<Object[]> allParams = (List<Object[]>) caller.getMethod("data").invoke(null);
                Object[] params = null;
                for (Object[] p : allParams) {
                    if (name.equals(p[0])){
                        params = p;
                        break;
                    }
                }

                Runnable preprocessing = (Runnable) params[1];
                Callable<Exchange> preparedExchange = (Callable<Exchange>) params[2];
                int expectedStatusCode = (Integer) params[3];
                ExceptionThrowingConsumer<Exchange> postprocessing = (ExceptionThrowingConsumer<Exchange>) params[4];

                preprocessing.run();
                Exchange exc = preparedExchange.call();
                OAuth2TestUtil.makeExchangeValid(exc);
                oasi.handleRequest(exc);
                assertEquals(expectedStatusCode, exc.getResponse().getStatusCode());
                postprocessing.accept(exc);

            }catch(Exception e){
                e.printStackTrace();
            }
        };

    }

    private void initLoginMockParametersForJohn() {
        afterLoginMockParams = new HashMap<>();
        afterLoginMockParams.put("response_type","code");
        afterLoginMockParams.put("scope","profile");
        afterLoginMockParams.put("redirect_uri","http://localhost:2001/oauth2callback");
        afterLoginMockParams.put("state", "security_token=123&url=/");
        afterLoginMockParams.put("client_id", "abc");
        afterLoginMockParams.putAll(johnsUserData.getAttributes());
    }

    private void initMas() throws Exception {
        mas = new MembraneAuthorizationService();
        mas.setClientId("abc");
        mas.setClientSecret("def");
        mas.setSrc(System.getProperty("user.dir") + "/src/test/resources/oauth2");
        mas.init(router);
        //mas.init2();    // requires pull request 330
    }

    private void initOasi() throws Exception {
        oasi = new OAuth2AuthorizationServerInterceptor() {
            @Override
            public String computeBasePath() {
                return "";
            }
        };
        setOasiUserDataProvider();
        setOasiClientList();
        setOasiClaimList();
        setOasiProperties();
        oasi.init(router);
    }

    private void setOasiProperties() {
        oasi.setLocation("src/test/resources/oauth2/loginDialog/dialog");
        oasi.setConsentFile("src/test/resources/oauth2/consentFile.json");
        oasi.setPath("/login/");
        oasi.setIssuer("http://Localhost:2001");
    }

    private void setOasiClaimList() {
        ClaimList cl = new ClaimList();
        cl.setValue("username email sub");
        ArrayList<ClaimList.Scope> scopes = new ArrayList<>();
        ClaimList.Scope scope = new ClaimList.Scope("profile","username email");
        scopes.add(scope);
        cl.setScopes(scopes);
        oasi.setClaimList(cl);
    }

    private void setOasiClientList() {
        StaticClientList cl = new StaticClientList();
        ArrayList<Client> clients = new ArrayList<>();
        Client john2 = new Client("abc","def","http://localhost:2001/oauth2callback", "authorization_code,password,client_credentials,refresh_token,implicit");
        clients.add(john2);
        cl.setClients(clients);
        oasi.setClientList(cl);
    }

    private void setOasiUserDataProvider() {
        StaticUserDataProvider udp = new StaticUserDataProvider();
        ArrayList<StaticUserDataProvider.User> users = new ArrayList<>();
        johnsUserData = new StaticUserDataProvider.User("john", "password");
        johnsUserData.getAttributes().put("email","e@mail.com");
        users.add(johnsUserData);
        udp.setUsers(users);
        oasi.setUserDataProvider(udp);
    }
}
