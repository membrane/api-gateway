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

import com.predic8.membrane.core.Constants;
import com.predic8.membrane.core.HttpRouter;
import com.predic8.membrane.core.Router;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.Header;
import com.predic8.membrane.core.http.Request;
import com.predic8.membrane.core.interceptor.authentication.session.SessionManager;
import com.predic8.membrane.core.interceptor.authentication.session.StaticUserDataProvider;
import com.predic8.membrane.core.interceptor.oauth2.authorizationservice.MembraneAuthorizationService;
import com.predic8.membrane.core.util.Util;
import com.predic8.membrane.core.util.functionalInterfaces.Consumer;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runners.Parameterized;

import javax.mail.internet.ParseException;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.regex.Pattern;

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

    static Consumer<Exchange> noPostprocessing() {
        return new Consumer<Exchange>() {
            @Override
            public void call(Exchange exchange) {
                return;
            }
        };
    }

    static Runnable noPreprocessing() {
        return new Runnable() {
            @Override
            public void run() {
                return;
            }
        };
    }

    public static Callable<Exchange> getMockAuthRequestExchange() throws Exception {
        return new Callable<Exchange>() {
            @Override
            public Exchange call() throws Exception {
                Exchange exc = new Request.Builder().get(mas.getLoginURL("123security","http://localhost:2001/", "/")).buildExchange();
                exc.getRequest().getHeader().add("Cookie",oasi.getSessionManager().getCookieName() + "=" + OAuth2TestUtil.sessionId);
                return exc;
            }
        };
    }

    public static Consumer<Exchange> loginAsJohn(){
        return new Consumer<Exchange>() {
            @Override
            public void call(Exchange exchange) {
                SessionManager.Session session = oasi.getSessionManager().getOrCreateSession(exchange);
                OAuth2TestUtil.sessionId = exchange.getResponse().getHeader().getFirstValue("Set-Cookie").split(Pattern.quote("="))[1].split(Pattern.quote(";"))[0];
                session.preAuthorize("john", afterLoginMockParams);
            }
        };
    }

    public static Callable<Exchange> getMockEmptyEndpointRequest() throws Exception {
        return new Callable<Exchange>() {
            @Override
            public Exchange call() throws Exception {
                Exchange exc =  new Request.Builder().get("/").buildExchange();
                exc.getRequest().getHeader().add("Cookie",oasi.getSessionManager().getCookieName() + "=" + OAuth2TestUtil.sessionId);
                OAuth2TestUtil.makeExchangeValid(exc);
                return exc;
            }
        };
    }

    static Consumer<Exchange> getCodeFromResponse() {
        return new Consumer<Exchange>() {
            @Override
            public void call(Exchange exc) {
                String loc = exc.getResponse().getHeader().getFirstValue("Location");
                for(String s1 : loc.split(Pattern.quote("?"))){
                    if(s1.startsWith("code=")){
                        for(String s2 : s1.split("&")){
                            if(s2.startsWith("code="))
                                afterCodeGenerationCode = s2.substring(5);
                            break;
                        }
                    }
                }
            }
        };
    }

    public static Callable<Exchange> getMockTokenRequest() throws Exception {
        return new Callable<Exchange>() {
            @Override
            public Exchange call() throws Exception {
                return new Request.Builder()
                        .post(mas.getTokenEndpoint())
                        .header(Header.CONTENT_TYPE, "application/x-www-form-urlencoded")
                        .header(Header.ACCEPT, "application/json")
                        .header(Header.USER_AGENT, Constants.USERAGENT)
                        .body("code=" + afterCodeGenerationCode
                                + "&client_id=" + mas.getClientId()
                                + "&client_secret=" + mas.getClientSecret()
                                + "&redirect_uri=" + "http://localhost:2001/" + "oauth2callback"
                                + "&grant_type=authorization_code")
                        .buildExchange();
            }
        };
    }

    static Consumer<Exchange> getTokenAndTokenTypeFromResponse() throws IOException, ParseException {
        return new Consumer<Exchange>() {
            @Override
            public void call(Exchange exc) throws Exception {
                HashMap<String, String> json = Util.parseSimpleJSONResponse(exc.getResponse());
                afterTokenGenerationToken = json.get("access_token");
                afterTokenGenerationTokenType = json.get("token_type");
            }
        };
    }

    public static Callable<Exchange> getMockUserinfoRequest() throws Exception {
        return new Callable<Exchange>() {
            @Override
            public Exchange call() throws Exception {
                return new Request.Builder()
                        .get(mas.getUserInfoEndpoint())
                        .header("Authorization", afterTokenGenerationTokenType + " " + afterTokenGenerationToken)
                        .header("User-Agent", Constants.USERAGENT)
                        .header(Header.ACCEPT, "application/json")
                        .buildExchange();
            }
        };
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

    @Before
    public void setUp() throws Exception{
        router = new HttpRouter();
        initOasi();
        initMas();
        initLoginMockParametersForJohn();
    }

    @Parameterized.Parameters(name = "{0}")
    public static Collection<Object[]> data() throws Exception {
        return Arrays.asList(new Object[][] {
        });
    }

    @Parameterized.Parameter
    public String testName;
    @Parameterized.Parameter(value = 1)
    public Runnable preprocessing;
    @Parameterized.Parameter(value = 2)
    public Callable<Exchange> preparedExchange;
    @Parameterized.Parameter(value = 3)
    public int expectedStatusCode;
    @Parameterized.Parameter(value = 4)
    public Consumer<Exchange> postprocessing;

    @Test
    public void test() throws Exception {
        preprocessing.run();
        exc = preparedExchange.call();
        OAuth2TestUtil.makeExchangeValid(exc);
        oasi.handleRequest(exc);
        Assert.assertEquals(expectedStatusCode,exc.getResponse().getStatusCode());
        postprocessing.call(exc);
    }

    protected static <T>Runnable runTest(final Class<T> caller, final String name){
        return new Runnable() {
            @Override
            public void run() {
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
                    Consumer<Exchange> postprocessing = (Consumer<Exchange>) params[4];

                    preprocessing.run();
                    Exchange exc = preparedExchange.call();
                    OAuth2TestUtil.makeExchangeValid(exc);
                    oasi.handleRequest(exc);
                    Assert.assertEquals(expectedStatusCode, exc.getResponse().getStatusCode());
                    postprocessing.call(exc);

                }catch(Exception e){
                    e.printStackTrace();
                }
            }
        };

    }

    private void initLoginMockParametersForJohn() {
        afterLoginMockParams = new HashMap<String, String>();
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
        oasi = new OAuth2AuthorizationServerInterceptor();
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
        ArrayList<ClaimList.Scope> scopes = new ArrayList<ClaimList.Scope>();
        ClaimList.Scope scope = new ClaimList.Scope("profile","username email");
        scopes.add(scope);
        cl.setScopes(scopes);
        oasi.setClaimList(cl);
    }

    private void setOasiClientList() {
        StaticClientList cl = new StaticClientList();
        ArrayList<Client> clients = new ArrayList<Client>();
        Client john2 = new Client("abc","def","http://localhost:2001/oauth2callback");
        clients.add(john2);
        cl.setClients(clients);
        oasi.setClientList(cl);
    }

    private void setOasiUserDataProvider() {
        StaticUserDataProvider udp = new StaticUserDataProvider();
        ArrayList<StaticUserDataProvider.User> users = new ArrayList<StaticUserDataProvider.User>();
        johnsUserData = new StaticUserDataProvider.User("john", "password");
        johnsUserData.getAttributes().put("email","e@mail.com");
        users.add(johnsUserData);
        udp.setUsers(users);
        oasi.setUserDataProvider(udp);
    }

}
