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
import com.predic8.membrane.core.http.Response;
import com.predic8.membrane.core.interceptor.authentication.session.SessionManager;
import com.predic8.membrane.core.interceptor.authentication.session.StaticUserDataProvider;
import com.predic8.membrane.core.rules.NullRule;
import com.predic8.membrane.core.util.Util;
import com.predic8.membrane.core.util.functionalInterfaces.Consumer;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import javax.mail.internet.ParseException;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.regex.Pattern;

import static org.junit.Assert.assertEquals;

@RunWith(Parameterized.class)
public class OAuth2AuthorizationServerInterceptorNewTest {

    static Router router;
    static Exchange exc;
    static OAuth2AuthorizationServerInterceptor oasi;
    static MembraneAuthorizationService mas;
    static StaticUserDataProvider.User johnsUserData;
    static Map<String,String> afterLoginMockParams;
    static String afterCodeGenerationCode;
    static String afterTokenGenerationToken;
    static String afterTokenGenerationTokenType;

    public static String sessionId = "123";
    public static String cookieHeaderContent = "SESSIONID=" + sessionId;

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
                testBadRequest(),
                testGoodAuthRequest(),
                testGoodGrantedAuthCode(),
                testGoodTokenRequest(),
                testGoodUserinfoRequest(),
                testGoodRevocationRequest()
        });
    }

    private static Object[] testGoodRevocationRequest() throws Exception{
        return new Object[]{"testGoodRevocationRequest", runTest("testGoodTokenRequest"),getMockRevocationRequest(),200,noPostprocessing()};
    }

    private static Object[] testGoodUserinfoRequest() throws Exception{
        return new Object[]{"testGoodUserinfoRequest", runTest("testGoodTokenRequest"),getMockUserinfoRequest(),200,userinfoRequestPostprocessing()};
    }

    private static Object[] testGoodTokenRequest() throws Exception{
        return new Object[]{"testGoodTokenRequest",runTest("testGoodGrantedAuthCode"),getMockTokenRequest(),200, getTokenAndTokenTypeFromResponse()};
    }

    private static Object[] testGoodGrantedAuthCode() throws Exception {
        return new Object[]{"testGoodGrantedAuthCode",runTest("testGoodAuthRequest"), getMockEmptyEndpointRequest(), 307, getCodeFromResponse()};
    }

    private static Object[] testGoodAuthRequest() throws Exception {
        return new Object[]{"testGoodAuthRequest", noPreprocessing(), getMockAuthRequestExchange(),307, loginAsJohn()};
    }

    private static Object[] testBadRequest() throws Exception {
        return new Object[]{"testBadRequest", noPreprocessing(), getMockBadRequestExchange(),400, noPostprocessing()};
    }

    private static Consumer<Exchange> noPostprocessing() {
        return new Consumer<Exchange>() {
            @Override
            public void call(Exchange exchange) {
                return;
            }
        };
    }

    private static Runnable noPreprocessing() {
        return new Runnable() {
            @Override
            public void run() {
                return;
            }
        };
    }

    public static Callable<Exchange> getMockBadRequestExchange() throws Exception {
        return new Callable<Exchange>() {
            @Override
            public Exchange call() throws Exception {
                return new Request.Builder().get("/thisdoesntexist").buildExchange();
            }
        };
    }

    public static Callable<Exchange> getMockAuthRequestExchange() throws Exception {
        return new Callable<Exchange>() {
            @Override
            public Exchange call() throws Exception {
                Exchange exc = new Request.Builder().get(mas.getLoginURL("123","http://localhost:2001/", "/")).buildExchange();
                exc.getRequest().getHeader().add("Cookie",cookieHeaderContent);
                return exc;
            }
        };
    }

    public static Consumer<Exchange> loginAsJohn(){
        return new Consumer<Exchange>() {
            @Override
            public void call(Exchange exchange) {
                SessionManager.Session session = oasi.getSessionManager().getSession("123");
                if(session == null){
                    session = createMockSession();
                }
                session.preAuthorize("john", afterLoginMockParams);
            }
        };
    }

    public static Callable<Exchange> getMockEmptyEndpointRequest() throws Exception {
        return new Callable<Exchange>() {
            @Override
            public Exchange call() throws Exception {
                Exchange exc =  new Request.Builder().get("/").buildExchange();
                exc.getRequest().getHeader().add("Cookie",cookieHeaderContent);
                return exc;
            }
        };
    }

    private static Consumer<Exchange> getCodeFromResponse() {
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

    private static Consumer<Exchange> getTokenAndTokenTypeFromResponse() throws IOException, ParseException {
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

    private static Consumer<Exchange> userinfoRequestPostprocessing() throws IOException, ParseException {
        return new Consumer<Exchange>() {
            @Override
            public void call(Exchange exchange) throws Exception {
                HashMap<String, String> json = Util.parseSimpleJSONResponse(exc.getResponse());
                assertEquals("john",json.get("username"));
            }
        };
    }

    public static Callable<Exchange> getMockRevocationRequest() throws Exception {
        return new Callable<Exchange>() {
            @Override
            public Exchange call() throws Exception {
                return new Request.Builder().post(mas.getRevocationEndpoint())
                        .header(Header.CONTENT_TYPE, "application/x-www-form-urlencoded")
                        .header(Header.USER_AGENT, Constants.USERAGENT)
                        .body("token=" + afterTokenGenerationToken +"&client_id=" + mas.getClientId() + "&client_secret=" + mas.getClientSecret())
                        .buildExchange();
            }
        };
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

    private static Runnable runTest(final String name){
        return new Runnable() {
            @Override
            public void run() {
                try {
                    List<Object[]> allParams = (List<Object[]>) data();
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
        mas.setSrc(System.getProperty("user.dir") + "\\src\\test\\resources\\oauth2");
        mas.init(router);
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
        oasi.setLocation("src\\test\\resources\\oauth2\\loginDialog\\dialog");
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

    private static SessionManager.Session createMockSession() {
        Exchange exc = new Exchange(null);
        exc.setResponse(new Response.ResponseBuilder().build());
        exc.setRule(new NullRule());
        return oasi.getSessionManager().createSession(exc,"123");
    }
}
