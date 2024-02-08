/* Copyright 2024 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */
package com.predic8.membrane.core.interceptor.oauth2client.rf;

import com.predic8.membrane.annot.MCAttribute;
import com.predic8.membrane.annot.MCElement;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.interceptor.oauth2.authorizationservice.AuthorizationService;
import com.predic8.membrane.core.rules.RuleKey;

import javax.annotation.concurrent.GuardedBy;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import static com.predic8.membrane.core.http.Header.X_FORWARDED_PROTO;

@MCElement(name = "publicURL")
public class PublicUrlManager {
    @GuardedBy("publicURLs")
    private final List<String> publicURLs = new ArrayList<>();
    private boolean initPublicURLsOnTheFly = false;
    private boolean firstInitWhenDynamicAuthorizationService;

    private AuthorizationService auth;
    private String callbackPath;

    public void init(AuthorizationService auth, String callbackPath) {
        this.auth = auth;
        this.callbackPath = callbackPath;

        synchronized (publicURLs) {
            if (publicURLs.isEmpty()) {
                initPublicURLsOnTheFly = true;
            } else {
                publicURLs.replaceAll(this::normalizePublicURL);
            }
        }

        firstInitWhenDynamicAuthorizationService = auth.supportsDynamicRegistration();
        if (!auth.supportsDynamicRegistration()) {
            firstInitWhenDynamicAuthorizationService = false;
        }
    }

    public String getPublicURL() {
        synchronized (publicURLs) {
            return String.join(" ", publicURLs);
        }
    }

    @MCAttribute
    public void setPublicURL(String publicURL) {
        synchronized (publicURLs) {
            publicURLs.clear();
            Collections.addAll(publicURLs, publicURL.split("[ \t]+"));
        }
    }

    public String normalizePublicURL(String url) {
        if (!url.endsWith("/"))
            url += "/";
        return url;
    }

    public String getPublicURL(Exchange exc) throws Exception {
        String xForwardedProto = exc.getRequest().getHeader().getFirstValue(X_FORWARDED_PROTO);
        boolean isHTTPS = xForwardedProto != null ? "https".equals(xForwardedProto) : exc.getRule().getSslInboundContext() != null;
        String publicURL = (isHTTPS ? "https://" : "http://") + exc.getOriginalHostHeader();
        RuleKey key = exc.getRule().getKey();
        if (!key.isPathRegExp() && key.getPath() != null) publicURL += key.getPath();
        publicURL = normalizePublicURL(publicURL);

        synchronized (publicURLs) {
            if (publicURLs.contains(publicURL)) return publicURL;
            if (!initPublicURLsOnTheFly) return publicURLs.get(0);
        }

        String newURL = null;
        if (initPublicURLsOnTheFly) newURL = addPublicURL(publicURL);

        if (firstInitWhenDynamicAuthorizationService && newURL != null)
            auth.dynamicRegistration(getPublicURLs().stream().map(url -> url + callbackPath).collect(Collectors.toList()));

        return publicURL;
    }

    /**
     * @return the new public URL, if a new one was added. null if the URL is not new.
     */
    private String addPublicURL(String publicURL) {
        synchronized (publicURLs) {
            if (publicURLs.contains(publicURL)) return null;
            publicURLs.add(publicURL);
        }
        return publicURL;
    }

    private List<String> getPublicURLs() {
        synchronized (publicURLs) {
            return new ArrayList<>(publicURLs);
        }
    }

}
