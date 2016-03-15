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
import org.jose4j.jwt.consumer.InvalidJwtException;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import javax.mail.internet.ParseException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.regex.Pattern;

import static org.junit.Assert.assertEquals;

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

    public static OAuth2AuthorizationServerInterceptorTest createAndSetUp() throws Exception {
        OAuth2AuthorizationServerInterceptorTest oasit = new OAuth2AuthorizationServerInterceptorTest();
        oasit.setUp();
        return oasit;
    }


    @Before
    public void setUp() throws Exception {
        router = new HttpRouter();
        initOasi();
        initMas();
        initLoginMockParametersForJohn();
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

    @Test
    public void testBadRequest() throws Exception{
        Exchange exc = getMockBadRequestExchange();
        oasi.handleRequest(exc);
        assertEquals(400,exc.getResponse().getStatusCode());
    }

    @Test
    public void testGoodAuthRequest() throws Exception {
        Exchange exc = getMockAuthRequestExchange();
        oasi.handleRequest(exc);
        assertEquals(307, exc.getResponse().getStatusCode());
        loginAsJohn();
    }

    @Test
    public void testGoodAuthOpenidRequest() throws Exception{
        Exchange exc = getMockAuthOpenidRequestExchange();
        oasi.handleRequest(exc);
        assertEquals(307, exc.getResponse().getStatusCode());
        loginAsJohnOpenid();
    }



    public Exchange getMockAuthRequestExchange() throws Exception {
        Exchange exc = new Request.Builder().get(mas.getLoginURL("123","http://localhost:2001/", "/")).buildExchange();
        exc.getRequest().getHeader().add("Cookie",cookieHeaderContent);
        makeExchangeValid(exc);
        return exc;
    }

    public Exchange getMockAuthOpenidRequestExchange() throws Exception{
        Exchange exc = getMockAuthRequestExchange();
        exc.getRequest().setUri(exc.getRequest().getUri()+ "&claims=" + OAuth2Util.urlencode(OAuth2TestUtil.getMockClaims()));
        exc.getRequest().setUri(exc.getRequest().getUri().replaceFirst(Pattern.quote("scope=profile"),"scope=" + OAuth2Util.urlencode("openid")));
        makeExchangeValid(exc);
        return exc;
    }

    private void makeExchangeValid(final Exchange exc) throws Exception {
        makeExchangeValid(new Callable<Exchange>() {
            @Override
            public Exchange call() throws Exception {
                return exc;
            }
        });
    }

    //TODO this is duplicated from RequestParameterizedTest -> this class will possibly also be an RequestParameterizedTest
    public static void makeExchangeValid(Callable<Exchange> exc) throws Exception {
        exc.call().setOriginalRequestUri(exc.call().getRequest().getUri());
        exc.call().setRule(new NullRule());
    }


    private void loginAsJohn(){
        SessionManager.Session session = oasi.getSessionManager().getSession("123");
        if(session == null){
            session = createMockSession();
        }
        session.preAuthorize("john", afterLoginMockParams);
    }

    private void loginAsJohnOpenid() throws IOException {
        loginAsJohn();
        oasi.getSessionManager().getSession("123").getUserAttributes().put(ParamNames.SCOPE,"openid");
        oasi.getSessionManager().getSession("123").getUserAttributes().put(ParamNames.CLAIMS, OAuth2TestUtil.getMockClaims());
        oasi.getSessionManager().getSession("123").getUserAttributes().put("consent","true");
    }

    private SessionManager.Session createMockSession() {
        Exchange exc = new Exchange(null);
        exc.setResponse(new Response.ResponseBuilder().build());
        exc.setRule(new NullRule());
        return oasi.getSessionManager().createSession(exc,"123");
    }

    @Test
    public void testGoodGrantedAuthCode() throws Exception{
        testGoodAuthRequest();
        Exchange exc = getMockEmptyEndpointRequest();

        oasi.handleRequest(exc);
        assertEquals(307,exc.getResponse().getStatusCode());
        getCodeFromResponse(exc);
    }

    @Test
    public void testGoodGrantedAuthCodeOpenid() throws Exception{
        testGoodAuthOpenidRequest();
        Exchange exc = getMockEmptyEndpointRequest();

        oasi.handleRequest(exc);
        assertEquals(307,exc.getResponse().getStatusCode());
        getCodeFromResponse(exc);
    }

    public Exchange getMockEmptyEndpointRequest() throws Exception {
        Exchange exc =  new Request.Builder().get("/").buildExchange();
        exc.getRequest().getHeader().add("Cookie",cookieHeaderContent);
        makeExchangeValid(exc);
        return exc;
    }

    private void getCodeFromResponse(Exchange exc) {
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

    public Exchange getMockTokenRequest() throws Exception {
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
        makeExchangeValid(exc);
        return exc;
    }

    @Test
    public void testGoodTokenRequest() throws Exception{
        testGoodGrantedAuthCode();
        Exchange exc = getMockTokenRequest();

        oasi.handleRequest(exc);
        assertEquals(200,exc.getResponse().getStatusCode());
        getTokenAndTokenTypeFromResponse(exc);
    }

    @Test
    public void testGoodTokenOpenidRequest() throws Exception{
        testGoodGrantedAuthCodeOpenid();
        Exchange exc = getMockTokenRequest();

        oasi.handleRequest(exc);
        assertEquals(200,exc.getResponse().getStatusCode());
        assertEquals(true,idTokenIsValid(exc.getResponse()));
        getTokenAndTokenTypeFromResponse(exc);
    }

    private boolean idTokenIsValid(Response response) throws IOException, ParseException {
        // TODO: currently only checks if signature is valid -> also check if requested claims are in it
        HashMap<String, String> json = Util.parseSimpleJSONResponse(response);
        try {
            oasi.getJwtGenerator().getClaimsFromSignedIdToken(json.get(ParamNames.ID_TOKEN), oasi.getIssuer(), "abc");
            return true;
        } catch (InvalidJwtException e) {
            return false;
        }
    }

    private void getTokenAndTokenTypeFromResponse(Exchange exc) throws IOException, ParseException {
        HashMap<String, String> json = Util.parseSimpleJSONResponse(exc.getResponse());
        afterTokenGenerationToken = json.get("access_token");
        afterTokenGenerationTokenType = json.get("token_type");
    }

    public Exchange getMockUserinfoRequest() throws Exception {
        Exchange exc = new Request.Builder()
                .get(mas.getUserInfoEndpoint())
                .header("Authorization", afterTokenGenerationTokenType + " " + afterTokenGenerationToken)
                .header("User-Agent", Constants.USERAGENT)
                .header(Header.ACCEPT, "application/json")
                .buildExchange();
        makeExchangeValid(exc);
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

    @Test
    public void testGoodUserinfoRequestOpenid() throws Exception{
        testGoodTokenOpenidRequest();
        Exchange exc = getMockUserinfoRequest();
        oasi.handleRequest(exc);
        assertEquals(200, exc.getResponse().getStatusCode());
        HashMap<String, String> json = Util.parseSimpleJSONResponse(exc.getResponse());
        assertEquals("e@mail.com",json.get("email"));
    }

    public Exchange getMockRevocationRequest() throws Exception {
        Exchange exc = new Request.Builder().post(mas.getRevocationEndpoint())
                .header(Header.CONTENT_TYPE, "application/x-www-form-urlencoded")
                .header(Header.USER_AGENT, Constants.USERAGENT)
                .body("token=" + afterTokenGenerationToken +"&client_id=" + mas.getClientId() + "&client_secret=" + mas.getClientSecret())
                .buildExchange();
        makeExchangeValid(exc);
        return exc;
    }

    @Test
    public void testGoodRevocationRequest() throws Exception{
        testGoodTokenRequest();
        Exchange exc = getMockRevocationRequest();
        oasi.handleRequest(exc);
        assertEquals(200, exc.getResponse().getStatusCode());
    }

    @After
    public void teardown() throws IOException {
    }

    public Exchange getMockBadRequestExchange() throws Exception {
        Exchange exc = new Request.Builder().get("/thisdoesntexist").buildExchange();
        makeExchangeValid(exc);
        return exc;
    }
}

