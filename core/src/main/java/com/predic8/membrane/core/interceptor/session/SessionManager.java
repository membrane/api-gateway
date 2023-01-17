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

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.predic8.membrane.annot.MCAttribute;
import com.predic8.membrane.core.Router;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.Header;
import com.predic8.membrane.core.http.HeaderField;
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
    public static final String VALUE_TO_EXPIRE_SESSION_IN_BROWSER = "Expires=Thu, 01 Jan 1970 00:00:00 GMT";
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
    protected boolean sessionCookie = false;

    Cache<String,String> cookieExpireCache = CacheBuilder.newBuilder()
            .maximumSize(10000)
            .expireAfterWrite(Duration.ofSeconds(10))
            .build();

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

            // expires old cookies and sets new cookie for the current session
            setCookieForCurrentSession(exc, currentCookieValueOfSession);
            setCookieForExpiredSessions(exc, currentCookieValueOfSession);

            // if old and new session are the same then drop redundant set cookie headers
            dropRedundantCookieHeaders(exc);

            cacheSetCookie(exc, currentCookieValueOfSession);
        }
    }

    private void cacheSetCookie(Exchange exc, String currentSessionCookieValue) {
        Optional<HeaderField> setCookie = getAllRelevantSetCookieHeaders(exc).filter(e -> e.getValue().contains(currentSessionCookieValue)).findFirst();
        if(setCookie.isPresent())
            synchronized (cookieExpireCache) {
                cookieExpireCache.put(currentSessionCookieValue, setCookie.get().getValue());
            }
    }

    private void dropRedundantCookieHeaders(Exchange exc) {
        Map<String, List<String>> setCookieHeaders = getAllRelevantSetCookieHeaders(exc)
                .map(hf -> hf.getValue())
                .map(v -> new AbstractMap.SimpleEntry(v.split("=true")[0], Arrays.asList(v)))
                .collect(Collectors.toMap(e -> (String)e.getKey(), e -> (List)e.getValue(), (a,b) -> Stream.concat(a.stream(),b.stream()).collect(Collectors.toList())));

        removeRedundantExpireCookieIfRefreshed(exc, setCookieHeaders);

        // TODO - does not work as expected as sometimes a newly issued cookie is removed
//        removeRefreshIfNoChangeInExpireTime(exc,setCookieHeaders);
    }

    private Stream<HeaderField> getAllRelevantSetCookieHeaders(Exchange exc) {
        return Arrays.stream(exc.getResponse().getHeader().getAllHeaderFields())
                .filter(hf -> hf.getHeaderName().toString().contains(Header.SET_COOKIE))
                .filter(hf -> hf.getValue().contains("=true"))
                .filter(hf -> isValidCookieForThisSessionManager(Arrays.stream(hf.getValue().split(";")).filter(s -> s.contains("=true")).findFirst().get()));
    }

    private void removeRefreshIfNoChangeInExpireTime(Exchange exc, Map<String, List<String>> setCookieHeaders) {
        synchronized (cookieExpireCache) {
            setCookieHeaders.entrySet().stream().collect(Collectors.toList()).stream() // copy so that map is modifiable
                    .filter(e -> cookieExpireCache.getIfPresent(e.getKey() + "=true") != null)
                    .forEach(e -> {
                        e.getValue().stream().forEach(cookieEntry -> {
                            String cookie = cookieExpireCache.getIfPresent(e.getKey() + "=true");
                            if (cookieEntry.equals(cookie)) {
                                setCookieHeaders.get(e.getKey()).remove(e.getValue());
                                exc.getResponse().getHeader().remove(getAllRelevantSetCookieHeaders(exc)
                                        .filter(hf -> hf.getValue().contains(cookieEntry))
                                        .findFirst().get());
                            }
                        });
                    });
        }
    }

    private void removeRedundantExpireCookieIfRefreshed(Exchange exc, Map<String, List<String>> setCookieHeaders) {
        setCookieHeaders.entrySet().stream().collect(Collectors.toList()).stream() // copy so that map is modifiable
                .filter(e -> e.getValue().size() > 1)
                .filter(e -> e.getValue().stream().filter(s -> s.contains(VALUE_TO_EXPIRE_SESSION_IN_BROWSER)).count() == 1)
                .forEach(e -> {
                    setCookieHeaders.get(e.getKey()).remove(e.getValue());
                    exc.getResponse().getHeader().remove(getAllRelevantSetCookieHeaders(exc)
                            .filter(hf -> hf.getValue().contains(VALUE_TO_EXPIRE_SESSION_IN_BROWSER))
                            .findFirst().get());
                });
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
        String setCookieValue = currentSessionCookieValue
                + ";" + String.join(";", createCookieAttributes(exc));
        exc.getResponse().getHeader()
                .add(Header.SET_COOKIE, setCookieValue);
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
                sessionCookie ? null : "Max-Age=" + expiresAfterSeconds,
                sessionCookie ? null : "Expires=" + DateTimeFormatter.RFC_1123_DATE_TIME.format(OffsetDateTime.now(ZoneOffset.UTC).plus(Duration.ofSeconds(expiresAfterSeconds))),
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
                VALUE_TO_EXPIRE_SESSION_IN_BROWSER,
                "Path=/",
                domain != null ? "Domain=" + domain + "; " : null,
                sameSite != null ? "SameSite="+sameSite : null
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
     * @description forces secure cookie attribute even when no ssl context is present (e.g. TLS termination in front of membrane)
     * @default false
     */
    @MCAttribute
    public SessionManager setSecure(boolean secure) {
        this.secure = secure;
        return this;
    }

    public boolean isSessionCookie() {
        return sessionCookie;
    }

    /**
     * @description if true removes the expire part of a set cookie header and thus makes it a session cookie
     * @default false
     */
    @MCAttribute
    public SessionManager setSessionCookie(boolean sessionCookie) {
        this.sessionCookie = sessionCookie;
        return this;
    }
}
