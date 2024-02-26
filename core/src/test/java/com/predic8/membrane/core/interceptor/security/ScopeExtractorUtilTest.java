package com.predic8.membrane.core.interceptor.security;

import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.Request;
import org.junit.jupiter.api.Test;

import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.predic8.membrane.core.interceptor.apikey.ApiKeysInterceptor.SCOPES;
import static com.predic8.membrane.core.interceptor.security.ScopeExtractorUtil.getScopes;
import static java.util.Set.of;
import static org.junit.jupiter.api.Assertions.assertEquals;

class ScopeExtractorUtilTest {

    @Test
    void getClaimsFromOAuth2() throws URISyntaxException {
        Exchange exc = Request.get("/").buildExchange();

        Map<String,Object> jwt = new HashMap<>();
        jwt.put("scp","read write finance");

        exc.setProperty("jwt",jwt);

        assertEquals(of("read", "write", "finance"), getScopes(exc));
    }

    @Test
    void mptyClaimsListOfOAuth2() throws URISyntaxException {
        Exchange exc = Request.get("/").buildExchange();

        Map<String,Object> jwt = new HashMap<>();

        exc.setProperty("jwt",jwt);

        assertEquals(of(), getScopes(exc));
    }

    @Test
    void getScopesFromApiKey() throws URISyntaxException {
        Exchange exc = Request.get("/").buildExchange();

        List<String> scopes = of("admin", "dev", "modify").stream().toList();

        exc.setProperty(SCOPES, scopes);

        assertEquals(of("admin", "dev", "modify"), getScopes(exc));
    }

    @Test
    void emptyScopesListOfApiKey() throws URISyntaxException {
        Exchange exc = Request.get("/").buildExchange();

        List<String> scopes = new ArrayList<>();

        exc.setProperty(SCOPES, scopes);

        assertEquals(of(), getScopes(exc));
    }

}