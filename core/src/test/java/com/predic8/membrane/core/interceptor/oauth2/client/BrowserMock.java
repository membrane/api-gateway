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
package com.predic8.membrane.core.interceptor.oauth2.client;

import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.HeaderField;
import com.predic8.membrane.core.http.HeaderName;
import com.predic8.membrane.core.http.MimeType;
import com.predic8.membrane.core.http.Request;
import com.predic8.membrane.core.resolver.ResolverMap;
import com.predic8.membrane.core.transport.http.HttpClient;
import com.predic8.membrane.core.transport.http.client.ConnectionConfiguration;
import com.predic8.membrane.core.transport.http.client.HttpClientConfiguration;
import org.jose4j.jwt.JwtClaims;
import org.jose4j.jwt.consumer.InvalidJwtException;
import org.jose4j.jwt.consumer.JwtConsumer;
import org.jose4j.jwt.consumer.JwtConsumerBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static java.util.stream.Collectors.joining;

public class BrowserMock implements Function<Exchange, Exchange> {

    Logger LOG = LoggerFactory.getLogger(BrowserMock.class);
    Map<String, Map<String, String>> cookie = new HashMap<>();
    Function<Exchange, Exchange> cookieHandlingHttpClient = cookieManager(httpClient());
    Function<Exchange, Exchange> cookieHandlingRedirectingHttpClient = handleFormPost(handleRedirect(cookieHandlingHttpClient));

    private Function<Exchange, Exchange> handleFormPost(Function<Exchange, Exchange> consumer) {
        return exc -> {
            while (true) {
                exc = consumer.apply(exc);

                int statusCode = exc.getResponse().getStatusCode();
                String response = exc.getResponse().getBodyAsStringDecoded();
                if (statusCode != 200)
                    break;
                if (!response.contains("javascript:document.forms[0].submit()"))
                    break;
                System.out.println(response);
                // this is a self-submitting form
                Matcher m1 = Pattern.compile("<form method=\"post\" action=\"([a-z:/0-9]*)\"").matcher(response);
                if (!m1.find()) {
                    LOG.warn("did not find form action");
                    break;
                }
                String target = m1.group(1);
                System.out.println("target = " + target);
                Matcher m2 = Pattern.compile("<input type=\"hidden\" name=\"([a-z0-9]*)\" value=\"([a-z_=0-9&/]*)\"/>").matcher(response);
                Map<String, String> parameters = new HashMap<>();
                while (m2.find()) {
                    parameters.put(m2.group(1), m2.group(2));
                }
                System.out.println("parameters = " + parameters);

                try {
                    exc = new Request.Builder().post(target).contentType(MimeType.APPLICATION_X_WWW_FORM_URLENCODED)
                            .body(parameters.entrySet().stream().map(e -> e.getKey() + "=" + e.getValue()).collect(joining("&")))
                            .buildExchange();
                } catch (URISyntaxException e) {
                    throw new RuntimeException(e);
                }

                LOG.debug("posting form to " + exc.getDestinations().get(0));
            }
            return exc;
        };
    }

    // this implementation does NOT implement a correct cookie manager, but fulfills this test's requirements
    private Function<Exchange, Exchange> cookieManager(Function<Exchange, Exchange> consumer) {
        return exc -> {
            String domain = null;
            try {
                domain = new URL(exc.getDestinations().get(0)).getHost();
            } catch (MalformedURLException e) {
                throw new RuntimeException(e);
            }
            Map<String, String> cookies;
            synchronized (cookie) {
                cookies = cookie.get(domain);
            }
            if (cookies != null)
                synchronized (cookies) {
                    Exchange finalExc = exc;
                    cookies.forEach((k, v) -> finalExc.getRequest().getHeader().add("Cookie", k + "=" + v));
                }
            exc = consumer.apply(exc);

            for (HeaderField headerField : exc.getResponse().getHeader().getValues(new HeaderName("Set-Cookie"))) {
                LOG.debug("from " + domain + " got Set-Cookie: " + headerField.getValue());

                String value = headerField.getValue().substring(0, headerField.getValue().indexOf(";"));
                boolean expired = headerField.getValue().contains("1970");

                String key = value.substring(0, value.indexOf("=")).trim();
                value = value.substring(value.indexOf("=") + 1).trim();

                if (cookies == null) {
                    cookies = new HashMap<>();
                    synchronized (cookie) {
                        // recheck whether there are still no cookies yet
                        Map<String, String> cookies2 = cookie.get(domain);
                        if (cookies2 != null)
                            cookies = cookies2;
                        else
                            cookie.put(domain, cookies);
                    }
                }

                if (expired) {
                    LOG.debug("removing cookie.");
                    synchronized (cookies) {
                        cookies.remove(key);
                    }
                } else {
                    try {
                        JwtConsumer jwtc = new JwtConsumerBuilder()
                                .setSkipSignatureVerification()
                                .build();

                        String v = headerField.getValue();
                        JwtClaims claims = jwtc.processToClaims(v.substring(0, v.indexOf("=")));
                        for (Map.Entry<String, Object> entry : claims.getClaimsMap().entrySet()) {
                            LOG.debug(entry.getKey() + ": " + entry.getValue());
                        }
                    } catch (InvalidJwtException e) {
                        //ignore
                    }

                    synchronized (cookies) {
                        cookies.put(key, value);
                    }
                }
            }

            return exc;
        };
    }

    private Function<Exchange, Exchange> handleRedirect(Function<Exchange, Exchange> consumer) {
        return exc -> {
            ArrayList<Object> urls = new ArrayList<>();
            while (true) {
                if (urls.size() == 19)
                    throw new RuntimeException("Too many redirects: " + urls);
                exc = consumer.apply(exc);

                int statusCode = exc.getResponse().getStatusCode();
                String location = exc.getResponse().getHeader().getFirstValue("Location");
                if (statusCode < 300 || statusCode >= 400 || location == null)
                    break;
                if (!location.contains("://"))
                    location = ResolverMap.combine(exc.getDestinations().get(0), location);
                urls.add(location);
                try {
                    exc = new Request.Builder().get(location).buildExchange();
                } catch (URISyntaxException e) {
                    throw new RuntimeException(e);
                }

                LOG.debug("redirected to " + exc.getDestinations().get(0));
            }
            return exc;
        };
    }

    private Function<Exchange, Exchange> httpClient() {
        HttpClientConfiguration configuration = new HttpClientConfiguration();
        ConnectionConfiguration connection = new ConnectionConfiguration();
        connection.setTimeout(10000);
        configuration.setConnection(connection);
        return new Function<>() {
            HttpClient httpClient = new HttpClient(configuration);

            @Override
            public Exchange apply(Exchange exchange) {
                try {
                    return httpClient.call(exchange);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
        };
    }

    @Override
    public Exchange apply(Exchange exchange) {
        return cookieHandlingRedirectingHttpClient.apply(exchange);
    }

    public Exchange applyWithoutRedirect(Exchange exchange) {
        return cookieHandlingHttpClient.apply(exchange);
    }

    public void clearCookies() {
        cookie.clear();
    }

    public int getCookieCount() {
        return cookie.values().stream().map(Map::size).reduce(0, Integer::sum);
    }
}
