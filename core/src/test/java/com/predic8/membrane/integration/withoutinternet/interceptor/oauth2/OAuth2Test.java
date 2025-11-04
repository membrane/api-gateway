/* Copyright 2024 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */
package com.predic8.membrane.integration.withoutinternet.interceptor.oauth2;

import com.predic8.membrane.core.*;
import com.predic8.membrane.core.exchangestore.*;
import com.predic8.membrane.core.interceptor.authentication.session.*;
import com.predic8.membrane.core.interceptor.flow.*;
import com.predic8.membrane.core.interceptor.jwt.*;
import com.predic8.membrane.core.interceptor.oauth2.ClaimList;
import com.predic8.membrane.core.interceptor.oauth2.Client;
import com.predic8.membrane.core.interceptor.oauth2.OAuth2AuthorizationServerInterceptor;
import com.predic8.membrane.core.interceptor.oauth2.StaticClientList;
import com.predic8.membrane.core.interceptor.oauth2.tokengenerators.*;
import com.predic8.membrane.core.interceptor.templating.*;
import com.predic8.membrane.core.proxies.*;
import com.predic8.membrane.core.transport.http.*;
import org.json.*;
import org.junit.jupiter.api.*;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.regex.*;

import static org.junit.jupiter.api.Assertions.*;


class OAuth2Test {

    static Router router;
    static Router router2;

    static ServiceProxy oAuth2ServerProxy;
    static OAuth2AuthorizationServerInterceptor oAuth2ASI;

    static ServiceProxy jwtAuthProxy;
    static JwtAuthInterceptor jwtAuthInterceptor;

    static final String clientId = "abc";
    static final String clientSecret = "def";

    @BeforeAll
    static void startup() throws Exception {
        router = new Router();
        router.setHotDeploy(false);
        router.setExchangeStore(new ForgetfulExchangeStore());
        router.setTransport(new HttpTransport());

        oAuth2ServerProxy = new ServiceProxy(new ServiceProxyKey("localhost", "*", ".*", 2000), null, 0);
        oAuth2ASI = createOAuth2AuthServerInterceptor();
        oAuth2ServerProxy.setFlow(List.of(oAuth2ASI));


        router.getRuleManager().addProxyAndOpenPortIfNew(oAuth2ServerProxy);
        router.init();
        router.start();

        router2 = new Router();
        router2.setHotDeploy(false);
        router2.setExchangeStore(new ForgetfulExchangeStore());
        router2.setTransport(new HttpTransport());

        jwtAuthProxy = new ServiceProxy(new ServiceProxyKey("localhost", "*", ".*", 3000), null, 0);
        jwtAuthInterceptor = createJwtAuthInterceptor();

        jwtAuthProxy.setFlow(List.of(
                jwtAuthInterceptor,
                new StaticInterceptor(){{setSrc("{\"success\": \"true\"}");}},
                new ReturnInterceptor()));

        router2.getRuleManager().addProxyAndOpenPortIfNew(jwtAuthProxy);
        router2.init();
        router2.start();
    }

    @AfterAll
    static void shutdown() {
        router2.stop();
        router.stop();
    }

    @Test
    void testJwtAuthentication() throws Exception {
        String json = getTokenRequestResponse(createTokenRequestParameters());
        JSONObject jsonObject = new JSONObject(json);
        assertEquals("Bearer", jsonObject.getString("token_type"));
        assertNotNull(jsonObject.getString("access_token"));
        assertEquals("Ok", sendRequestToTarget(parseTokenRequestResponse("access_token", json)));
    }

    @Test
    void testJwtAuthenticationAfterRefresh() throws Exception {
        String json = getTokenRequestResponse(createTokenRequestParameters());
        JSONObject jsonObject = new JSONObject(json);
        String at1 = jsonObject.getString("access_token");
        assertNotNull(at1);

        json = getTokenRequestResponse(createTokenRequestParametersAfterRefresh(jsonObject.getString("refresh_token")));
        jsonObject = new JSONObject(json);
        String at2 = jsonObject.getString("access_token");
        assertNotNull(at2);

        assertEquals("Bearer", jsonObject.getString("token_type"));
        assertNotNull(jsonObject.getString("access_token"));
        assertEquals("Ok", sendRequestToTarget(parseTokenRequestResponse("access_token", json)));

        assertEquals(getJwtClaim(at1, "iss"), getJwtClaim(at2, "iss"));
        assertEquals(getJwtClaim(at1, "sub"), getJwtClaim(at2, "sub"));
        assertEquals(getJwtClaim(at1, "scope"), getJwtClaim(at2, "scope"));
    }

    @Test
    void testJwtAuthenticationFailingWithRefreshInsteadOfAccessToken() throws Exception {
        String json = getTokenRequestResponse(createTokenRequestParameters());
        JSONObject jsonObject = new JSONObject(json);
        assertEquals("Bearer", jsonObject.getString("token_type"));
        assertNotNull(jsonObject.getString("refresh_token"));
        assertEquals("Bad Request", sendRequestToTarget(parseTokenRequestResponse("refresh_token", json)));
    }

    private static OAuth2AuthorizationServerInterceptor createOAuth2AuthServerInterceptor() {
        OAuth2AuthorizationServerInterceptor oAuth2AuthSI = new OAuth2AuthorizationServerInterceptor();
        oAuth2AuthSI.setIssuer("http://localhost:2000");
        oAuth2AuthSI.setTokenGenerator(new BearerJwtTokenGenerator() {{
            setExpiration(60);
        }});
        oAuth2AuthSI.setRefreshTokenGenerator(new BearerJwtTokenGenerator() {{
            setExpiration(60);
        }});

        oAuth2AuthSI.setUserDataProvider(new StaticUserDataProvider() {{
            User u = new User("john", "password");
            u.getAttributes().put("aud", "demo1");
            setUsers(List.of(u));
        }});

        oAuth2AuthSI.setClientList(new StaticClientList() {{
            setClients(List.of(new Client("abc", "def", "http://localhost:3000/oauth2callback", "authorization_code,password,client_credentials,refresh_token,implicit")));
        }});

        oAuth2AuthSI.setClaimList(new ClaimList() {{
            setValue("username");
            setScopes(new ArrayList<>() {{
                add(new Scope() {{
                    setId("username");
                    setClaims("username");
                }});
            }});
        }});

        return oAuth2AuthSI;
    }

    private static JwtAuthInterceptor createJwtAuthInterceptor() {
        return new JwtAuthInterceptor() {{
            setExpectedAud("demo1");
            setJwks(new Jwks() {{
                setJwksUris("http://localhost:2000/oauth2/certs");
            }});
        }};
    }

    private static String sendRequestToTarget(String authorizationHeaderValue) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL("http://localhost:3000").openConnection();

        connection.setRequestMethod("GET");
        connection.setRequestProperty("Authorization", authorizationHeaderValue);

        return connection.getResponseMessage();
    }

    private static String parseTokenRequestResponse(String key, String tokenRequestResponse) {

        String temp = tokenRequestResponse.replaceFirst(Pattern.quote("{\""+key+"\":\""),"");
        String token = temp.split(Pattern.quote("\""))[0];

        temp = temp.replaceFirst(Pattern.quote(token + "\",\"token_type\":\""),"");
        String tokenType = temp.split(Pattern.quote("\""))[0];

        return tokenType + " " + token;
    }

    private static String getTokenRequestResponse(String data) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL("http://localhost:2000/oauth2/token").openConnection();
        connection.setRequestMethod("POST");

        sendPostData(connection, data);

        return readResponse(connection);
    }

    private static String createTokenRequestParameters() {
        return "grant_type=password&client_id=" + clientId + "&client_secret=" + clientSecret + "&username=john&password=password";
    }

    private static String createTokenRequestParametersAfterRefresh(String refreshToken) {
        return "grant_type=refresh_token&client_id=" + clientId + "&client_secret=" + clientSecret + "&refresh_token=" + refreshToken;
    }

    private static String readResponse(HttpURLConnection connection) throws IOException {
        BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream()));
        StringBuilder response = new StringBuilder();

        String line;
        while((line = in.readLine()) != null)
            response.append(line);
        in.close();

        return response.toString();
    }

    private static void sendPostData(HttpURLConnection connection, String urlParameters) throws IOException {
        connection.setDoOutput(true);
        DataOutputStream wr = new DataOutputStream(connection.getOutputStream());
        wr.writeBytes(urlParameters);
        wr.flush();
        wr.close();
    }

    private static String getJwtClaim(String jwt, String claim) {
        String[] parts = jwt.split("\\.");
        if (parts.length < 2) throw new AssertionError("Invalid JWT format");
        String payloadJson = new String(
                java.util.Base64.getUrlDecoder().decode(parts[1]),
                java.nio.charset.StandardCharsets.UTF_8
        );
        JSONObject payload = new JSONObject(payloadJson);
        Object v = payload.opt(claim);
        return (v == null) ? null : String.valueOf(v);
    }
}
