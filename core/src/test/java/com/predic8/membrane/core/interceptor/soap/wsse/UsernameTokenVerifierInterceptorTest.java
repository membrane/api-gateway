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
package com.predic8.membrane.core.interceptor.soap.wsse;

import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.Request;
import com.predic8.membrane.core.interceptor.Outcome;
import com.predic8.membrane.core.router.DefaultRouter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.predic8.membrane.core.http.MimeType.TEXT_XML;
import static org.junit.jupiter.api.Assertions.*;

class UsernameTokenVerifierInterceptorTest {

    private static final String SOAP_TEMPLATE = """
            <soap:Envelope xmlns:soap="http://schemas.xmlsoap.org/soap/envelope/">
                <soap:Header>
                    <wsse:Security xmlns:wsse="http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-wssecurity-secext-1.0.xsd">
                        <wsse:UsernameToken>
                            %s
                        </wsse:UsernameToken>
                    </wsse:Security>
                </soap:Header>
                <soap:Body>
                    <foo>bar</foo>
                </soap:Body>
            </soap:Envelope>
            """;

    DefaultRouter router;
    Exchange exchange;
    UsernameTokenVerifierInterceptor verifier;

    @BeforeEach
    void setUp() {
        router = new DefaultRouter();
        verifier = new UsernameTokenVerifierInterceptor();
        verifier.setUsername("alice");
        verifier.setPassword("secret");
    }

    private void exchangeWithBody(String body) throws Exception {
        exchange = new Exchange(null);
        exchange.setRequest(new Request.Builder()
                .post("/service")
                .contentType(TEXT_XML)
                .body(body)
                .build());
    }

    private void plainTextToken(String username, String password) throws Exception {
        exchangeWithBody(SOAP_TEMPLATE.formatted("""
                <wsse:Username>%s</wsse:Username>
                <wsse:Password Type="http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-wssecurity-secext-1.0.xsd#PasswordText">%s</wsse:Password>
                """.formatted(username, password)));
    }

    @Test
    void correctPlainTextTokenIsAccepted() throws Exception {
        plainTextToken("alice", "secret");
        verifier.init(router);

        assertEquals(Outcome.CONTINUE, verifier.handleRequest(exchange));
    }

    @Test
    void wrongPasswordIsRejected() throws Exception {
        plainTextToken("alice", "wrong");
        verifier.init(router);

        assertEquals(Outcome.ABORT, verifier.handleRequest(exchange));
        assertEquals(403, exchange.getResponse().getStatusCode());
    }

    @Test
    void wrongUsernameIsRejected() throws Exception {
        plainTextToken("mallory", "secret");
        verifier.init(router);

        assertEquals(Outcome.ABORT, verifier.handleRequest(exchange));
        assertEquals(403, exchange.getResponse().getStatusCode());
    }

    @Test
    void missingTokenIsRejected() throws Exception {
        exchangeWithBody("""
                <soap:Envelope xmlns:soap="http://schemas.xmlsoap.org/soap/envelope/">
                    <soap:Body><foo>bar</foo></soap:Body>
                </soap:Envelope>
                """);
        verifier.init(router);

        assertEquals(Outcome.ABORT, verifier.handleRequest(exchange));
        assertEquals(401, exchange.getResponse().getStatusCode());
    }

    @Test
    void nonSoapMessageIsRejected() throws Exception {
        exchangeWithBody("<foo>bar</foo>");
        verifier.init(router);

        assertEquals(Outcome.ABORT, verifier.handleRequest(exchange));
        assertEquals(400, exchange.getResponse().getStatusCode());
    }

    @Test
    void malformedNonceIsRejectedAsVerificationFailure() throws Exception {
        exchangeWithBody(SOAP_TEMPLATE.formatted("""
                <wsse:Username>alice</wsse:Username>
                <wsse:Password Type="http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-username-token-profile-1.0#PasswordDigest">irrelevant</wsse:Password>
                <wsse:Nonce>not-valid-base64!!</wsse:Nonce>
                <wsu:Created xmlns:wsu="http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-wssecurity-utility-1.0.xsd">%s</wsu:Created>
                """.formatted(java.time.Instant.now().toString())));
        verifier.init(router);

        assertEquals(Outcome.ABORT, verifier.handleRequest(exchange));
        assertEquals(403, exchange.getResponse().getStatusCode());
    }

    private String createDigestToken(String password, String created, String nonceBase64) {
        try {
            java.security.MessageDigest sha1 = java.security.MessageDigest.getInstance("SHA-1");
            sha1.update(java.util.Base64.getDecoder().decode(nonceBase64));
            sha1.update(created.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            sha1.update(password.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            String digest = java.util.Base64.getEncoder().encodeToString(sha1.digest());
            return """
                    <wsse:Username>alice</wsse:Username>
                    <wsse:Password Type="http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-username-token-profile-1.0#PasswordDigest">%s</wsse:Password>
                    <wsse:Nonce>%s</wsse:Nonce>
                    <wsu:Created xmlns:wsu="http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-wssecurity-utility-1.0.xsd">%s</wsu:Created>
                    """.formatted(digest, nonceBase64, created);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void correctDigestTokenIsAccepted() throws Exception {
        String created = java.time.Instant.now().toString();
        exchangeWithBody(SOAP_TEMPLATE.formatted(createDigestToken("secret", created, "abcdefgh")));
        verifier.init(router);

        assertEquals(Outcome.CONTINUE, verifier.handleRequest(exchange));
    }

    @Test
    void wrongDigestIsRejected() throws Exception {
        String created = java.time.Instant.now().toString();
        exchangeWithBody(SOAP_TEMPLATE.formatted(createDigestToken("wrongpassword", created, "abcdefgh")));
        verifier.init(router);

        assertEquals(Outcome.ABORT, verifier.handleRequest(exchange));
        assertEquals(403, exchange.getResponse().getStatusCode());
    }

    @Test
    void staleCreatedIsRejected() throws Exception {
        String created = java.time.Instant.now().minus(java.time.Duration.ofHours(1)).toString();
        exchangeWithBody(SOAP_TEMPLATE.formatted(createDigestToken("secret", created, "abcdefgh")));
        verifier.init(router);

        assertEquals(Outcome.ABORT, verifier.handleRequest(exchange));
        assertEquals(403, exchange.getResponse().getStatusCode());
    }

    @Test
    void replayedNonceIsRejectedOnSecondUse() throws Exception {
        String created = java.time.Instant.now().toString();
        String nonce = java.util.Base64.getEncoder().encodeToString("replayed-nonce-1".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        String tokenBody = createDigestToken("secret", created, nonce);

        exchangeWithBody(SOAP_TEMPLATE.formatted(tokenBody));
        verifier.init(router);
        assertEquals(Outcome.CONTINUE, verifier.handleRequest(exchange));

        // Same nonce+created replayed in a second, otherwise-identical request.
        exchangeWithBody(SOAP_TEMPLATE.formatted(tokenBody));
        assertEquals(Outcome.ABORT, verifier.handleRequest(exchange));
        assertEquals(403, exchange.getResponse().getStatusCode());
    }
}
