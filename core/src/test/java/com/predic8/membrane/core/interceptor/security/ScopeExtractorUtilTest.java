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