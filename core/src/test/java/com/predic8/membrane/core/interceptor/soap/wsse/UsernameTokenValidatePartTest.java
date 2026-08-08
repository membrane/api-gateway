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

import com.predic8.membrane.core.interceptor.Outcome;
import com.predic8.membrane.core.security.BasicHttpSecurityScheme;
import com.predic8.membrane.core.security.SecurityScheme;
import com.predic8.membrane.core.util.ConfigurationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;

import static com.predic8.membrane.core.exchange.Exchange.SECURITY_SCHEMES;
import static com.predic8.membrane.core.interceptor.soap.wsse.WsSecurityFaultCode.*;
import static com.predic8.membrane.core.interceptor.soap.wsse.WsSecurityXmlUtil.WSSE_NS;
import static com.predic8.membrane.core.interceptor.soap.wsse.WsSecurityXmlUtil.WSU_NS;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.*;

class UsernameTokenValidatePartTest extends AbstractWsSecurityTest {

    private static final String PASSWORD_TEXT_TYPE =
            "http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-username-token-profile-1.0#PasswordText";
    private static final String PASSWORD_DIGEST_TYPE =
            "http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-username-token-profile-1.0#PasswordDigest";

    private static final String NONCE = "abcdefgh";

    private static final String SOAP_TEMPLATE = """
            <soap:Envelope xmlns:soap="http://schemas.xmlsoap.org/soap/envelope/">
                <soap:Header>
                    <wsse:Security xmlns:wsse="%s">
                        <wsse:UsernameToken>
                            %%s
                        </wsse:UsernameToken>
                    </wsse:Security>
                </soap:Header>
                <soap:Body>
                    <foo>bar</foo>
                </soap:Body>
            </soap:Envelope>
            """.formatted(WSSE_NS);

    UsernameTokenValidatePart usernameToken;
    WsSecurityInterceptor wsSecurity;

    @BeforeEach
    void setUp() {
        usernameToken = new UsernameTokenValidatePart();
        usernameToken.setUsername("alice");
        usernameToken.setPassword("secret");
        wsSecurity = validating(usernameToken);
    }

    private void exchangeWithToken(String token) throws Exception {
        exchangeWithBody(SOAP_TEMPLATE.formatted(token));
        wsSecurity.init(router);
    }

    private void exchangeWithPlainTextToken(String username, String password) throws Exception {
        exchangeWithToken("""
                <wsse:Username>%s</wsse:Username>
                <wsse:Password Type="%s">%s</wsse:Password>
                """.formatted(username, PASSWORD_TEXT_TYPE, password));
    }

    /**
     * A digest token for user {@code alice}, with the digest computed over the given password - so a
     * password other than the configured one yields a token that must be rejected.
     */
    private static String digestTokenForAlice(String password, String created, String nonceBase64) throws Exception {
        MessageDigest sha1 = MessageDigest.getInstance("SHA-1");
        sha1.update(Base64.getDecoder().decode(nonceBase64));
        sha1.update(created.getBytes(UTF_8));
        sha1.update(password.getBytes(UTF_8));
        return """
                <wsse:Username>alice</wsse:Username>
                <wsse:Password Type="%s">%s</wsse:Password>
                <wsse:Nonce>%s</wsse:Nonce>
                <wsu:Created xmlns:wsu="%s">%s</wsu:Created>
                """.formatted(PASSWORD_DIGEST_TYPE, Base64.getEncoder().encodeToString(sha1.digest()),
                nonceBase64, WSU_NS, created);
    }

    private static String freshDigestTokenForAlice(String password) throws Exception {
        return digestTokenForAlice(password, Instant.now().toString(), NONCE);
    }

    @Test
    void correctPlainTextTokenIsAccepted() throws Exception {
        exchangeWithPlainTextToken("alice", "secret");

        assertEquals(Outcome.CONTINUE, wsSecurity.handleRequest(exchange));
    }

    @Test
    void successfulValidationExposesTheUsernameAsBasicSecurityScheme() throws Exception {
        exchangeWithPlainTextToken("alice", "secret");

        assertEquals(Outcome.CONTINUE, wsSecurity.handleRequest(exchange));

        List<SecurityScheme> schemes = exchange.getProperty(SECURITY_SCHEMES, List.class);
        assertEquals(1, schemes.size());
        assertEquals("alice", ((BasicHttpSecurityScheme) schemes.getFirst()).getUsername());
    }

    @Test
    void unresolvedCredentialExpressionDoesNotAuthenticateLiteralNull() throws Exception {
        // An unresolved template expression renders as the literal string "null"; a client sending
        // "null"/"null" must not be authenticated by it. A misconfigured gateway is an internal
        // error, not a WS-Security fault.
        usernameToken.setUsername("${property.missingUser}");
        usernameToken.setPassword("${property.missingPassword}");
        exchangeWithPlainTextToken("null", "null");

        assertAborts(wsSecurity, 500);
    }

    @Test
    void missingUsernameConfigurationIsRejected() {
        usernameToken.setUsername("  ");

        assertThrows(ConfigurationException.class, () -> wsSecurity.init(router));
    }

    @Test
    void missingPasswordConfigurationIsRejected() {
        usernameToken.setPassword("  ");

        assertThrows(ConfigurationException.class, () -> wsSecurity.init(router));
    }

    @Test
    void wrongPasswordIsRejected() throws Exception {
        exchangeWithPlainTextToken("alice", "wrong");

        assertFault(wsSecurity, FAILED_AUTHENTICATION);
    }

    @Test
    void wrongUsernameIsRejected() throws Exception {
        exchangeWithPlainTextToken("mallory", "secret");

        assertFault(wsSecurity, FAILED_AUTHENTICATION);
    }

    @Test
    void securityHeaderWithoutAUsernameTokenIsRejected() throws Exception {
        exchangeWithBody("""
                <soap:Envelope xmlns:soap="http://schemas.xmlsoap.org/soap/envelope/">
                    <soap:Header><wsse:Security xmlns:wsse="%s"/></soap:Header>
                    <soap:Body><foo>bar</foo></soap:Body>
                </soap:Envelope>
                """.formatted(WSSE_NS));
        wsSecurity.init(router);

        assertFault(wsSecurity, INVALID_SECURITY_TOKEN);
    }

    @Test
    void missingPasswordElementIsRejected() throws Exception {
        exchangeWithToken("<wsse:Username>alice</wsse:Username>");

        assertFault(wsSecurity, INVALID_SECURITY_TOKEN);
    }

    @Test
    void nonSoapMessageIsRejected() throws Exception {
        exchangeWithBody("<foo>bar</foo>");
        wsSecurity.init(router);

        assertAborts(wsSecurity, 400);
    }

    @Test
    void malformedNonceIsRejectedAsInvalidToken() throws Exception {
        exchangeWithToken("""
                <wsse:Username>alice</wsse:Username>
                <wsse:Password Type="%s">irrelevant</wsse:Password>
                <wsse:Nonce>not-valid-base64!!</wsse:Nonce>
                <wsu:Created xmlns:wsu="%s">%s</wsu:Created>
                """.formatted(PASSWORD_DIGEST_TYPE, WSU_NS, Instant.now()));

        assertFault(wsSecurity, INVALID_SECURITY_TOKEN);
    }

    @Test
    void correctDigestTokenIsAccepted() throws Exception {
        exchangeWithToken(freshDigestTokenForAlice("secret"));

        assertEquals(Outcome.CONTINUE, wsSecurity.handleRequest(exchange));
    }

    @Test
    void wrongDigestIsRejected() throws Exception {
        exchangeWithToken(freshDigestTokenForAlice("wrongpassword"));

        assertFault(wsSecurity, FAILED_AUTHENTICATION);
    }

    @Test
    void staleCreatedIsRejected() throws Exception {
        exchangeWithToken(digestTokenForAlice(
                "secret", Instant.now().minus(Duration.ofHours(1)).toString(), NONCE));

        assertFault(wsSecurity, FAILED_AUTHENTICATION);
    }

    @Test
    void replayedNonceIsRejectedOnSecondUse() throws Exception {
        String token = digestTokenForAlice("secret", Instant.now().toString(),
                Base64.getEncoder().encodeToString("replayed-nonce-1".getBytes(UTF_8)));

        exchangeWithToken(token);
        assertEquals(Outcome.CONTINUE, wsSecurity.handleRequest(exchange));

        // Same nonce+created replayed in a second, otherwise-identical request.
        exchangeWithToken(token);
        assertFault(wsSecurity, FAILED_AUTHENTICATION);
    }

    @Test
    void createdWithANonZuluOffsetIsAccepted() throws Exception {
        // xs:dateTime permits any offset, not only "Z". Rejecting "+02:00" as malformed would fault a
        // conformant peer - and would disagree with the wsu:Timestamp parsing in validate/signature.
        String created = Instant.now().atOffset(java.time.ZoneOffset.ofHours(2)).toString();
        exchangeWithToken(digestTokenForAlice("secret", created, NONCE));

        assertEquals(Outcome.CONTINUE, wsSecurity.handleRequest(exchange));
    }

    @Test
    void createdWithANonZuluOffsetOutsideTheWindowIsStillRejected() throws Exception {
        // The offset is honoured rather than ignored: the same wall-clock text at a different offset is
        // a different instant, and a stale one must not slip through as fresh.
        String created = Instant.now().minus(Duration.ofHours(1)).atOffset(java.time.ZoneOffset.ofHours(2)).toString();
        exchangeWithToken(digestTokenForAlice("secret", created, NONCE));

        assertFault(wsSecurity, FAILED_AUTHENTICATION);
    }

    @Test
    void moreThanOneUsernameTokenIsRejected() throws Exception {
        // Taking the first would let the choice of token decide whether the message authenticates, and
        // would forward the other one to the backend unchecked.
        exchangeWithBody("""
                <soap:Envelope xmlns:soap="http://schemas.xmlsoap.org/soap/envelope/">
                    <soap:Header>
                        <wsse:Security xmlns:wsse="%s">
                            <wsse:UsernameToken>
                                <wsse:Username>alice</wsse:Username>
                                <wsse:Password Type="%s">secret</wsse:Password>
                            </wsse:UsernameToken>
                            <wsse:UsernameToken>
                                <wsse:Username>mallory</wsse:Username>
                                <wsse:Password Type="%s">whatever</wsse:Password>
                            </wsse:UsernameToken>
                        </wsse:Security>
                    </soap:Header>
                    <soap:Body><foo>bar</foo></soap:Body>
                </soap:Envelope>
                """.formatted(WSSE_NS, PASSWORD_TEXT_TYPE, PASSWORD_TEXT_TYPE));
        wsSecurity.init(router);

        assertFault(wsSecurity, INVALID_SECURITY_TOKEN);
    }

    @Test
    void passwordWithAnUnsupportedTypeIsRejected() throws Exception {
        // Not compared as plain text: the sender said the content is something else, so a match would
        // be an accident and a mismatch would be reported as a wrong password.
        exchangeWithToken("""
                <wsse:Username>alice</wsse:Username>
                <wsse:Password Type="http://example.com/PasswordSomethingElse">secret</wsse:Password>
                """);

        assertFault(wsSecurity, UNSUPPORTED_SECURITY_TOKEN);
    }

    @Test
    void passwordWithoutATypeIsTreatedAsPlainText() throws Exception {
        // The UsernameToken profile's default when Type is absent.
        exchangeWithToken("""
                <wsse:Username>alice</wsse:Username>
                <wsse:Password>secret</wsse:Password>
                """);

        assertEquals(Outcome.CONTINUE, wsSecurity.handleRequest(exchange));
    }

    @Test
    void zeroFreshnessWindowIsRejected() {
        assertThrows(ConfigurationException.class, () -> usernameToken.setFreshnessWindow("PT0S"));
    }

    @Test
    void negativeFreshnessWindowIsRejected() {
        assertThrows(ConfigurationException.class, () -> usernameToken.setFreshnessWindow("PT-5M"));
    }

    @Test
    void malformedFreshnessWindowIsRejected() {
        ConfigurationException e = assertThrows(ConfigurationException.class,
                () -> usernameToken.setFreshnessWindow("not-a-duration"));
        assertTrue(e.getMessage().contains("not-a-duration"));
    }
}
