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
 * @description Adds one or more Set-Cookie headers to the HTTP response for session handling, user preferences, or tracking purposes.
 *              Supports computing expiry, setting standard attributes like Domain and Path, and building a compliant cookie string.
 *              Useful for enriching responses with configurable cookies directly at gateway level without backend involvement.
 * @topic 6. Misc
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
     * @description Registers the list of &lt;cookie&gt; child elements.
     * @param cookies list of CookieDef parsed from XML
     */
    @MCChildElement(order = 1)
    public void setCookies(List<CookieDef> cookies) {
        this.cookies = cookies;
    }

    /**
     * @description Holder for a single cookie's attributes.
     */
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

        /**
         * @description Sets the cookie name.
         * @param name cookie name
         */
        @MCAttribute
        @Required
        public void setName(String name) {
            this.name = name;
        }

        public String getValue() {
            return value;
        }

        /**
         * @description Sets the cookie value.
         * @param value cookie value
         */
        @MCAttribute
        @Required
        public void setValue(String value) {
            this.value = value;
        }

        public String getPath() {
            return path;
        }

        /**
         * @description Sets the cookie path.
         * @param path path value
         * @default "/"
         */
        @MCAttribute
        public void setPath(String path) {
            this.path = path;
        }

        public String getDomain() {
            return domain;
        }

        /**
         * @description Sets the cookie domain.
         * @param domain domain value
         */
        @MCAttribute
        public void setDomain(String domain) {
            this.domain = domain;
        }

        public int getMaxAge() {
            return maxAge;
        }

        /**
         * @description Sets the Max-Age for the cookie.
         * @param maxAge seconds until expiration
         * @default -1
         */
        @MCAttribute
        public void setMaxAge(int maxAge) {
            this.maxAge = maxAge;
        }

        public String getExpires() {
            return expires;
        }

        /**
         * @description Sets the Expires attribute.
         * @param expires RFC1123 date string
         */
        @MCAttribute
        public void setExpires(String expires) {
            this.expires = expires;
        }

        public boolean isSecure() {
            return secure;
        }

        /**
         * @description Sets the Secure flag.
         * @param secure true to mark cookie Secure
         * @default false
         */
        @MCAttribute
        public void setSecure(boolean secure) {
            this.secure = secure;
        }

        public boolean isHttpOnly() {
            return httpOnly;
        }

        /**
         * @description Sets the HttpOnly flag.
         * @param httpOnly true to mark cookie HttpOnly
         * @default false
         */
        @MCAttribute
        public void setHttpOnly(boolean httpOnly) {
            this.httpOnly = httpOnly;
        }

        public SameSite getSameSite() {
            return sameSite;
        }

        /**
         * @description Sets the SameSite policy.
         * @param sameSite one of LAX, STRICT, or NONE
         * @default LAX
         */
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
