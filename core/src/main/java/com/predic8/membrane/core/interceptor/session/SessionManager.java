/* Copyright 2019 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */
package com.predic8.membrane.core.interceptor.session;

import com.predic8.membrane.annot.MCAttribute;
import com.predic8.membrane.core.Router;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.Header;
import com.predic8.membrane.core.http.HeaderName;
import com.predic8.membrane.core.http.Response;
import com.predic8.membrane.core.rules.RuleKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public abstract class SessionManager {

    public static final String SESSION_VALUE_SEPARATOR = ",";
    Logger log = LoggerFactory.getLogger(SessionManager.class);

    public static final String SESSION = "SESSION";
    public static final String SESSION_COOKIE_ORIGINAL = "SESSION_COOKIE_ORIGINAL";

    protected String usernameKeyName = "username";
    long expiresAfterSeconds = 15 * 60;
    String domain;
    boolean httpOnly = false;
    String sameSite = null;

    String issuer;
    protected boolean ttlExpiryRefreshOnAccess = true;
    protected boolean secure = false;

    protected String cookieNamePrefix = UUID.randomUUID().toString().substring(0,8);

    private void initIssuer(Exchange exc) {
        String xForwardedProto = exc.getRequest().getHeader().getFirstValue(Header.X_FORWARDED_PROTO);
        boolean isHTTPS = xForwardedProto != null ? "https".equals(xForwardedProto) : exc.getRule().getSslInboundContext() != null;
        issuer = (isHTTPS ? "https://" : "http://") + exc.getOriginalHostHeader();
        RuleKey key = exc.getRule().getKey();
        if (!key.isPathRegExp() && key.getPath() != null)
            issuer += key.getPath();
        normalizePublicURL();
    }

    private void normalizePublicURL() {
        if(!issuer.endsWith("/"))
            issuer += "/";
    }

    public abstract void init(Router router) throws Exception;

    /**
     * Transforms a cookie value into its attributes. The cookie should be assumed valid as @isValidCookieForThisSessionManager was called beforehand
     *
     * @param cookie
     * @return
     */
    protected abstract Map<String, Object> cookieValueToAttributes(String cookie);

    /**
     * Get the String identifier of the sessions to be used as cookie value.
     *
     * @param session
     * @return
     */
    protected abstract Map<Session, String> getCookieValues(Session... session);

    /**
     * Get all cookies String representations from the request that are not valid anymore, e.g. because the cookie is a self contained value and has changed or expired (e.g. jwt).
     * Should return cookie values in the form of key=value.
     *
     * @param exc
     * @param validCookie is the cookie value representation of the currently active session. Is key=value
     * @return
     */
    public abstract List<String> getInvalidCookies(Exchange exc, String validCookie);

    /**
     * Gets called for every cookie value. Returns if the cookie value is valid and managed by this manager instance, e.g. jwt session manager checks if the cookie is a jwt, if it has the correct issuer, if it is not expired and if the signature is valid.
     * Cookie is in format key=value
     * @param cookie
     * @return
     */
    protected abstract boolean isValidCookieForThisSessionManager(String cookie);


    /**
     * Gets called when session was not modified. Should check, if session needs to be renewed (e.g. jwt expiration).
     * @param originalCookie the original cookie from which the session was created (can be different from current session)
     * @return
     */
    protected abstract boolean cookieRenewalNeeded(String originalCookie);


    public void postProcess(Exchange exc) {
        synchronized (this) {
            if (issuer == null)
                initIssuer(exc);
        }

        getSessionFromExchange(exc).ifPresent(session -> {
            try {
                createDefaultResponseIfNeeded(exc);
                handleSetCookieHeaderForResponse(exc, session);
            } catch (Exception e) {
                throw new RuntimeException("The newly created session could not be persisted in the Set-Cookie header", e);
            }
        });
    }

    private void createDefaultResponseIfNeeded(Exchange exc) {
        if (exc.getResponse() == null)
            exc.setResponse(Response.ok().build());
    }

    private void handleSetCookieHeaderForResponse(Exchange exc, Session session) throws Exception {
        Optional<Object> originalCookieValueAtBeginning = Optional.ofNullable(exc.getProperty(SESSION_COOKIE_ORIGINAL));

        if(ttlExpiryRefreshOnAccess || session.isDirty() || !originalCookieValueAtBeginning.isPresent() || cookieRenewalNeeded(originalCookieValueAtBeginning.get().toString())){
            String currentCookieValueOfSession = getCookieValue(session);
            if (!ttlExpiryRefreshOnAccess && originalCookieValueAtBeginning.isPresent() &&
                    originalCookieValueAtBeginning.get().toString().trim().equals(currentCookieValueOfSession))
                return;
            setCookieForExpiredSessions(exc, currentCookieValueOfSession);
            setCookieForCurrentSession(exc, currentCookieValueOfSession);
            mergeSameSetCookieHeaders(exc);
        }
    }

    private void mergeSameSetCookieHeaders(Exchange exc) {
        // TODO
    }

    private boolean cookieIsAlreadySet(Exchange exc, String currentSessionCookieValue) {
        return Optional
                .ofNullable(getCookieHeader(exc))
                .orElseGet(String::new)
                .contains(currentSessionCookieValue);
    }

    private void setCookieForCurrentSession(Exchange exc, String currentSessionCookieValue) {
        if (currentSessionCookieValue.length() > 4093)
            log.warn("Cookie is larger than 4093 bytes, this will not work some browsers.");
        exc.getResponse().getHeader()
                .add(Header.SET_COOKIE, currentSessionCookieValue
                        + ";" + String.join(";", createCookieAttributes(exc)));
    }

    private void setCookieForExpiredSessions(Exchange exc, String currentSessionCookieValue) {
        cookiesToExpire(exc, currentSessionCookieValue).stream()
                .forEach(cookie -> exc.getResponse().getHeader().add(Header.SET_COOKIE, cookie));
    }

    private List<String> cookiesToExpire(Exchange exc, String currentSessionCookieValue) {
        if (getCookieHeader(exc) != null)
            return expireCookies(getInvalidCookies(exc, ttlExpiryRefreshOnAccess ? UUID.randomUUID().toString() : currentSessionCookieValue));

        return new ArrayList<>();
    }

    private String getCookieValue(Session s) throws Exception {
        return getCookieValues(s)
                .values()
                .stream()
                .findFirst()
                .orElseThrow(Exception::new) + "=true";
    }

    private List<String> expireCookies(List<String> invalidCookies) {
        return invalidCookies
                .stream()
                .map(cookie -> cookie + ";" + String.join(";", createInvalidationAttributes()))
                .collect(Collectors.toList());
    }

    protected Session getSessionInternal(Exchange exc) {
        exc.setProperty(SESSION_COOKIE_ORIGINAL,null);
        if (getCookieHeader(exc) == null)
            return new Session(usernameKeyName, new HashMap<>());

        Map<String, Map<String, Object>> validCookiesAsListOfMaps = convertValidCookiesToAttributes(exc);
        Session session = new Session(usernameKeyName, mergeCookies(validCookiesAsListOfMaps.values().stream().collect(Collectors.toList())));

        if(validCookiesAsListOfMaps.size() == 1)
            exc.setProperty(SESSION_COOKIE_ORIGINAL,validCookiesAsListOfMaps.keySet().iterator().next());

        return session;
    }

    private Map<String, Object> mergeCookies(List<Map<String,Object>> validCookiesAsListOfMaps) {
        return validCookiesAsListOfMaps
                .stream()
                .flatMap(map -> map.entrySet().stream())
                .collect(Collectors.toMap(entry -> entry.getKey(), entry -> entry.getValue(), (e1, e2) -> e1 != null && e1.equals(e2) ? e1 : e1 + SESSION_VALUE_SEPARATOR + e2));
    }

    private Map<String,Map<String, Object>> convertValidCookiesToAttributes(Exchange exc) {
        return getCookies(exc)
                .filter(cookie -> isValidCookieForThisSessionManager(cookie))
                .collect(Collectors.toMap(cookie -> cookie, cookie -> cookieValueToAttributes(cookie), (c1,c2) -> c1));
    }

    public Session getSession(Exchange exc) {
        Optional<Session> sessionFromExchange = getSessionFromExchange(exc);
        if(sessionFromExchange.isPresent()) // have to do it like this and not with .orElse because getSessionFromManager would be called unnecessarily (overwriting session property)
            return sessionFromExchange.get();

        return getSessionFromManager(exc);
    }

    private Session getSessionFromManager(Exchange exc) {
        exc.setProperty(SESSION, getSessionInternal(exc));
        return getSessionFromExchange(exc).get();
    }


    private Optional<Session> getSessionFromExchange(Exchange exc) {
        return Optional.ofNullable((Session) exc.getProperty(SESSION));
    }

    public List<String> createCookieAttributes(Exchange exc) {
        return Stream.of(
                "Max-Age=" + expiresAfterSeconds,
                "Expires=" + DateTimeFormatter.RFC_1123_DATE_TIME.format(OffsetDateTime.now(ZoneOffset.UTC).plus(Duration.ofSeconds(expiresAfterSeconds))),
                "Path=/",

                needsSecureAttribute(exc) ? "Secure" : null,
                domain != null ? "Domain=" + domain + "; " : null,
                httpOnly ? "HttpOnly" : null,
                sameSite != null ? "SameSite="+sameSite : null
        )
                .filter(attr -> attr != null)
                .collect(Collectors.toList());
    }

    private boolean needsSecureAttribute(Exchange exc) {
        return exc.getRule().getSslInboundContext() != null || secure;
    }

    public List<String> createInvalidationAttributes() {
        return Stream.of(
                "Expires=Thu, 01 Jan 1970 00:00:00 GMT",
                "Path=/",
                domain != null ? "Domain=" + domain + "; " : null
        )
                .filter(attr -> attr != null)
                .collect(Collectors.toList());
    }


    protected Stream<String> getCookies(Exchange exc) {
        return exc.getRequest().getHeader().getValues(new HeaderName(Header.COOKIE)).stream().map(s -> s.getValue().split(";")).flatMap(Arrays::stream).map(c -> c.trim());
    }


    public long getExpiresAfterSeconds() {
        return expiresAfterSeconds;
    }

    @MCAttribute
    public void setExpiresAfterSeconds(long expiresAfterSeconds) {
        this.expiresAfterSeconds = expiresAfterSeconds;
    }

    public String getDomain() {
        return domain;
    }

    @MCAttribute
    public void setDomain(String domain) {
        this.domain = domain;
    }


    protected String getCookieHeader(Exchange exc) {
        return getCookies(exc).collect(Collectors.joining(";"));
    }

    public boolean isHttpOnly() {
        return httpOnly;
    }

    @MCAttribute
    public void setHttpOnly(boolean httpOnly) {
        this.httpOnly = httpOnly;
    }

    public String getSameSite() {
        return sameSite;
    }

    @MCAttribute
    public void setSameSite(String sameSite) {
        this.sameSite = sameSite;
    }

    protected String[] getAllCookieKeys(Exchange exc) {
        return getCookieHeader(exc).split(Pattern.quote(";"));
    }

    public String getIssuer() {
        return issuer;
    }

    @MCAttribute
    public void setIssuer(String issuer) {
        this.issuer = issuer;
    }

    public boolean isTtlExpiryRefreshOnAccess() {
        return ttlExpiryRefreshOnAccess;
    }

    /**
     * @description controls if the expiry refreshes to expiresAfterSeconds on access (true) or if it should not refresh (false)
     * @default true
     */
    @MCAttribute
    public void setTtlExpiryRefreshOnAccess(boolean ttlExpiryRefreshOnAccess) {
        this.ttlExpiryRefreshOnAccess = ttlExpiryRefreshOnAccess;
    }

    public boolean isSecure() {
        return secure;
    }

    /**
     * @description override setting secure attribute on cookie even when not using TLS (example usecase: Membrane behind TLS terminating firewall)
     * @default false
     */
    @MCAttribute
    public SessionManager setSecure(boolean secure) {
        this.secure = secure;
        return this;
    }
}
