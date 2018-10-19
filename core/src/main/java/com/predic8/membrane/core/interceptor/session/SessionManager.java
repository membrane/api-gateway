package com.predic8.membrane.core.interceptor.session;

import com.predic8.membrane.annot.MCAttribute;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.Header;
import com.predic8.membrane.core.http.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public abstract class SessionManager {

    Logger log = LoggerFactory.getLogger(SessionManager.class);

    public static final String NEW_SESSION = "NEW_SESSION";
    public static final String REMOVE_COOKIES = "REMOVE_COOKIES";
    long timeout;
    String domain;

    /**
     * Transforms a cookie value into a freshly (new'ed) session instance.
     *
     * @param cookie
     * @return
     */
    protected abstract Map<String, String> cookieValueToAttributes(String cookie);

    /**
     * Get the String identifier of the sessions to be used as cookie value.
     *
     * @param session
     * @return
     */
    protected abstract Map<Session, String> getCookieValues(Session... session);

    public abstract long getExpiration(Session session);

    public void postProcess(Exchange exc) {
        Session s = (Session) exc.getProperty(NEW_SESSION);
        exc.getProperties().remove(NEW_SESSION);
        if (s == null)
            return;
        try {
            cleanup(exc);

            String additionalCookieValue = getCookieValues(s).values().stream().findFirst().orElseThrow(Exception::new) + "=true";
            String removeCookiesValue = null;


            String setCookieValue = additionalCookieValue + (removeCookiesValue != null ? "," + removeCookiesValue : "");

            if (exc.getResponse() == null)
                exc.setResponse(new Response());
            exc.getResponse().getHeader().setValue(Header.SET_COOKIE, setCookieValue);

        } catch (Exception e) {
            throw new RuntimeException("The newly created session could not be persisted in the Set-Cookie header", e);
        }
    }

    public void cleanup(Exchange exc) {

    }

    protected Session getSessionInternal(Exchange exc) {
        if (exc.getRequest().getHeader().getFirstValue(Header.COOKIE) == null)
            return new Session(new HashMap<>());
        String[] cookieValues = getCookies(exc);

        List<Map<String, String>> jwts = Stream
                .of(cookieValues)
                .map(cookie -> cookieValueToAttributes(cookie.split("=")[0]))
                .collect(Collectors.toList());

        Map<String, String> merged = jwts
                .stream()
                .flatMap(map -> map.entrySet().stream())
                .collect(Collectors.toMap(entry -> entry.getKey(), entry -> entry.getValue(), (e1, e2) -> e1 + "," + e2));

        return new Session(merged);
    }

    public Session getSession(Exchange exc) {
        Session s = (Session) exc.getProperty(NEW_SESSION);
        if (s != null)
            return s;
        s = getSessionInternal(exc);
        exc.setProperty(NEW_SESSION, s);
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
