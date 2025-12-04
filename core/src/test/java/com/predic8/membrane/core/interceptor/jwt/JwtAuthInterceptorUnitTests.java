/* Copyright 2023 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */
package com.predic8.membrane.core.interceptor.jwt;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import com.predic8.membrane.core.HttpRouter;
import com.predic8.membrane.core.exchange.*;
import com.predic8.membrane.core.http.Request;
import org.junit.jupiter.api.*;

import java.util.Map;

import static com.predic8.membrane.core.interceptor.jwt.JwtAuthInterceptor.*;
import static org.junit.jupiter.api.Assertions.*;

public class JwtAuthInterceptorUnitTests {

    Exchange exc;
    JwtAuthInterceptor interceptor;


    @BeforeEach
    void setup() {
        exc = new Exchange(null);
        interceptor = new JwtAuthInterceptor();
    }

    private String getErrorResponse() {
        return getErrorResponse(exc);
    }

    private String getErrorResponse(Exchange exc) {
        try {
            var map = new ObjectMapper().readValue(exc.getResponse().getBody().toString(), Map.class);
            return map.get("detail").toString();
        } catch (JacksonException e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void noJwtInHeader() {
        var exchange = new Request.Builder().buildExchange();
        interceptor.setJwtRetriever(new HeaderJwtRetriever("Authorization", "Bearer"));
        Jwks jwks = new Jwks();
        Jwks.Jwk jwk = new Jwks.Jwk();
        jwk.setContent("{\"kty\":\"RSA\", \"n\":\""+
                "B".repeat(1024 * 8 / 6)
                +"\", \"e\":\"BB\"}");
        jwks.getJwks().add(jwk);
        interceptor.setJwks(jwks);
        interceptor.init(new HttpRouter());
        interceptor.handleRequest(exchange);

        assertEquals(ERROR_JWT_NOT_FOUND, getErrorResponse(exchange));
    }

    @Test
    void invalidHeader() {
        // {"typ":"A","typ":"B"}.{}
        var exception = assertThrows(JWTException.class, () -> interceptor.handleJwt(exc, "eyJ0eXAiOiJBIiwidHlwIjoiQiJ9.e30=.BB"));
        assertEquals(ERROR_DECODED_HEADER_NOT_JSON, exception.getMessage());
    }

    @Test
    void noJwtGiven() {
        var exception = assertThrows(JWTException.class, () -> interceptor.handleJwt(exc, null));
        assertEquals(ERROR_JWT_NOT_FOUND, exception.getMessage());
    }

    @Test
    void noKidGiven() {
        var token = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMjM0NTY3ODkwIiwibmFtZSI6IkpvaG4gRG9lIiwiaWF0IjoxNTE2MjM5MDIyfQ.SflKxwRJSMeKKF2QT4fwpMeJf36POk6yJV_adQssw5c";
        var exception = assertThrows(JWTException.class, () -> interceptor.handleJwt(exc, token));
        assertEquals(ERROR_JWT_VALUE_NOT_PRESENT("kid"), exception.getMessage());
    }
}
