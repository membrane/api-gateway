/*
 * Copyright 2026 predic8 GmbH, www.predic8.com
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

package com.predic8.membrane.integration.withoutinternet.interceptor.oauth2;

import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.Request;
import com.predic8.membrane.core.interceptor.oauth2.ClaimList;
import com.predic8.membrane.core.interceptor.oauth2.Client;
import com.predic8.membrane.core.interceptor.oauth2.OAuth2AuthorizationServerInterceptor;
import com.predic8.membrane.core.interceptor.oauth2.StaticClientList;
import com.predic8.membrane.core.interceptor.oauth2.tokengenerators.BearerJwtTokenGenerator;
import com.predic8.membrane.core.router.TestRouter;
import com.predic8.membrane.core.util.Util;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static com.predic8.membrane.annot.Constants.USERAGENT;
import static com.predic8.membrane.core.http.Header.ACCEPT;
import static com.predic8.membrane.core.http.Header.USER_AGENT;
import static com.predic8.membrane.core.http.MimeType.APPLICATION_JSON;
import static com.predic8.membrane.core.http.MimeType.APPLICATION_X_WWW_FORM_URLENCODED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * An oauth2authserver without a userDataProvider serves user-less flows
 * (client_credentials); flows that need a user reject the request.
 */
public class AuthServerWithoutUserDataProviderTest {

    private TestRouter router;
    private OAuth2AuthorizationServerInterceptor oasi;

    @BeforeEach
    void setUp() throws Exception {
        router = new TestRouter();
        router.start();
        oasi = new OAuth2AuthorizationServerInterceptor() {
            @Override
            public String computeBasePath() {
                return "";
            }
        };
        BearerJwtTokenGenerator tokenGenerator = new BearerJwtTokenGenerator();
        tokenGenerator.setWarningGeneratedKey(false);
        oasi.setTokenGenerator(tokenGenerator);
        oasi.setPath("/login/");
        oasi.setIssuer("http://localhost:2001");
        setClientList();
        setClaimList();
        oasi.init(router);
    }

    @AfterEach
    void tearDown() {
        router.stop();
    }

    @Test
    void clientCredentialsGrantWorks() throws Exception {
        Exchange exc = tokenRequest("grant_type=client_credentials&client_id=my-client&client_secret=secret");
        assertEquals(200, exc.getResponse().getStatusCode());
        assertNotNull(Util.parseSimpleJSONResponse(exc.getResponse()).get("access_token"));
    }

    @Test
    void passwordGrantIsRejected() throws Exception {
        Exchange exc = tokenRequest("grant_type=password&username=john&password=secret"
                                    + "&client_id=my-client&client_secret=secret");
        assertEquals(400, exc.getResponse().getStatusCode());
        assertEquals("access_denied", Util.parseSimpleJSONResponse(exc.getResponse()).get("error"));
    }

    private Exchange tokenRequest(String body) throws Exception {
        Exchange exc = new Request.Builder()
                .post("/oauth2/token")
                .contentType(APPLICATION_X_WWW_FORM_URLENCODED)
                .header(ACCEPT, APPLICATION_JSON)
                .header(USER_AGENT, USERAGENT)
                .body(body)
                .buildExchange();
        OAuth2TestUtil.makeExchangeValid(exc);
        oasi.handleRequest(exc);
        return exc;
    }

    private void setClientList() {
        Client client = new Client("my-client", "secret", "http://localhost:2001/oauth2callback", "client_credentials,password");
        StaticClientList cl = new StaticClientList();
        cl.setClients(new ArrayList<>(List.of(client)));
        oasi.setClientList(cl);
    }

    private void setClaimList() {
        ClaimList cl = new ClaimList();
        cl.setValue("sub");
        cl.setScopes(new ArrayList<>());
        oasi.setClaimList(cl);
    }
}
