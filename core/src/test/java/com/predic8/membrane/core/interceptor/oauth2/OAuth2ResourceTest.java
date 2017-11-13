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
import com.predic8.membrane.core.HttpRouter;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.Request;
import com.predic8.membrane.core.http.Response;
import com.predic8.membrane.core.interceptor.AbstractInterceptor;
import com.predic8.membrane.core.interceptor.CountInterceptor;
import com.predic8.membrane.core.interceptor.MockInterceptor;
import com.predic8.membrane.core.interceptor.Outcome;
import com.predic8.membrane.core.interceptor.oauth2.authorizationservice.MembraneAuthorizationService;
import com.predic8.membrane.core.rules.ServiceProxy;
import com.predic8.membrane.core.rules.ServiceProxyKey;
import com.predic8.membrane.core.transport.http.HttpClient;
import com.predic8.membrane.core.util.URIFactory;
import com.predic8.membrane.core.util.URLParamUtil;
import org.junit.Test;

import java.io.IOException;
import java.math.BigInteger;
import java.security.SecureRandom;
import java.util.*;
import java.util.concurrent.CountDownLatch;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class OAuth2ResourceTest {

    int serverPort = 1337;
    private String serverHost = "localhost";
    private int clientPort = 31337;

    private String getServerAddress(){
        return "http://"+serverHost + ":" + serverPort;
    }

    private String getClientAddress(){
        return "http://"+serverHost + ":" + clientPort;
    }

    // this test also implicitly tests concurrency on oauth2resource
    @Test
    public void testUseRefreshTokenOnTokenExpiration() throws Exception {
        HttpRouter mockAuthServer = new HttpRouter();
        mockAuthServer.getRuleManager().addProxyAndOpenPortIfNew(getMockAuthServiceProxy());
        mockAuthServer.init();

        HttpRouter oauth2Resource = new HttpRouter();
        oauth2Resource.getRuleManager().addProxyAndOpenPortIfNew(getConfiguredOAuth2Resource());
        oauth2Resource.init();

        HttpClient httpClient = new HttpClient();
        Exchange excCallResource = new Request.Builder().get(getClientAddress()).buildExchange();
        excCallResource = httpClient.call(excCallResource);
        String cookie = excCallResource.getResponse().getHeader().getFirstValue("Set-Cookie");

        Exchange excFollowRedirectToAuth = new Request.Builder().header("Cookie",cookie).get(excCallResource.getResponse().getHeader().getFirstValue("Location")).buildExchange();
        excFollowRedirectToAuth = httpClient.call(excFollowRedirectToAuth);

        Exchange excFollowRedirectToClient = new Request.Builder().header("Cookie",cookie).get(excFollowRedirectToAuth.getResponse().getHeader().getFirstValue("Location")).buildExchange();
        excFollowRedirectToClient = httpClient.call(excFollowRedirectToClient);

        Set<String> accessTokens = new HashSet<>();
        int limit = 1000;
        List<Thread> threadList = new ArrayList<>();
        CountDownLatch cdl = new CountDownLatch(limit);
        for(int i = 0; i < limit; i++) {
            threadList.add(new Thread(() -> {
                try {
                    cdl.countDown();
                    cdl.await();
                    Exchange excCallResource2 = new Request.Builder().get(getClientAddress()).header("Cookie", cookie).buildExchange();
                    excCallResource2 = httpClient.call(excCallResource2);
                    synchronized (accessTokens) {
                        accessTokens.add(excCallResource2.getResponse().getBodyAsStringDecoded());
                    }
                }catch (Exception e){
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
        assertTrue(accessTokens.size() == limit);
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

        OAuth2ResourceInterceptor oAuth2ResourceInterceptor = new OAuth2ResourceInterceptor();
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
                exc.setResponse(Response.ok(answer.getAccessToken()).build());
                return Outcome.RETURN;
            }
        });
        return sp;
    }
}
