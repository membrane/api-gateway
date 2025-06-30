package com.predic8.membrane.core.interceptor.lang;

import com.predic8.membrane.annot.MCAttribute;
import com.predic8.membrane.annot.MCChildElement;
import com.predic8.membrane.annot.MCElement;
import com.predic8.membrane.annot.Required;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.interceptor.AbstractInterceptor;
import com.predic8.membrane.core.interceptor.Outcome;

import java.util.ArrayList;
import java.util.List;

import static com.predic8.membrane.core.interceptor.Outcome.CONTINUE;

/**
 * Interceptor that adds one or more Set-Cookie headers on the response.
 */
@MCElement(name = "setCookies")
public class SetCookiesInterceptor extends AbstractInterceptor {

    /**
     * Holder for a single cookie's attributes.
     */
    private List<CookieDef> cookies = new ArrayList<>();

    @Override
    public Outcome handleResponse(Exchange exc) {
        for (CookieDef def : cookies) {
            exc.getResponse().getHeader().add("Set-Cookie", def.buildHeader());
        }
        return CONTINUE;
    }

    public List<CookieDef> getCookies() {
        return cookies;
    }

    /**
     * Register one <cookie> child element.
     */
    @MCChildElement(order = 1)
    public void setCookies(List<CookieDef> cookies) {
        this.cookies = cookies;
    }

    @SuppressWarnings("unused")
    @MCElement(name = "cookie")
    public static class CookieDef {
        private String domain;
        private String path = "/";
        private String expires; // RFC1123 date format
        private int maxAge = -1;
        private boolean secure = false;
        private boolean httpOnly = false;
        private SameSite sameSite = SameSite.LAX;

        private String name;
        private String value;

        public String getName() {
            return name;
        }

        @MCAttribute
        @Required
        public void setName(String name) {
            this.name = name;
        }

        public String getValue() {
            return value;
        }

        @MCAttribute
        @Required
        public void setValue(String value) {
            this.value = value;
        }

        public String getPath() {
            return path;
        }

        @MCAttribute
        public void setPath(String path) {
            this.path = path;
        }

        public String getDomain() {
            return domain;
        }

        @MCAttribute
        public void setDomain(String domain) {
            this.domain = domain;
        }

        public int getMaxAge() {
            return maxAge;
        }

        @MCAttribute
        public void setMaxAge(int maxAge) {
            this.maxAge = maxAge;
        }

        public String getExpires() {
            return expires;
        }

        @MCAttribute
        public void setExpires(String expires) {
            this.expires = expires;
        }

        public boolean isSecure() {
            return secure;
        }

        @MCAttribute
        public void setSecure(boolean secure) {
            this.secure = secure;
        }

        public boolean isHttpOnly() {
            return httpOnly;
        }

        @MCAttribute
        public void setHttpOnly(boolean httpOnly) {
            this.httpOnly = httpOnly;
        }

        public SameSite getSameSite() {
            return sameSite;
        }

        @MCAttribute
        public void setSameSite(SameSite sameSite) {
            this.sameSite = sameSite;
        }

        @Override
        public String toString() {
            return buildHeader();
        }

        /**
         * Build the Set-Cookie header string with proper attributes.
         */
        public String buildHeader() {
            StringBuilder sb = new StringBuilder();
            if (name.contains("\r") || name.contains("\n") || value.contains("\r") || value.contains("\n"))
                throw new IllegalArgumentException("Cookie attributes must not contain CR/LF characters.");
            sb.append(name).append("=").append(value);
            sb.append("; Path=").append(path);
            if (domain != null) sb.append("; Domain=").append(domain);
            if (maxAge >= 0) sb.append("; Max-Age=").append(maxAge);
            if (expires != null) sb.append("; Expires=").append(expires);
            if (httpOnly) sb.append("; HttpOnly");
            if (sameSite != null) {
                sb.append("; SameSite=").append(sameSite.name());
                if (sameSite == SameSite.NONE && !secure) {
                    sb.append("; Secure");
                }
            }
            if (secure) sb.append("; Secure");


            return sb.toString();
        }

        public enum SameSite {LAX, STRICT, NONE}
    }

}
