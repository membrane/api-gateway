/* Copyright 2026 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */

package com.predic8.membrane.core.interceptor.oauth2.tokengenerators;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

class JwtGeneratorTest {

    /**
     * Additional claims come from requested claims, whose values a client could choose. They must not replace the
     * claims the server sets itself.
     */
    @Test
    void additionalClaimsDoNotReplaceServerClaims() throws Exception {
        final var idToken = new JwtGenerator().getSignedIdToken("https://as.example.com", "john", "client-a", 600,
                new JwtGenerator.Claim("sub", "alice"),
                new JwtGenerator.Claim("aud", "client-b"),
                new JwtGenerator.Claim("iss", "https://evil.example.com"),
                new JwtGenerator.Claim("email", "john@example.com"));

        final var payload = payloadOf(idToken);
        assertEquals("john", payload.get("sub").asText());
        assertEquals("client-a", payload.get("aud").asText());
        assertEquals("https://as.example.com", payload.get("iss").asText());
        assertEquals("john@example.com", payload.get("email").asText());
    }

    private static JsonNode payloadOf(String jwt) throws Exception {
        return new ObjectMapper().readTree(Base64.getUrlDecoder().decode(jwt.split("\\.")[1]));
    }
}
