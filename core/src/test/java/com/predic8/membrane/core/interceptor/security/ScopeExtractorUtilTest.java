//package com.predic8.membrane.core.interceptor.security;
//
//import com.predic8.membrane.core.exchange.*;
//import com.predic8.membrane.core.http.*;
//import org.junit.jupiter.api.*;
//
//import java.net.*;
//import java.util.*;
//
//import static com.predic8.membrane.core.interceptor.apikey.ApiKeysInterceptor.*;
//import static java.util.Set.*;
//
//class ScopeExtractorUtilTest {
//
//    @Test
//    void getClaimsFromOAuth2() throws URISyntaxException {
//        Exchange exc = Request.get("/").buildExchange();
//
//        Map<String,Object> jwt = new HashMap<>();
//        jwt.put("scp","read write finance");
//
//        exc.setProperty("jwt",jwt);
//
//        assertEquals(of("read", "write", "finance"), getScopes(exc));
//    }
//
//    @Test
//    void mptyClaimsListOfOAuth2() throws URISyntaxException {
//        Exchange exc = Request.get("/").buildExchange();
//
//        Map<String,Object> jwt = new HashMap<>();
//
//        exc.setProperty("jwt",jwt);
//
//        assertEquals(of(), getScopes(exc));
//    }
//
//    @Test
//    void getScopesFromApiKey() throws URISyntaxException {
//        Exchange exc = Request.get("/").buildExchange();
//
//        List<String> scopes = of("admin", "dev", "modify").stream().toList();
//
//        exc.setProperty(SCOPES, scopes);
//
//        assertEquals(of("admin", "dev", "modify"), getScopes(exc));
//    }
//
//    @Test
//    void emptyScopesListOfApiKey() throws URISyntaxException {
//        Exchange exc = Request.get("/").buildExchange();
//
//        List<String> scopes = new ArrayList<>();
//
//        exc.setProperty(SCOPES, scopes);
//
//        assertEquals(of(), getScopes(exc));
//    }
//
//}