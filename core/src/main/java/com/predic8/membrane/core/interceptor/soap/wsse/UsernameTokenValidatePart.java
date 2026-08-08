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
import com.predic8.membrane.annot.MCChildElement;
import com.predic8.membrane.annot.MCElement;
import com.predic8.membrane.core.interceptor.authentication.session.UserDataProvider;
import com.predic8.membrane.core.util.ConfigurationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Element;

import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import static com.predic8.membrane.core.exchange.Exchange.SECURITY_SCHEMES;
import static com.predic8.membrane.core.interceptor.soap.wsse.WsSecurityFaultCode.*;
import static com.predic8.membrane.core.interceptor.soap.wsse.WsSecurityXmlUtil.*;
import static com.predic8.membrane.core.security.HttpSecurityScheme.BASIC;

/**
 * @description Verifies a <code>wsse:UsernameToken</code> in the inbound
 * <code>wsse:Security</code> header against a pluggable {@link UserDataProvider} (the same
 * abstraction <code>basicAuthentication</code> uses - a static list, an htpasswd file, JDBC, or
 * LDAP), so hashed passwords (bcrypt, crypt(3), argon2id) are supported the same way they are
 * there. When the token carries a <code>wsu:Created</code>/<code>wsse:Nonce</code>, this also
 * rejects stale tokens and replayed nonces - the standard WS-Security anti-replay mechanism for
 * UsernameToken. A missing or malformed token answers <code>wsse:InvalidSecurityToken</code>, a
 * wrong or replayed credential <code>wsse:FailedAuthentication</code>. On success, the username is
 * exposed to the rest of the exchange the same way a <code>basicAuthentication</code> login is, so
 * <code>user()</code> in a later <code>template</code> or <code>groovy</code> step returns it.
 * <p>
 * <code>wsse:Password</code> of type <code>PasswordDigest</code> is not supported yet: verifying it
 * requires the literal plaintext password on the gateway's side to recompute the digest, which a
 * pluggable, hash-friendly provider cannot hand back out. A digest token is rejected with
 * <code>wsse:UnsupportedSecurityToken</code>.
 * </p>
 */
@MCElement(name = "usernameToken", component = false, id = "wsSecurity-validate-usernameToken")
public class UsernameTokenValidatePart extends ValidatePart {

    private static final Logger log = LoggerFactory.getLogger(UsernameTokenValidatePart.class);

    private static final Duration DEFAULT_FRESHNESS_WINDOW = Duration.ofMinutes(5);

    private UserDataProvider userDataProvider;
    private Duration freshnessWindow = DEFAULT_FRESHNESS_WINDOW;

    // A nonce only needs to be remembered for as long as its Created timestamp is still inside the
    // freshness window, but that's a rate, not a constant - so growth is additionally capped here.
    private static final int MAX_REMEMBERED_NONCES = 100_000;

    private final Map<String, Instant> seenNonces = new ConcurrentHashMap<>();
    private final AtomicLong lastPurgeEpochSecond = new AtomicLong();

    @Override
    protected void init() {
        if (userDataProvider == null) {
            throw new ConfigurationException("wsSecurity validate/usernameToken requires a userDataProvider.");
        }
        userDataProvider.init(parent.getRouter());
    }

    @Override
    void process(WsSecurityContext ctx) throws Exception {
        Element usernameToken = findSingleUsernameToken(ctx.security());

        Element passwordEl = getFirstChildByName(usernameToken, WSSE_NS, "Password");
        if (passwordEl == null) {
            throw new WsSecurityFaultException(INVALID_SECURITY_TOKEN,
                    "wsse:UsernameToken has no wsse:Password.");
        }
        String passwordType = passwordEl.getAttribute("Type");
        if (PASSWORD_DIGEST_TYPE.equals(passwordType)) {
            log.info("Rejecting wsse:UsernameToken with an unsupported PasswordDigest.");
            throw new WsSecurityFaultException(UNSUPPORTED_SECURITY_TOKEN,
                    "wsse:Password of type PasswordDigest is not supported.");
        }
        // An absent Type defaults to PasswordText per the UsernameToken profile. An unrecognized one
        // is refused rather than treated as text: the sender said the content is something this part
        // does not compute, so comparing it to the expected password would be testing the wrong value
        // - and would report a mismatch as a wrong password rather than as an unsupported token.
        if (!passwordType.isEmpty() && !PASSWORD_TEXT_TYPE.equals(passwordType)) {
            throw new WsSecurityFaultException(UNSUPPORTED_SECURITY_TOKEN,
                    "Unsupported wsse:Password Type \"" + passwordType + "\".");
        }

        Element usernameEl = getFirstChildByName(usernameToken, WSSE_NS, "Username");
        String username = usernameEl == null ? "" : usernameEl.getTextContent();
        String password = passwordEl.getTextContent();

        try {
            userDataProvider.verify(Map.of("username", username, "password", password));
        } catch (NoSuchElementException e) {
            throw new WsSecurityFaultException(FAILED_AUTHENTICATION, "Unknown username or wrong password.");
        }

        Element nonceEl = getFirstChildByName(usernameToken, WSSE_NS, "Nonce");
        Element createdEl = getFirstChildByName(usernameToken, WSU_NS, "Created");
        Instant created = createdEl == null ? null : parseCreated(createdEl.getTextContent());

        if (created != null) {
            checkFreshness(created);
        }
        if (nonceEl != null && created != null) {
            checkNonceNotReplayed(username, nonceEl.getTextContent(), created);
        }

        ctx.exchange().setProperty(SECURITY_SCHEMES, List.of(BASIC().username(username)));
    }

    /**
     * The one {@code wsse:UsernameToken} of the header. More than one is refused rather than resolved
     * by taking the first: which token a receiver picks would then decide whether the message
     * authenticates, and the ones this part ignored would still be forwarded to the backend.
     */
    private static Element findSingleUsernameToken(Element security) {
        List<Element> tokens = getChildrenByName(security, WSSE_NS, "UsernameToken");
        if (tokens.isEmpty()) {
            throw new WsSecurityFaultException(INVALID_SECURITY_TOKEN,
                    "wsse:Security carries no wsse:UsernameToken.");
        }
        if (tokens.size() > 1) {
            throw new WsSecurityFaultException(INVALID_SECURITY_TOKEN,
                    "More than one wsse:UsernameToken found; rejecting as ambiguous.");
        }
        return tokens.getFirst();
    }

    // OffsetDateTime, not Instant.parse: the latter only accepts a "Z" offset, while xs:dateTime
    // permits any (e.g. "+02:00") and SignatureValidatePart already accepts those on wsu:Timestamp.
    // Only the parsed value is normalized - the digest is computed over the original text.
    private Instant parseCreated(String value) {
        try {
            return OffsetDateTime.parse(value.trim()).toInstant();
        } catch (DateTimeParseException e) {
            throw new WsSecurityFaultException(INVALID_SECURITY_TOKEN,
                    "wsu:Created is not a valid xs:dateTime: " + value);
        }
    }

    private void checkFreshness(Instant created) {
        Instant now = Instant.now();
        if (created.isBefore(now.minus(freshnessWindow)) || created.isAfter(now.plus(freshnessWindow))) {
            throw new WsSecurityFaultException(FAILED_AUTHENTICATION,
                    "wsu:Created (" + created + ") is outside the allowed freshness window.");
        }
    }

    private void checkNonceNotReplayed(String username, String nonce, Instant created) {
        purgeExpiredNoncesIfDue();
        String key = username + '|' + nonce;
        if (seenNonces.size() >= MAX_REMEMBERED_NONCES) {
            purgeExpiredNonces();
            if (seenNonces.size() >= MAX_REMEMBERED_NONCES) {
                throw new WsSecurityFaultException(FAILED_AUTHENTICATION,
                        "Replay cache is full; rejecting the message.");
            }
        }
        if (seenNonces.putIfAbsent(key, created.plus(freshnessWindow)) != null) {
            throw new WsSecurityFaultException(FAILED_AUTHENTICATION,
                    "Nonce has already been used; rejecting as a replay.");
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

    public UserDataProvider getUserDataProvider() {
        return userDataProvider;
    }

    /**
     * @description Source that verifies the token's username/password, such as a static list, a
     * file, a JDBC database, or an LDAP directory. Only <code>PasswordText</code> tokens can be
     * checked this way; a <code>PasswordDigest</code> token is rejected.
     */
    @MCChildElement
    public void setUserDataProvider(UserDataProvider userDataProvider) {
        this.userDataProvider = userDataProvider;
    }

    public String getFreshnessWindow() {
        return freshnessWindow.toString();
    }

    /**
     * @description Tolerance, as an ISO-8601 duration, applied to a token's <code>wsu:Created</code>
     * and to how long its <code>wsse:Nonce</code> is remembered for replay detection. Only checked
     * when the token actually carries <code>wsu:Created</code>/<code>wsse:Nonce</code>. Nonces are
     * remembered per gateway instance, so a replay is caught once per instance rather than across a
     * cluster.
     * @default PT5M
     */
    @MCAttribute
    public void setFreshnessWindow(String freshnessWindow) {
        Duration parsed;
        try {
            parsed = Duration.parse(freshnessWindow);
        } catch (DateTimeParseException e) {
            throw new ConfigurationException("freshnessWindow \"" + freshnessWindow +
                    "\" is not a valid ISO-8601 duration. Use time-based units (days, hours, minutes, " +
                    "seconds), e.g. \"PT5M\" for 5 minutes; calendar units like months are not supported.", e);
        }
        if (!parsed.isPositive()) {
            throw new ConfigurationException("freshnessWindow must be a positive duration.");
        }
        this.freshnessWindow = parsed;
    }
}
