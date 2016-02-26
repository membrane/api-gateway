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
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

import static org.junit.Assert.*;

public class OAuth2AuthorizationServerInterceptorTest {

    Router router;
    OAuth2AuthorizationServerInterceptor oasi;
    MembraneAuthorizationService mas;
    StaticUserDataProvider.User johnsUserData;
    Map<String,String> afterLoginMockParams;
    String afterCodeGenerationCode;
    String afterTokenGenerationToken;
    String afterTokenGenerationTokenType;

    String cookieHeaderContent = "SESSIONID=123";


    @Before
    public void setUp() throws Exception {
        router = new HttpRouter();

        oasi = new OAuth2AuthorizationServerInterceptor();

        StaticUserDataProvider udp = new StaticUserDataProvider();
        ArrayList<StaticUserDataProvider.User> users = new ArrayList<StaticUserDataProvider.User>();
        johnsUserData = new StaticUserDataProvider.User("john", "password");
        users.add(johnsUserData);
        udp.setUsers(users);
        oasi.setUserDataProvider(udp);

        StaticClientList cl = new StaticClientList();
        ArrayList<Client> clients = new ArrayList<Client>();
        Client john2 = new Client("abc","def","http://localhost:2001/oauth2callback");
        clients.add(john2);
        cl.setClients(clients);
        oasi.setClientList(cl);

        ScopeList sl = new ScopeList();
        ArrayList<ScopeList.Scope> scopes = new ArrayList<ScopeList.Scope>();
        ScopeList.Scope scope = new ScopeList.Scope("profile","username email");
        scopes.add(scope);
        sl.setScopes(scopes);
        oasi.setScopeList(sl);

        oasi.setLocation("src\\test\\resources\\oauth2\\loginDialog\\dialog");
        oasi.setPath("/login/");

        oasi.init(router);

        mas = new MembraneAuthorizationService();
        mas.setClientId("abc");
        mas.setClientSecret("def");
        mas.setSrc(System.getProperty("user.dir") + "\\src\\test\\resources\\oauth2");

        mas.init(router);

        afterLoginMockParams = new HashMap<String, String>();
        afterLoginMockParams.put("response_type","code");
        afterLoginMockParams.put("scope","profile");
        afterLoginMockParams.put("redirect_uri","http://localhost:2001/oauth2callback");
        afterLoginMockParams.put("state", "security_token=123&url=/");
        afterLoginMockParams.put("client_id", "abc");
        afterLoginMockParams.putAll(johnsUserData.getAttributes());
    }

    @Test
    public void testGoodAuthRequest() throws Exception {
        Exchange exc = mockGetMethodExchange(mas.getLoginURL("123","http://localhost:2001/", "/"));

        oasi.handleRequest(exc);
        assertEquals(307, exc.getResponse().getStatusCode()); // user gets redirected to login when successful
    }

    private void loginAsJohn(){
        SessionManager.Session session = oasi.getSessionManager().getSession("123");
        if(session == null){
            session = createMockSession();
        }
        session.preAuthorize("john", afterLoginMockParams);
    }

    private SessionManager.Session createMockSession() {
        Exchange exc = new Exchange(null);
        exc.setResponse(new Response.ResponseBuilder().build());
        exc.setRule(new NullRule());
        return oasi.getSessionManager().createSession(exc,"123");
    }

    private Exchange mockGetMethodExchange(String uri) throws URISyntaxException {
        Exchange exc = new Request.Builder().get(uri).buildExchange();
        exc.getRequest().getHeader().add("Cookie",cookieHeaderContent);
        exc.setOriginalRequestUri(exc.getRequest().getUri());
        exc.setRule(new NullRule());
        return exc;
    }

    @Test
    public void testGoodGrantedAuthCode() throws Exception{
        testGoodAuthRequest();
        loginAsJohn();
        Exchange exc = mockGetMethodExchange("/");

        oasi.handleRequest(exc);
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
        assertEquals(307,exc.getResponse().getStatusCode());

    }

    private Exchange getMockTokenRequest() throws URISyntaxException {
        Exchange exc = new Request.Builder()
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
        exc.setOriginalRequestUri(exc.getRequest().getUri());
        exc.setRule(new NullRule());
        return exc;
    }

    @Test
    public void testGoodTokenRequest() throws Exception{
        testGoodGrantedAuthCode();
        Exchange exc = getMockTokenRequest();

        oasi.handleRequest(exc);
        assertEquals(200,exc.getResponse().getStatusCode());
        HashMap<String, String> json = Util.parseSimpleJSONResponse(exc.getResponse());
        afterTokenGenerationToken = json.get("access_token");
        afterTokenGenerationTokenType = json.get("token_type");
    }

    public Exchange getMockUserinfoRequest() throws URISyntaxException {
        Exchange exc = new Request.Builder()
                .get(mas.getUserInfoEndpoint())
                .header("Authorization", afterTokenGenerationTokenType + " " + afterTokenGenerationToken)
                .header("User-Agent", Constants.USERAGENT)
                .header(Header.ACCEPT, "application/json")
                .buildExchange();
        exc.setOriginalRequestUri(exc.getRequest().getUri());
        exc.setRule(new NullRule());
        return exc;
    }

    @Test
    public void testGoodUserinfoRequest() throws Exception{
        testGoodTokenRequest();
        Exchange exc = getMockUserinfoRequest();
        oasi.handleRequest(exc);
        assertEquals(200, exc.getResponse().getStatusCode());

        HashMap<String, String> json = Util.parseSimpleJSONResponse(exc.getResponse());
        assertEquals("john",json.get("username"));
    }

    @After
    public void teardown() throws IOException {
    }
}

