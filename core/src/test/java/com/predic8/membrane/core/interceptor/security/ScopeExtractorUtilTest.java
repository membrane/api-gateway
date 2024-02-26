package com.predic8.membrane.core.interceptor.security;

import com.predic8.membrane.core.exchange.*;
import com.predic8.membrane.core.http.*;
import org.junit.jupiter.api.*;

import java.net.*;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class ScopeExtractorUtilTest {

    @Test
    void getClaimsFromOAuth2() throws URISyntaxException {

        Exchange exc = Request.get("/").buildExchange();

        Map<String,Object> jwt = new HashMap<>();
        jwt.put("scp","read write finance");

        exc.setProperty("jwt",jwt);

        assertEquals(Set.of("scp","read write finance"), ScopeExtractorUtil.getScopes(exc));
    }

}