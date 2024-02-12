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

package com.predic8.membrane.core.interceptor.oauth2.client;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.predic8.membrane.core.HttpRouter;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.*;
import com.predic8.membrane.core.interceptor.*;
import com.predic8.membrane.core.interceptor.oauth2.OAuth2AnswerParameters;
import com.predic8.membrane.core.interceptor.oauth2.WellknownFile;
import com.predic8.membrane.core.interceptor.oauth2.authorizationservice.MembraneAuthorizationService;
import com.predic8.membrane.core.interceptor.oauth2client.LoginParameter;
import com.predic8.membrane.core.interceptor.oauth2client.OAuth2Resource2Interceptor;
import com.predic8.membrane.core.interceptor.oauth2client.RequireAuth;
import com.predic8.membrane.core.interceptor.session.InMemorySessionManager;
import com.predic8.membrane.core.lang.spel.functions.BuiltInFunctions;
import com.predic8.membrane.core.resolver.ResolverMap;
import com.predic8.membrane.core.rules.ServiceProxy;
import com.predic8.membrane.core.rules.ServiceProxyKey;
import com.predic8.membrane.core.transport.http.HttpClient;
import com.predic8.membrane.core.transport.http.client.ConnectionConfiguration;
import com.predic8.membrane.core.transport.http.client.HttpClientConfiguration;
import com.predic8.membrane.core.util.URI;
import com.predic8.membrane.core.util.URIFactory;
import com.predic8.membrane.core.util.URLParamUtil;
import org.intellij.lang.annotations.Language;
import org.jose4j.jwt.JwtClaims;
import org.jose4j.jwt.consumer.InvalidJwtException;
import org.jose4j.jwt.consumer.JwtConsumer;
import org.jose4j.jwt.consumer.JwtConsumerBuilder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.math.BigInteger;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.security.SecureRandom;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

import static com.predic8.membrane.core.http.MimeType.APPLICATION_JSON;
import static org.junit.jupiter.api.Assertions.*;

public abstract class OAuth2ResourceTest {

    Logger LOG = LoggerFactory.getLogger(OAuth2ResourceTest.class);

    int serverPort = 1337;
    private String serverHost = "localhost";
    private int clientPort = 31337;
    protected HttpRouter mockAuthServer;
    private HttpRouter oauth2Resource;

    private String getServerAddress(){
        return "http://"+serverHost + ":" + serverPort;
    }

    protected String getClientAddress(){
        return "http://"+serverHost + ":" + clientPort;
    }

    private final int limit = 500;
    protected ObjectMapper om = new ObjectMapper();

    Function<Exchange, Exchange> cookieHandlingHttpClient = cookieManager(httpClient());
    Function<Exchange, Exchange> cookieHandlingRedirectingHttpClient = handleRedirect(cookieHandlingHttpClient);

    @BeforeEach
    public void init() throws IOException {
        mockAuthServer = new HttpRouter();
        mockAuthServer.getTransport().setBacklog(10000);
        mockAuthServer.getTransport().setSocketTimeout(10000);
        mockAuthServer.setHotDeploy(false);
        mockAuthServer.getTransport().setConcurrentConnectionLimitPerIp(limit);
        mockAuthServer.getRuleManager().addProxyAndOpenPortIfNew(getMockAuthServiceProxy());
        mockAuthServer.start();

        oauth2Resource = new HttpRouter();
        oauth2Resource.getTransport().setBacklog(10000);
        oauth2Resource.getTransport().setSocketTimeout(10000);
        oauth2Resource.setHotDeploy(false);
        oauth2Resource.getTransport().setConcurrentConnectionLimitPerIp(limit);
        oauth2Resource.getRuleManager().addProxyAndOpenPortIfNew(getConfiguredOAuth2Resource());
        oauth2Resource.start();
    }

    @AfterEach
    public void done() {
        if (mockAuthServer != null)
            mockAuthServer.stop();
        if (oauth2Resource != null)
            oauth2Resource.stop();
    }

    @Test
    public void getOriginalRequest() throws Exception {
        Exchange excCallResource = new Request.Builder().get(getClientAddress() + "/init").buildExchange();

        excCallResource = cookieHandlingRedirectingHttpClient.apply(excCallResource);
        Map body2 = om.readValue(excCallResource.getResponse().getBodyAsStream(), Map.class);
        assertEquals("/init", body2.get("path"));
        assertEquals("", body2.get("body"));
        assertEquals("GET", body2.get("method"));
    }

    @Test
    public void postOriginalRequest() throws Exception {
        Exchange excCallResource = new Request.Builder().post(getClientAddress() + "/init").body("demobody").buildExchange();

        excCallResource = cookieHandlingRedirectingHttpClient.apply(excCallResource);
        Map body2 = om.readValue(excCallResource.getResponse().getBodyAsStream(), Map.class);
        assertEquals("/init", body2.get("path"));
        assertEquals("demobody", body2.get("body"));
        assertEquals("POST", body2.get("method"));
    }

    // this test also implicitly tests concurrency on oauth2resource
    @Test
    public void testUseRefreshTokenOnTokenExpiration() throws Exception {
        Exchange excCallResource = new Request.Builder().get(getClientAddress() + "/init").buildExchange();

        excCallResource = cookieHandlingRedirectingHttpClient.apply(excCallResource);
        Map body2 = om.readValue(excCallResource.getResponse().getBodyAsStream(), Map.class);
        assertEquals("/init", body2.get("path"));

        Set<String> accessTokens = new HashSet<>();
        List<Thread> threadList = new ArrayList<>();
        CountDownLatch cdl = new CountDownLatch(limit);
        for(int i = 0; i < limit; i++) {
            threadList.add(new Thread(() -> {
                try {
                    cdl.countDown();
                    cdl.await();
                    String uuid = UUID.randomUUID().toString();
                    Exchange excCallResource2 = new Request.Builder().get(getClientAddress() + "/" + uuid).buildExchange();
                    excCallResource2 = cookieHandlingRedirectingHttpClient.apply(excCallResource2);
                    Map body = om.readValue(excCallResource2.getResponse().getBodyAsStringDecoded(), Map.class);
                    String path = (String)body.get("path");
                    assertEquals("/" + uuid, path);
                    synchronized (accessTokens) {
                        accessTokens.add((String)body.get("accessToken"));
                    }
                }catch (Exception e){
                    e.printStackTrace();
                }
            }));

        }
        threadList.forEach(Thread::start);
        threadList.forEach(thread -> {
            try {
                thread.join();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        });
        synchronized (accessTokens) {
            assertEquals(accessTokens.size(), limit);
        }
    }


    @Test
    public void testStateAttack() throws Exception {
        AtomicReference<String> ref = new AtomicReference<>();
        AtomicInteger state = new AtomicInteger();
        // state 0: the attacker aborts the OAuth2 flow at the AS
        // state 1: the helpless user continues using the same link

        mockAuthServer.getTransport().getInterceptors().add(2, new AbstractInterceptor() {
            @Override
            public Outcome handleRequest(Exchange exc) throws Exception {
                if (state.get() == 0) {
                    ref.set(exc.getOriginalRequestUri());
                    state.set(1);
                    exc.setResponse(Response.internalServerError().body("").build());
                    return Outcome.RETURN;
                }

                return super.handleRequest(exc);
            }
        });


        Exchange excCallResource = new Request.Builder().get(getClientAddress() + "/malicious").buildExchange();
        LOG.debug("getting " + excCallResource.getDestinations().get(0));
        excCallResource = cookieHandlingRedirectingHttpClient.apply(excCallResource); // will be aborted

        cookie.clear(); // send the auth link to some helpless (other) user

        excCallResource = cookieHandlingRedirectingHttpClient.apply(new Request.Builder().get("http://localhost:1337" + ref.get()).buildExchange());

        assertEquals(400, excCallResource.getResponse().getStatusCode());

        assertTrue(excCallResource.getResponse().getBodyAsStringDecoded().contains("CSRF"));
    }

    @Test
    public void testCSRFProblem() throws Exception {
        AtomicBoolean blocked = new AtomicBoolean(true);
        mockAuthServer.getTransport().getInterceptors().add(2, new AbstractInterceptor() {
            @Override
            public Outcome handleRequest(Exchange exc) throws Exception {
                if (blocked.get()) {
                    exc.setResponse(Response.ok("Login aborted").build());
                    return Outcome.RETURN;
                }
                return Outcome.CONTINUE;
            }
        });

        // hit the client, do not continue at AS with login
        Exchange excCallResource = new Request.Builder().get(getClientAddress() + "/init" + 0).buildExchange();
        excCallResource = cookieHandlingRedirectingHttpClient.apply(excCallResource);

        assertEquals(200, excCallResource.getResponse().getStatusCode());
        assertTrue(excCallResource.getResponse().getBodyAsStringDecoded().contains("Login aborted"));

        blocked.set(false);

        // hit client again, login
        excCallResource = new Request.Builder().get(getClientAddress() + "/init" + 1).buildExchange();
        excCallResource = cookieHandlingRedirectingHttpClient.apply(excCallResource);

        // works
        assertEquals(200, excCallResource.getResponse().getStatusCode());
        assertTrue(excCallResource.getResponse().getBodyAsStringDecoded().contains("/init1"));
    }

    @Test
    public void logout() throws Exception {
        cookieHandlingRedirectingHttpClient.apply(new Request.Builder()
                .get(getClientAddress() + "/init").buildExchange());

        var ili = cookieHandlingRedirectingHttpClient.apply(new Request.Builder().get(getClientAddress() + "/is-logged-in").buildExchange());

        assertTrue(ili.getResponse().getBodyAsStringDecoded().contains("true"));

        // call to /logout uses cookieHandlingHttpClient: *NOT* following the redirect (which would auto-login again)
        cookieHandlingHttpClient.apply(new Request.Builder()
                .get(getClientAddress() + "/logout").buildExchange());

        ili = cookieHandlingRedirectingHttpClient.apply(new Request.Builder().get(getClientAddress() + "/is-logged-in").buildExchange());

        assertTrue(ili.getResponse().getBodyAsStringDecoded().contains("false"));
    }

//    @Test
//    public void requestAuth() throws Exception {
//        Exchange exc = new Request.Builder().get(getClientAddress() + "/init").buildExchange();
//        cookieHandlingRedirectingHttpClient.apply(exc);
//
//        System.out.println();
//    }

    @Test
    public void loginParams() throws Exception {
        Exchange exc = new Request.Builder().get(getClientAddress() + "/init?login_hint=def&illegal=true").buildExchange();
        cookieHandlingHttpClient.apply(exc);

        String location = exc.getResponse().getHeader().getFirstValue("Location");

        URI jUri = new URIFactory().create(location);
        String q = jUri.getRawQuery();

        var params = URLParamUtil.parseQueryString(q, URLParamUtil.DuplicateKeyOrInvalidFormStrategy.ERROR);

        assertTrue(params.containsKey("foo"));
        assertEquals("bar", params.get("foo"));
        assertTrue(params.containsKey("login_hint"));
        assertEquals("def", params.get("login_hint"));
        assertFalse(params.containsKey("illegal"));
    }


    Map<String, Map<String, String>> cookie = new HashMap<>();

    // this implementation does NOT implement a correct cookie manager, but fulfills this test's requirements
    private Function<Exchange, Exchange> cookieManager(Function<Exchange, Exchange> consumer) {
        return exc -> {
            String domain = null;
            try {
                domain = new URL(exc.getDestinations().get(0)).getHost();
            } catch (MalformedURLException e) {
                throw new RuntimeException(e);
            }
            Map<String, String> cookies;
            synchronized (cookie) {
                cookies = cookie.get(domain);
            }
            if (cookies != null)
                synchronized (cookies) {
                    Exchange finalExc = exc;
                    cookies.forEach((k, v) -> finalExc.getRequest().getHeader().add("Cookie", k + "=" + v));
                }
            exc = consumer.apply(exc);

            for (HeaderField headerField : exc.getResponse().getHeader().getValues(new HeaderName("Set-Cookie"))) {
                LOG.debug("from " + domain + " got Set-Cookie: " + headerField.getValue());

                String value = headerField.getValue().substring(0, headerField.getValue().indexOf(";"));
                boolean expired = headerField.getValue().contains("1970");

                String key = value.substring(0, value.indexOf("=")).trim();
                value = value.substring(value.indexOf("=") + 1).trim();

                if (cookies == null) {
                    cookies = new HashMap<>();
                    synchronized (cookie) {
                        // recheck whether there are still no cookies yet
                        Map<String, String> cookies2 = cookie.get(domain);
                        if (cookies2 != null)
                            cookies = cookies2;
                        else
                            cookie.put(domain, cookies);
                    }
                }

                if (expired) {
                    LOG.debug("removing cookie.");
                    synchronized (cookies) {
                        cookies.remove(key);
                    }
                } else {
                    try {
                        JwtConsumer jwtc = new JwtConsumerBuilder()
                                .setSkipSignatureVerification()
                                .build();

                        String v = headerField.getValue();
                        JwtClaims claims = jwtc.processToClaims(v.substring(0, v.indexOf("=")));
                        for (Map.Entry<String, Object> entry : claims.getClaimsMap().entrySet()) {
                            LOG.debug(entry.getKey() + ": " + entry.getValue());
                        }
                    } catch (InvalidJwtException e) {
                        //ignore
                    }

                    synchronized (cookies) {
                        cookies.put(key, value);
                    }
                }
            }

            return exc;
        };
    }

    private Function<Exchange, Exchange> handleRedirect(Function<Exchange, Exchange> consumer) {
        return exc -> {
            ArrayList<Object> urls = new ArrayList<>();
            while (true) {
                if (urls.size() == 19)
                    throw new RuntimeException("Too many redirects: " + urls);
                exc = consumer.apply(exc);

                int statusCode = exc.getResponse().getStatusCode();
                String location = exc.getResponse().getHeader().getFirstValue("Location");
                if (statusCode < 300 || statusCode >= 400 || location == null)
                    break;
                if (!location.contains("://"))
                    location = ResolverMap.combine(exc.getDestinations().get(0), location);
                urls.add(location);
                try {
                    exc = new Request.Builder().get(location).buildExchange();
                } catch (URISyntaxException e) {
                    throw new RuntimeException(e);
                }

                LOG.debug("redirected to " + exc.getDestinations().get(0));
            }
            return exc;
        };
    }

    private Function<Exchange, Exchange> httpClient() {
        HttpClientConfiguration configuration = new HttpClientConfiguration();
        ConnectionConfiguration connection = new ConnectionConfiguration();
        connection.setTimeout(10000);
        configuration.setConnection(connection);
        return new Function<>() {
            HttpClient httpClient = new HttpClient(configuration);

            @Override
            public Exchange apply(Exchange exchange) {
                try {
                    return httpClient.call(exchange);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
        };
    }

    private ServiceProxy getMockAuthServiceProxy() throws IOException {

        ServiceProxy sp = new ServiceProxy(new ServiceProxyKey(serverPort),null,99999);



        WellknownFile wkf = new WellknownFile();

        wkf.setIssuer(getServerAddress());
        wkf.setAuthorizationEndpoint(getServerAddress() + "/auth");
        wkf.setTokenEndpoint(getServerAddress() + "/token");
        wkf.setUserinfoEndpoint(getServerAddress() + "/userinfo");
        wkf.setRevocationEndpoint(getServerAddress() + "/revoke");
        wkf.setJwksUri(getServerAddress() + "/certs");
        wkf.setSupportedResponseTypes("code token");
        wkf.setSupportedSubjectType("public");
        wkf.setSupportedIdTokenSigningAlgValues("RS256");
        wkf.setSupportedScopes("openid email profile");
        wkf.setSupportedTokenEndpointAuthMethods("client_secret_post");
        wkf.setSupportedClaims("sub email username");
        wkf.init(new HttpRouter());

        sp.getInterceptors().add(new AbstractInterceptor(){

            SecureRandom rand = new SecureRandom();

            @Override
            public synchronized Outcome handleRequest(Exchange exc) throws Exception {
                if(exc.getRequestURI().endsWith("/.well-known/openid-configuration")){
                    exc.setResponse(Response.ok(wkf.getWellknown()).build());
                }else if(exc.getRequestURI().startsWith("/auth?")){
                    Map<String, String> params = URLParamUtil.getParams(new URIFactory(), exc, URLParamUtil.DuplicateKeyOrInvalidFormStrategy.ERROR);
                    exc.setResponse(Response.redirect(getClientAddress()+"/oauth2callback?code=1234&state=" + params.get("state"),false).build());
                }else if(exc.getRequestURI().startsWith("/token")){
                    ObjectMapper om = new ObjectMapper();
                    Map<String,String> res = new HashMap<>();
                    res.put("access_token", new BigInteger(130, rand).toString(32));
                    res.put("token_type","bearer");
                    res.put("expires_in","1");
                    res.put("refresh_token",new BigInteger(130, rand).toString(32));
                    exc.setResponse(Response.ok(om.writeValueAsString(res)).contentType(APPLICATION_JSON).build());

                }else if(exc.getRequestURI().startsWith("/userinfo")){
                    ObjectMapper om = new ObjectMapper();
                    Map<String,String> res = new HashMap<>();
                    res.put("username","dummy");
                    exc.setResponse(Response.ok(om.writeValueAsString(res)).contentType(APPLICATION_JSON).build());
                }

                if(exc.getResponse() == null)
                    exc.setResponse(Response.notFound().build());
                return Outcome.RETURN;
            }
        });

        return sp;
    }

    private ServiceProxy getConfiguredOAuth2Resource() {

        ServiceProxy sp = new ServiceProxy(new ServiceProxyKey(clientPort),null,99999);

        OAuth2Resource2Interceptor oAuth2ResourceInterceptor = new OAuth2Resource2Interceptor();
        configureSessionManager(oAuth2ResourceInterceptor);
        MembraneAuthorizationService auth = new MembraneAuthorizationService();
        auth.setSrc(getServerAddress());
        auth.setClientId("2343243242");
        auth.setClientSecret("3423233123123");
        auth.setScope("openid profile");
        oAuth2ResourceInterceptor.setAuthService(auth);

        oAuth2ResourceInterceptor.setLogoutUrl("/logout");

        var withOutValue = new LoginParameter();
        withOutValue.setName("login_hint");

        var withValue = new LoginParameter();
        withValue.setName("foo");
        withValue.setValue("bar");

        oAuth2ResourceInterceptor.setLoginParameters(List.of(
                withOutValue,
                withValue
        ));

        var aud = new RequireAuth();
        aud.setExpectedAud("asdf");
        aud.setOauth2(oAuth2ResourceInterceptor);


        sp.getInterceptors().add(new AbstractInterceptor() {
            @Override
            public Outcome handleRequest(Exchange exc) throws Exception {
                if (!exc.getRequest().getUri().contains("is-logged-in"))
                    return Outcome.CONTINUE;

                boolean isLoggedIn = oAuth2ResourceInterceptor.getSessionManager().getSession(exc).isVerified();

                exc.setResponse(Response.ok("{\"success\":"+isLoggedIn+"}").header(Header.CONTENT_TYPE, APPLICATION_JSON).build());
                return Outcome.RETURN;
            }
        });
        sp.getInterceptors().add(oAuth2ResourceInterceptor);
        sp.getInterceptors().add(new AbstractInterceptor(){
            @Override
            public Outcome handleRequest(Exchange exc) throws Exception {
                OAuth2AnswerParameters answer = OAuth2AnswerParameters.deserialize(String.valueOf(exc.getProperty(Exchange.OAUTH2)));
                String accessToken = answer.getAccessToken();
                Map<String, String> body = Map.of(
                        "accessToken", accessToken,
                        "path", exc.getRequestURI(),
                        "method", exc.getRequest().getMethod(),
                        "body", exc.getRequest().getBodyAsStringDecoded()
                );

                exc.setResponse(Response.ok(om.writeValueAsString(body)).build());
                return Outcome.RETURN;
            }
        });
        return sp;
    }

    protected abstract void configureSessionManager(OAuth2Resource2Interceptor oauth2);
}
