package com.predic8.membrane.core.interceptor.session;

import com.predic8.membrane.annot.MCAttribute;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.Header;
import com.predic8.membrane.core.http.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public abstract class SessionManager {

    Logger log = LoggerFactory.getLogger(SessionManager.class);

    public static final String SESSION = "SESSION";

    long timeout;
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
     * Get all cookies String representations from the request that are not valid anymore, e.g. because the cookie is a self contained value and has changed (e.g. jwt)
     * @param exc
     * @param validCookie is the cookie value representation of the currently active session
     * @return
     */
    public abstract List<String> getInvalidCookies(Exchange exc, String validCookie);

    public void postProcess(Exchange exc) {
        if (exc.getProperty(SESSION) == null)
            return;

        try {
            if (exc.getResponse() == null)
                exc.setResponse(new Response());

            setCookieHeaderHandling(exc, getCurrentSessionCookieValue((Session) exc.getProperty(SESSION)));
        } catch (Exception e) {
            throw new RuntimeException("The newly created session could not be persisted in the Set-Cookie header", e);
        }
    }

    private void setCookieHeaderHandling(Exchange exc, String currentSessionCookieValue) {
        setCookieForCurrentSession(exc, currentSessionCookieValue);
        setCookieForExpiredSessions(exc, currentSessionCookieValue);
    }

    private void setCookieForCurrentSession(Exchange exc, String currentSessionCookieValue) {
        exc.getResponse().getHeader().setValue(Header.SET_COOKIE, currentSessionCookieValue);
    }

    private void setCookieForExpiredSessions(Exchange exc, String currentSessionCookieValue) {
        cookiesToExpire(exc, currentSessionCookieValue).stream().forEach(cookie -> {
            exc.getResponse().getHeader().add(Header.SET_COOKIE, cookie);
        });
    }

    private List<String> cookiesToExpire(Exchange exc, String currentSessionCookieValue) {
        List<String> removeCookiesValue = new ArrayList<>();
        if (exc.getRequest().getHeader().getFirstValue(Header.COOKIE) != null)
            removeCookiesValue.addAll(expireCookies(getInvalidCookies(exc, currentSessionCookieValue)));
        return removeCookiesValue;
    }

    private String getCurrentSessionCookieValue(Session s) throws Exception {
        return getCookieValues(s).values().stream().findFirst().orElseThrow(Exception::new) + "=true";
    }

    private List<String> expireCookies(List<String> invalidCookies) {
        return invalidCookies
                .stream()
                .map(cookie -> cookie + "; Expires=Thursday, 01-January-1970 00:00:00 GMT")
                .collect(Collectors.toList());
    }



    protected Session getSessionInternal(Exchange exc) {
        if (exc.getRequest().getHeader().getFirstValue(Header.COOKIE) == null)
            return new Session(new HashMap<>());

        return new Session(mergeCookies(exc));
    }

    private Map<String, Object> mergeCookies(Exchange exc) {
        return convertCookiesToAttributes(exc)
                .stream()
                .flatMap(map -> map.entrySet().stream())
                .collect(Collectors.toMap(entry -> entry.getKey(), entry -> entry.getValue(), (e1, e2) -> e1 + "," + e2));
    }

    private List<Map<String, Object>> convertCookiesToAttributes(Exchange exc) {
        return Stream
                .of(getCookies(exc))
                .map(cookie -> cookieValueToAttributes(cookie.split("=")[0]))
                .collect(Collectors.toList());
    }

    public Session getSession(Exchange exc) {
        Session s = (Session) exc.getProperty(SESSION);
        if (s != null)
            return s;
        s = getSessionInternal(exc);
        exc.setProperty(SESSION, s);
        return s;
    }


    protected String[] getCookies(Exchange exc) {
        return exc.getRequest().getHeader().getFirstValue(Header.COOKIE).split(";");
    }


    public long getTimeout() {
        return timeout;
    }

    @MCAttribute
    public void setTimeout(long timeout) {
        this.timeout = timeout;
    }

    public String getDomain() {
        return domain;
    }

    @MCAttribute
    public void setDomain(String domain) {
        this.domain = domain;
    }


}
