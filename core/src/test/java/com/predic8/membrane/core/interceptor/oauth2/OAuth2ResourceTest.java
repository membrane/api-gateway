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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableMap;
import com.predic8.membrane.core.HttpRouter;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.HeaderField;
import com.predic8.membrane.core.http.HeaderName;
import com.predic8.membrane.core.http.Request;
import com.predic8.membrane.core.http.Response;
import com.predic8.membrane.core.http.cookie.CookieSupport;
import com.predic8.membrane.core.http.cookie.Cookies;
import com.predic8.membrane.core.http.cookie.MimeHeaders;
import com.predic8.membrane.core.http.cookie.ServerCookie;
import com.predic8.membrane.core.interceptor.*;
import com.predic8.membrane.core.interceptor.oauth2.authorizationservice.MembraneAuthorizationService;
import com.predic8.membrane.core.interceptor.oauth2client.OAuth2Resource2Interceptor;
import com.predic8.membrane.core.resolver.ResolverMap;
import com.predic8.membrane.core.rules.ServiceProxy;
import com.predic8.membrane.core.rules.ServiceProxyKey;
import com.predic8.membrane.core.transport.http.HttpClient;
import com.predic8.membrane.core.util.URIFactory;
import com.predic8.membrane.core.util.URLParamUtil;
import com.predic8.membrane.core.util.URLUtil;
import com.predic8.membrane.core.util.functionalInterfaces.Consumer;
import com.predic8.membrane.core.util.functionalInterfaces.Function;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.math.BigInteger;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.security.SecureRandom;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class OAuth2ResourceTest {

    int serverPort = 1337;
    private String serverHost = "localhost";
    private int clientPort = 31337;
    private HttpRouter mockAuthServer;
    private HttpRouter oauth2Resource;

    private String getServerAddress(){
        return "http://"+serverHost + ":" + serverPort;
    }

    private String getClientAddress(){
        return "http://"+serverHost + ":" + clientPort;
    }

    private final int limit = 1000;
    private ObjectMapper om = new ObjectMapper();

    Function<Exchange, Exchange> cookieHandlingRedirectingHttpClient = handleRedirect(cookieManager(httpClient()));

    @Before
    public void init() throws IOException {
        mockAuthServer = new HttpRouter();
        mockAuthServer.setHotDeploy(false);
        mockAuthServer.getTransport().setConcurrentConnectionLimitPerIp(limit);
        mockAuthServer.getRuleManager().addProxyAndOpenPortIfNew(getMockAuthServiceProxy());
        mockAuthServer.start();

        oauth2Resource = new HttpRouter();
        oauth2Resource.setHotDeploy(false);
        oauth2Resource.getTransport().setConcurrentConnectionLimitPerIp(limit);
        oauth2Resource.getRuleManager().addProxyAndOpenPortIfNew(getConfiguredOAuth2Resource());
        oauth2Resource.start();
    }

    @After
    public void done() {
        if (mockAuthServer != null)
            mockAuthServer.stop();
        if (oauth2Resource != null)
            oauth2Resource.stop();
    }

    @Test
    public void getOriginalRequest() throws Exception {
        Exchange excCallResource = new Request.Builder().get(getClientAddress() + "/init").buildExchange();

        excCallResource = cookieHandlingRedirectingHttpClient.call(excCallResource);
        Map body2 = om.readValue(excCallResource.getResponse().getBodyAsStream(), Map.class);
        Assert.assertEquals("/init", (String) body2.get("path"));
        Assert.assertEquals("", (String) body2.get("body"));
        Assert.assertEquals("GET", (String) body2.get("method"));
    }

    @Test
    public void postOriginalRequest() throws Exception {
        Exchange excCallResource = new Request.Builder().post(getClientAddress() + "/init").body("demobody").buildExchange();

        excCallResource = cookieHandlingRedirectingHttpClient.call(excCallResource);
        Map body2 = om.readValue(excCallResource.getResponse().getBodyAsStream(), Map.class);
        Assert.assertEquals("/init", (String) body2.get("path"));
        Assert.assertEquals("demobody", (String) body2.get("body"));
        Assert.assertEquals("POST", (String) body2.get("method"));
    }

    // this test also implicitly tests concurrency on oauth2resource
    @Test
    public void testUseRefreshTokenOnTokenExpiration() throws Exception {
        Exchange excCallResource = new Request.Builder().get(getClientAddress() + "/init").buildExchange();

        excCallResource = cookieHandlingRedirectingHttpClient.call(excCallResource);
        Map body2 = om.readValue(excCallResource.getResponse().getBodyAsStream(), Map.class);
        Assert.assertEquals("/init", (String) body2.get("path"));

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
                    excCallResource2 = cookieHandlingRedirectingHttpClient.call(excCallResource2);
                    Map body = om.readValue(excCallResource2.getResponse().getBodyAsStringDecoded(), Map.class);
                    String path = (String)body.get("path");
                    Assert.assertEquals("/" + uuid, path);
                    synchronized (accessTokens) {
                        accessTokens.add((String)body.get("accessToken"));
                    }
                }catch (Exception e){
                    e.printStackTrace();
                }
            }));

        }
        threadList.forEach(thread -> thread.start());
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

    // this implementation does NOT implement a correct cookie manager, but fulfills this test's requirements
    private Function<Exchange, Exchange> cookieManager(Function<Exchange, Exchange> consumer) {
        return new Function<Exchange, Exchange>() {
            Map<String, Map<String, String>> cookie = new HashMap<>();

            @Override
            public Exchange call(Exchange exc) {
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
                        cookies.forEach((k, v) -> finalExc.getRequest().getHeader().setValue("Cookie", k + "=" + v));
                    }
                exc = consumer.call(exc);

                for (HeaderField headerField : exc.getResponse().getHeader().getValues(new HeaderName("Set-Cookie"))) {
                    String value = headerField.getValue().substring(0, headerField.getValue().indexOf(";"));
                    boolean expired = headerField.getValue().contains("1970");

                    String key = value.substring(0, value.indexOf("=")).trim();
                    value = value.substring(value.indexOf("=")+1).trim();

                    if (cookies == null) {
                        cookies = new HashMap<>();
                        synchronized (this.cookie) {
                            // recheck whether there are still no cookies yet
                            Map<String, String> cookies2 = this.cookie.get(domain);
                            if (cookies2 != null)
                                cookies = cookies2;
                            else
                                this.cookie.put(domain, cookies);
                        }
                    }

                    if (expired) {
                        synchronized (cookies) {
                            cookies.remove(key);
                        }
                    } else {
                        synchronized (cookies) {
                            cookies.put(key, value);
                        }
                    }
                }

                return exc;
            }
        };
    }

    private Function<Exchange, Exchange> handleRedirect(Function<Exchange, Exchange> consumer) {
        return new Function<Exchange, Exchange>() {
            @Override
            public Exchange call(Exchange exc) {
                ArrayList urls = new ArrayList<>();
                while (true) {
                    if (urls.size() == 19)
                        throw new RuntimeException("Too many redirects: " + urls);
                    exc = consumer.call(exc);

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
                }
                return exc;
            }
        };
    }

    private Function<Exchange, Exchange> httpClient() {
        return new Function<Exchange, Exchange>() {
            HttpClient httpClient = new HttpClient();

            @Override
            public Exchange call(Exchange exchange) {
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
                    Map<String, String> params = URLParamUtil.getParams(new URIFactory(), exc);
                    exc.setResponse(Response.redirect(getClientAddress()+"/oauth2callback?code=1234&state=" + params.get("state"),false).build());
                }else if(exc.getRequestURI().startsWith("/token")){
                    ObjectMapper om = new ObjectMapper();
                    Map<String,String> res = new HashMap<>();
                    res.put("access_token",new BigInteger(130, rand).toString(32));
                    res.put("token_type","bearer");
                    res.put("expires_in","1");
                    res.put("refresh_token",new BigInteger(130, rand).toString(32));
                    exc.setResponse(Response.ok(om.writeValueAsString(res)).contentType("application/json").build());

                }else if(exc.getRequestURI().startsWith("/userinfo")){
                    ObjectMapper om = new ObjectMapper();
                    Map<String,String> res = new HashMap<>();
                    res.put("username","dummy");
                    exc.setResponse(Response.ok(om.writeValueAsString(res)).contentType("application/json").build());
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
        MembraneAuthorizationService auth = new MembraneAuthorizationService();
        auth.setSrc(getServerAddress());
        auth.setClientId("2343243242");
        auth.setClientSecret("3423233123123");
        auth.setScope("openid profile");
        oAuth2ResourceInterceptor.setAuthService(auth);


        sp.getInterceptors().add(oAuth2ResourceInterceptor);
        sp.getInterceptors().add(new AbstractInterceptor(){
            @Override
            public Outcome handleRequest(Exchange exc) throws Exception {
                OAuth2AnswerParameters answer = OAuth2AnswerParameters.deserialize(String.valueOf(exc.getProperty(Exchange.OAUTH2)));
                String accessToken = answer.getAccessToken();
                Map body = ImmutableMap.of(
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
}
