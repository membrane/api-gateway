/* Copyright 2020 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */
package com.predic8.membrane.core.interceptor.oauth2client;

import com.google.common.collect.ImmutableList;
import com.predic8.membrane.annot.MCElement;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.exchange.snapshots.AbstractExchangeSnapshot;
import com.predic8.membrane.core.http.HeaderName;
import com.predic8.membrane.core.interceptor.oauth2client.rf.StateManager;
import com.predic8.membrane.core.interceptor.session.Session;
import com.predic8.membrane.core.proxies.SSLableProxy;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.predic8.membrane.core.http.Header.COOKIE;
import static com.predic8.membrane.core.http.Header.SET_COOKIE;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.time.ZoneOffset.UTC;

@MCElement(name = "cookieOriginalExchangeStore")
public class CookieOriginialExchangeStore extends OriginalExchangeStore {
    public static final String ORIGINAL_REQUEST_PREFIX = "_original_request_for_state_";
    private static final Logger log = LoggerFactory.getLogger(CookieOriginialExchangeStore.class);

    final long expiresAfterSeconds = 15 * 60;
    String domain;
    final boolean httpOnly = true;
    final String sameSite = null;

    private String originalRequestKeyNameInSession(StateManager state) {
        return ORIGINAL_REQUEST_PREFIX + state.getSecurityToken();
    }

    public List<String> createCookieAttributes(Exchange exc) {

        return Stream.of(
                "Max-Age=" + expiresAfterSeconds,
                "Expires=" + DateTimeFormatter.RFC_1123_DATE_TIME.format(OffsetDateTime.now(UTC).plus(Duration.ofSeconds(expiresAfterSeconds))),
                "Path=/", getSecureAttribute(exc),
                domain != null ? "Domain=" + domain + "; " : null,
                httpOnly ? "HttpOnly" : null,
                sameSite != null ? "SameSite="+sameSite : null
        )
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    private static @Nullable String getSecureAttribute(Exchange exc) {
        if (!(exc.getProxy() instanceof SSLableProxy sp))
            return null;
        return sp.isInboundSSL() ? "Secure" : null;
    }

    public List<String> createInvalidationAttributes() {
        return Stream.of(
                "Expires=Thu, 01 Jan 1970 00:00:00 GMT",
                "Path=/",
                domain != null ? "Domain=" + domain + "; " : null
        )
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    private List<String> expireCookies(List<String> invalidCookies) {
        return invalidCookies
                .stream()
                .map(cookie -> cookie + ";" + String.join(";", createInvalidationAttributes()))
                .collect(Collectors.toList());
    }

    @Override
    public void store(Exchange exchange, Session session, StateManager state, Exchange exchangeToStore) throws IOException {
        try {
            AbstractExchangeSnapshot trimmedAbstractExchangeSnapshot = getTrimmedAbstractExchangeSnapshot(exchangeToStore, 3000);

            var r = trimmedAbstractExchangeSnapshot.getRule();

            String currentSessionCookieValue = originalRequestKeyNameInSession(state) + "=" + escapeForCookie(new ObjectMapper().writeValueAsString(trimmedAbstractExchangeSnapshot));

            if (currentSessionCookieValue.length() > 4093)
                log.warn("Cookie is larger than 4093 bytes, this will not work some browsers.");
            exchange.getResponse().getHeader()
                    .add(SET_COOKIE, currentSessionCookieValue
                                     + ";" + String.join(";", createCookieAttributes(exchange)));

        } catch (JacksonException e) {
            throw new RuntimeException(e);
        }

    }

    private String escapeForCookie(String value) {
        return URLEncoder.encode(value, UTF_8);
    }

    private String unescapeForCookie(String value) {
        return URLDecoder.decode(value, UTF_8);
    }

    protected Stream<String> getCookies(Exchange exc) {
        return exc.getRequest().getHeader().getValues(new HeaderName(COOKIE)).stream().map(s -> s.getValue().split(";")).flatMap(Arrays::stream);
    }

    @Override
    public AbstractExchangeSnapshot reconstruct(Exchange exchange, Session session, StateManager state) {
        try {
            String value = getCookies(exchange)
                    .filter(cookie -> cookie.indexOf("=") > 0)
                    .filter(cookie -> cookie.split("=")[0].trim().equals(originalRequestKeyNameInSession(state)))
                    .map(cookie -> cookie.split("=")[1])
                    .map(cookie -> {
                        int p = cookie.indexOf(';');
                        if (p == -1)
                            return cookie;
                        return cookie.substring(0, p);
                    })
                    .findFirst().get();
            return new ObjectMapper().readValue(unescapeForCookie(value),AbstractExchangeSnapshot.class);
        } catch (JacksonException e) {
            throw new RuntimeException(e);
        }
    }

    private List<StateManager> getStatesToRemove(Exchange exchange) {
        ArrayList<StateManager> l = (ArrayList<StateManager>) exchange.getProperty("statesToRemove");
        if (l != null)
            return l;
        l = new ArrayList<>();
        exchange.setProperty("statesToRemove", l);
        return l;
    }

    @Override
    public void remove(Exchange exchange, Session session, StateManager state) {
        getStatesToRemove(exchange).add(state);
    }

    @Override
    public void postProcess(Exchange exchange) {
        getStatesToRemove(exchange).forEach(state -> {
            String currentSessionCookieValue = originalRequestKeyNameInSession(state) + "=";
            expireCookies(ImmutableList.of(currentSessionCookieValue))
                .forEach(cookie -> exchange.getResponse().getHeader().add(SET_COOKIE, cookie));
        });
    }
}
