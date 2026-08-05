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

import com.predic8.membrane.annot.MCAttribute;
import com.predic8.membrane.annot.MCElement;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.interceptor.Outcome;
import com.predic8.membrane.core.lang.ExchangeExpression;
import com.predic8.membrane.core.lang.TemplateExchangeExpression;
import com.predic8.membrane.core.util.ConfigurationException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import static com.predic8.membrane.core.exceptions.ProblemDetails.security;
import static com.predic8.membrane.core.interceptor.Outcome.ABORT;
import static com.predic8.membrane.core.interceptor.Outcome.CONTINUE;
import static com.predic8.membrane.core.interceptor.soap.wsse.WsSecurityXml.*;
import static com.predic8.membrane.core.lang.ExchangeExpression.Language.SPEL;
import static com.predic8.membrane.core.util.text.SerializationFunction.TEXT_SERIALIZATION;

/**
 * @description Verifies a WS-Security <code>wsse:UsernameToken</code> on an incoming SOAP request,
 * as added by e.g. the <code>usernameToken</code> interceptor. Checks the username and, depending
 * on the token's <code>Password</code> type, either the plain-text password or the WS-Security
 * digest against the expected values. When the token carries a <code>wsu:Created</code>/
 * <code>wsse:Nonce</code>, this also rejects stale tokens and replayed nonces - the standard
 * WS-Security anti-replay mechanism for UsernameToken. A missing token returns 401; an invalid,
 * stale, or replayed one returns 403, both as Problem Details. Only acts on requests.
 * @topic 3. Security
 * @yaml <pre><code>
 * api:
 *   port: 2000
 *   flow:
 *     - usernameTokenVerifier:
 *         username: ${property.apiUser}
 *         password: ${property.apiPassword}
 * </code></pre>
 */
@MCElement(name = "usernameTokenVerifier")
public class UsernameTokenVerifierInterceptor extends AbstractSoapDomInterceptor {

    private static final Duration DEFAULT_FRESHNESS_WINDOW = Duration.ofMinutes(5);

    private String username;
    private String password;
    private Duration freshnessWindow = DEFAULT_FRESHNESS_WINDOW;

    private ExchangeExpression usernameExpression;
    private ExchangeExpression passwordExpression;

    // A nonce only needs to be remembered for as long as its Created timestamp is still inside the
    // freshness window, but that's a rate, not a constant - so growth is additionally capped here.
    private static final int MAX_REMEMBERED_NONCES = 100_000;

    private final Map<String, Instant> seenNonces = new ConcurrentHashMap<>();
    private final AtomicLong lastPurgeEpochSecond = new AtomicLong();

    @Override
    public void init() {
        super.init();
        if (username == null || username.isBlank()) {
            throw new ConfigurationException("usernameTokenVerifier requires a username attribute.");
        }
        if (password == null || password.isBlank()) {
            throw new ConfigurationException("usernameTokenVerifier requires a password attribute.");
        }
        usernameExpression = TemplateExchangeExpression.newInstance(this, SPEL, username, router, TEXT_SERIALIZATION);
        passwordExpression = TemplateExchangeExpression.newInstance(this, SPEL, password, router, TEXT_SERIALIZATION);
    }

    @Override
    protected String notSoapDetail() {
        return "no wsse:UsernameToken could be verified.";
    }

    @Override
    protected String internalErrorDetail() {
        return "Could not verify wsse:UsernameToken on SOAP message.";
    }

    @Override
    protected Outcome handleDocument(Exchange exc, Document doc) throws Exception {
        Element usernameToken = findUsernameToken(doc);
        if (usernameToken == null) {
            security(router.getConfiguration().isProduction(), getDisplayName())
                    .title("UsernameToken missing.")
                    .status(401)
                    .detail("Request has no wsse:Security/wsse:UsernameToken to verify.")
                    .buildAndSetResponse(exc);
            return ABORT;
        }
        try {
            verify(exc, usernameToken);
            return CONTINUE;
        } catch (VerificationException e) {
            security(router.getConfiguration().isProduction(), getDisplayName())
                    .title("UsernameToken verification failed.")
                    .status(403)
                    .detail(e.getMessage())
                    .buildAndSetResponse(exc);
            return ABORT;
        }
    }

    private static Element findUsernameToken(Document doc) {
        Element envelope = doc.getDocumentElement();
        Element header = getFirstChildByName(envelope, envelope.getNamespaceURI(), "Header");
        Element securityHeader = header == null ? null : getFirstChildByName(header, WSSE_NS, "Security");
        return securityHeader == null ? null : getFirstChildByName(securityHeader, WSSE_NS, "UsernameToken");
    }

    private void verify(Exchange exc, Element usernameToken) throws Exception {
        String expectedUsername = usernameExpression.evaluate(exc, Flow.REQUEST, String.class);
        String expectedPassword = passwordExpression.evaluate(exc, Flow.REQUEST, String.class);
        // A template expression whose value doesn't resolve (e.g. ${property.apiUser} when nothing
        // set that property) renders as the literal string "null" - see
        // TemplateExchangeExpression.evaluateMultiple. Comparing against that would authenticate
        // anyone sending Username/Password "null", so an unresolved expected credential must fail
        // closed as a configuration error instead of being treated as the secret.
        requireResolved(expectedUsername, "username");
        requireResolved(expectedPassword, "password");

        checkUsername(usernameToken, expectedUsername);

        Element nonceEl = getFirstChildByName(usernameToken, WSSE_NS, "Nonce");
        Element createdEl = getFirstChildByName(usernameToken, WSU_NS, "Created");
        Instant created = createdEl == null ? null : parseCreated(createdEl.getTextContent());

        checkPassword(usernameToken, expectedPassword, nonceEl, createdEl == null ? null : createdEl.getTextContent());

        if (created != null) {
            checkFreshness(created);
        }
        if (nonceEl != null && created != null) {
            checkNonceNotReplayed(expectedUsername, nonceEl.getTextContent(), created);
        }
    }

    private static void checkUsername(Element usernameToken, String expectedUsername) {
        Element usernameEl = getFirstChildByName(usernameToken, WSSE_NS, "Username");
        if (!constantTimeEquals(expectedUsername, usernameEl == null ? "" : usernameEl.getTextContent())) {
            throw new VerificationException("Unknown username.");
        }
    }

    private static void checkPassword(Element usernameToken, String expectedPassword,
                                      Element nonceEl, String created) throws Exception {
        Element passwordEl = getFirstChildByName(usernameToken, WSSE_NS, "Password");
        if (passwordEl == null) {
            throw new VerificationException("wsse:UsernameToken has no wsse:Password.");
        }
        if (!PASSWORD_DIGEST_TYPE.equals(passwordEl.getAttribute("Type"))) {
            if (!constantTimeEquals(expectedPassword, passwordEl.getTextContent())) {
                throw new VerificationException("Password does not match.");
            }
            return;
        }
        if (nonceEl == null || created == null) {
            throw new VerificationException("wsse:Password of type PasswordDigest requires wsse:Nonce and wsu:Created.");
        }
        String expectedDigest = usernameTokenDigest(decodeNonce(nonceEl.getTextContent()), created, expectedPassword);
        if (!constantTimeEquals(expectedDigest, passwordEl.getTextContent())) {
            throw new VerificationException("Password digest does not match.");
        }
    }

    // Not a VerificationException: this is a misconfigured gateway, not a bad request - it must
    // surface as an internal error (500) rather than as a plain authentication failure.
    private static void requireResolved(String value, String attribute) {
        if (value == null || value.isBlank() || "null".equals(value)) {
            throw new ConfigurationException(
                    "usernameTokenVerifier: the " + attribute + " expression did not resolve to a value.");
        }
    }

    private Instant parseCreated(String value) {
        try {
            return Instant.parse(value);
        } catch (DateTimeParseException e) {
            throw new VerificationException("wsu:Created is not a valid xs:dateTime: " + value);
        }
    }

    private void checkFreshness(Instant created) {
        Instant now = Instant.now();
        if (created.isBefore(now.minus(freshnessWindow)) || created.isAfter(now.plus(freshnessWindow))) {
            throw new VerificationException("wsu:Created (" + created + ") is outside the allowed freshness window.");
        }
    }

    private void checkNonceNotReplayed(String username, String nonce, Instant created) {
        purgeExpiredNoncesIfDue();
        String key = username + '|' + nonce;
        if (seenNonces.size() >= MAX_REMEMBERED_NONCES) {
            purgeExpiredNonces();
            if (seenNonces.size() >= MAX_REMEMBERED_NONCES) {
                throw new VerificationException("Replay cache is full; rejecting the request.");
            }
        }
        if (seenNonces.putIfAbsent(key, created.plus(freshnessWindow)) != null) {
            throw new VerificationException("Nonce has already been used; rejecting as a replay.");
        }
    }

    private void purgeExpiredNoncesIfDue() {
        long nowSecond = Instant.now().getEpochSecond();
        long previous = lastPurgeEpochSecond.get();
        if (nowSecond > previous && lastPurgeEpochSecond.compareAndSet(previous, nowSecond)) {
            purgeExpiredNonces();
        }
    }

    private void purgeExpiredNonces() {
        Instant now = Instant.now();
        seenNonces.values().removeIf(expiry -> expiry.isBefore(now));
    }

    private static byte[] decodeNonce(String nonce) {
        try {
            return Base64.getDecoder().decode(nonce);
        } catch (IllegalArgumentException e) {
            throw new VerificationException("wsse:Nonce is not valid Base64.");
        }
    }

    private static boolean constantTimeEquals(String expected, String actual) {
        if (expected == null || actual == null) {
            return expected == actual;
        }
        return MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8), actual.getBytes(StandardCharsets.UTF_8));
    }

    public String getUsername() {
        return username;
    }

    /**
     * @description Expected username, evaluated as a SpEL template expression, so both static
     * values and expressions (e.g. <code>${property.apiUser}</code>) are supported.
     * @example ${property.apiUser}
     */
    @MCAttribute
    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    /**
     * @description Expected password, evaluated as a SpEL template expression. Compared directly
     * against a <code>PasswordText</code> token, or used to recompute the digest for a
     * <code>PasswordDigest</code> token.
     * @example ${property.apiPassword}
     */
    @MCAttribute
    public void setPassword(String password) {
        this.password = password;
    }

    public String getFreshnessWindow() {
        return freshnessWindow.toString();
    }

    /**
     * @description Tolerance, as an ISO-8601 duration, applied to a token's <code>wsu:Created</code>
     * and to how long its <code>wsse:Nonce</code> is remembered for replay detection. Only checked
     * when the token actually carries <code>wsu:Created</code>/<code>wsse:Nonce</code>.
     * @default PT5M
     */
    @MCAttribute
    public void setFreshnessWindow(String freshnessWindow) {
        Duration parsed = Duration.parse(freshnessWindow);
        if (!parsed.isPositive()) {
            throw new ConfigurationException("freshnessWindow must be a positive duration.");
        }
        this.freshnessWindow = parsed;
    }
}
