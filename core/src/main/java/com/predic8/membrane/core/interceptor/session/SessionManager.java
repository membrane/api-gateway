package com.predic8.membrane.core.interceptor.session;

import com.predic8.membrane.annot.MCAttribute;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.Header;
import com.predic8.membrane.core.http.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public abstract class SessionManager {

    public static final String SESSION_VALUE_SEPARATOR = ",";
    Logger log = LoggerFactory.getLogger(SessionManager.class);

    public static final String SESSION = "SESSION";

    long expiresAfterSeconds = 15 * 60;
    String domain;

    /**
     * Transforms a cookie value into a freshly (new'ed) session instance.
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

    public void postProcess(Exchange exc) {
        getSessionFromExchange(exc).ifPresent(session -> {
            try {
                createDefaultResponseIfNeeded(exc);
                handleSetCookieHeaderForResponse(exc, getCookieValue(session));
            } catch (Exception e) {
                throw new RuntimeException("The newly created session could not be persisted in the Set-Cookie header", e);
            }
        });
    }

    private void createDefaultResponseIfNeeded(Exchange exc) {
        if (exc.getResponse() == null)
            exc.setResponse(new Response());
    }

    private void handleSetCookieHeaderForResponse(Exchange exc, String currentSessionCookieValue) {
        if (!cookieIsAlreadySet(exc, currentSessionCookieValue))
            setCookieForCurrentSession(exc, currentSessionCookieValue);
        setCookieForExpiredSessions(exc, currentSessionCookieValue);
    }

    private boolean cookieIsAlreadySet(Exchange exc, String currentSessionCookieValue) {
        return Optional
                .ofNullable(getCookieHeader(exc))
                .orElseGet(String::new)
                .contains(currentSessionCookieValue);
    }

    private void setCookieForCurrentSession(Exchange exc, String currentSessionCookieValue) {
        exc.getResponse().getHeader()
                .setValue(Header.SET_COOKIE, currentSessionCookieValue
                        + ";" + String.join(";", createCookieAttributes(exc)));
    }

    private void setCookieForExpiredSessions(Exchange exc, String currentSessionCookieValue) {
        cookiesToExpire(exc, currentSessionCookieValue).stream()
                .forEach(cookie -> exc.getResponse().getHeader().add(Header.SET_COOKIE, cookie));
    }

    private List<String> cookiesToExpire(Exchange exc, String currentSessionCookieValue) {
        if (getCookieHeader(exc) != null)
            return expireCookies(getInvalidCookies(exc, currentSessionCookieValue));

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
        if (getCookieHeader(exc) == null)
            return new Session(new HashMap<>());

        return new Session(mergeCookies(exc));
    }

    private Map<String, Object> mergeCookies(Exchange exc) {
        return convertCookiesToAttributes(exc)
                .stream()
                .flatMap(map -> map.entrySet().stream())
                .collect(Collectors.toMap(entry -> entry.getKey(), entry -> entry.getValue(), (e1, e2) -> e1 + SESSION_VALUE_SEPARATOR + e2));
    }

    private List<Map<String, Object>> convertCookiesToAttributes(Exchange exc) {
        return Stream
                .of(getCookies(exc))
                .map(cookie -> cookieValueToAttributes(cookie.split("=")[0]))
                .collect(Collectors.toList());
    }

    public Session getSession(Exchange exc) {
        return getSessionFromExchange(exc).orElse(getSessionFromManager(exc));
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
                exc.getRule().getSslInboundContext() != null ? "Secure" : null
        )
                .filter(attr -> attr != null)
                .collect(Collectors.toList());
    }

    public List<String> createInvalidationAttributes() {
        return Stream.of(
                "Expires=Thu, 01 Jan 1970 00:00:00 GMT"
        ).collect(Collectors.toList());
    }


    protected String[] getCookies(Exchange exc) {
        return getCookieHeader(exc).split(";");
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
        return exc.getRequest().getHeader().getFirstValue(Header.COOKIE);
    }
}
