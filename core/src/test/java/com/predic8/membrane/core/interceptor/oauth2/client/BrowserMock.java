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
import com.predic8.membrane.core.http.*;
import com.predic8.membrane.core.resolver.ResolverMap;
import com.predic8.membrane.core.transport.http.HttpClient;
import com.predic8.membrane.core.transport.http.client.ConnectionConfiguration;
import com.predic8.membrane.core.transport.http.client.HttpClientConfiguration;
import org.jetbrains.annotations.NotNull;
import org.jose4j.jwt.JwtClaims;
import org.jose4j.jwt.consumer.InvalidJwtException;
import org.jose4j.jwt.consumer.JwtConsumer;
import org.jose4j.jwt.consumer.JwtConsumerBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static java.net.URLEncoder.encode;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.stream.Collectors.*;
import static org.apache.commons.text.StringEscapeUtils.unescapeXml;

public class BrowserMock implements Function<Exchange, Exchange> {

    public static final Pattern INPUT_PATTERN = Pattern.compile("<input type=\"hidden\" name=\"([-a-zA-Z0-9&;_]*)\" value=\"([-a-zA-Z ._~=0-9&/;]*)\"/>");
    public static final Pattern FORM_PATTERN = Pattern.compile("<form method=\"post\" action=\"([a-z:/0-9]*)\"");
    final Logger LOG = LoggerFactory.getLogger(BrowserMock.class);
    final Map<String, Map<String, String>> cookie = new HashMap<>();
    final Function<Exchange, Exchange> cookieHandlingHttpClient = exc -> cookieManager(httpClient(), exc);
    final Function<Exchange, Exchange> cookieHandlingRedirectingHttpClient = outerExc -> handleFormPost(innerExc -> handleRedirect(cookieHandlingHttpClient, innerExc, new ArrayList<>()), outerExc);

    /**
     * Recursively processes an HTTP exchange that contains a self-submitting HTML form.
     *
     * <p>This method applies the provided redirect handler to the exchange and inspects the response.
     * If the response contains a JavaScript-triggered auto-submitting form, it extracts the form's
     * target URL and input fields, constructs a new form submission, and recursively processes the
     * resulting exchange. The recursion terminates when the response does not indicate a self-submitting
     * form or has a non-200 status code.</p>
     *
     * @param redirectHandler the function that handles redirects and cookie management for the HTTP exchange
     * @param exc the original HTTP exchange to process
     * @return the resulting HTTP exchange after handling any self-submitting forms
     * @throws RuntimeException if a malformed URL is encountered during form submission
     */
    private @NotNull Exchange handleFormPost(Function<Exchange, Exchange> redirectHandler, Exchange exc) {
        Exchange result = redirectHandler.apply(exc);

        int statusCode = result.getResponse().getStatusCode();
        String response = result.getResponse().getBodyAsStringDecoded();
        if (statusCode != 200)
            return result;
        if (!response.contains("javascript:document.forms[0].submit()"))
            return result;
        System.out.println(response);
        // this is a self-submitting form
        Matcher m1 = FORM_PATTERN.matcher(response);
        if (!m1.find()) {
            LOG.warn("did not find form action");
            return result;
        }
        String target = m1.group(1);
        System.out.println("target = " + target);
        String firstDestination = result.getDestinations().getFirst();
        if (!target.contains(":")) {
            target = ResolverMap.combine(firstDestination, target);
            System.out.println("target = " + target);
        }

        LOG.debug("posting form to {}", firstDestination);
        try {
            return handleFormPost(
                    redirectHandler,
                    new Request.Builder()
                            .post(target)
                            .contentType(MimeType.APPLICATION_X_WWW_FORM_URLENCODED)
                            .body(encodeMapAsBody(gatherInputFields(response)))
                            .buildExchange()
            );
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }

    private static @NotNull Map<String, String> gatherInputFields(String response) {
        // the regex is just good enough for the tests
        Matcher m2 = INPUT_PATTERN.matcher(response);
        Map<String, String> parameters = new HashMap<>();
        while (m2.find()) {
            parameters.put(unescapeXml(m2.group(1)), unescapeXml(m2.group(2)));
        }
        System.out.println("parameters = " + parameters);
        return parameters;
    }

    private static @NotNull String encodeMapAsBody(Map<String, String> parameters) {
        return parameters.entrySet().stream().map(BrowserMock::encodeMapEntry).collect(joining("&"));
    }

    private static @NotNull String encodeMapEntry(Map.Entry<String, String> e) {
        return encode(e.getKey(), UTF_8) + "=" + encode(e.getValue(), UTF_8);
    }

    /**
     * Applies a simplified cookie management process to an HTTP exchange.
     *
     * <p>
     * This method extracts the domain from the given exchange and retrieves any stored cookies for that domain.
     * It adds these cookies to the exchange's request headers, then applies the provided consumer function to
     * process the exchange (typically representing an HTTP client call). After receiving the response, it iterates
     * over any "Set-Cookie" headers to update or expire cookies accordingly.
     * </p>
     *
     * <p>
     * Note: This implementation is a simplified version that fulfills test requirements and does not represent a
     * fully compliant cookie management solution.
     * </p>
     *
     * @param consumer a function that processes an Exchange and returns the resulting Exchange, typically representing an HTTP call
     * @param exc the HTTP exchange containing request and response data
     * @return the Exchange resulting from applying the consumer function
     */

    private @NotNull Exchange cookieManager(Function<Exchange, Exchange> consumer, final Exchange exc) {
        String domain = getDomain(exc);
        Map<String, String> cookies;
        synchronized (cookie) {
            cookies = cookie.get(domain);
            addCookiesToExchange(exc, cookies);
        }
        var result = consumer.apply(exc);

        List<HeaderField> setCookieInstructions = result.getResponse().getHeader().getValues(new HeaderName("Set-Cookie"));
        checkSameForCookieKeyUsedMultipleTimes(setCookieInstructions);

        synchronized (cookie) {
            for (HeaderField setCookieField : setCookieInstructions) {
                cookies = manipulateCookies(setCookieField, domain, cookies);
            }
        }

        return result;
    }

    private void checkSameForCookieKeyUsedMultipleTimes(List<HeaderField> headers) {
        var counts = headers.stream().map(BrowserMock::getKeyValue).collect(groupingBy(KeyValue::key, counting()));
        var duplicates = counts.entrySet().stream().filter(e -> e.getValue() > 1).map(Map.Entry::getKey).toList();
        if (!duplicates.isEmpty()) {
            throw new RuntimeException("Same Cookie Key(s) "+String.join(", ", duplicates) + " used multiple times in response: " + headers);
        }
    }

      /**
     * Adds cookies to the request header of the provided exchange.
     *
     * <p>If the cookies map is non-null, each cookie is converted into a header field using
     * {@link BrowserMock#createCookieHeaderField} and added to the exchange's request header.</p>
     *
     * @param exc the exchange whose request header is to be updated
     * @param cookies a map of cookie names and values to be appended to the request header
     */
    private static void addCookiesToExchange(Exchange exc, Map<String, String> cookies) {
        if (cookies == null)
            return;
        Header header = exc.getRequest().getHeader();
        header.addAll(cookies.entrySet().stream()
                .map(BrowserMock::createCookieHeaderField)
                .toList());
    }

    /**
     * Creates a cookie header field from a map entry.
     *
     * This method constructs a new HeaderField with the name "Cookie" and a value
     * in the format "key=value", where the key and value are derived from the provided entry.
     *
     * @param entry a map entry containing the cookie name as the key and its value as the value
     * @return a HeaderField representing the cookie
     */
    private static HeaderField createCookieHeaderField(Map.Entry<String, String> entry) {
        return new HeaderField("Cookie", entry.getKey() + "=" + entry.getValue());
    }

    /**
     * Updates the provided cookie map based on the Set-Cookie header field.
     *
     * <p>This method extracts the cookie's key-value pair from the given header field. If the cookie is
     * determined to be expired, it removes the cookie from the map; otherwise, it adds or updates the cookie.
     * If the cookie map is null, a new map is initialized for the specified domain.</p>
     *
     * @param headerField the header field containing the cookie information
     * @param domain the domain associated with the cookie
     * @param cookies a map of cookies for the domain, or null to create a new map if needed
     * @return the updated, non-null map of cookies for the specified domain
     */
    private @NotNull Map<String, String> manipulateCookies(final HeaderField headerField, final String domain, Map<String, String> cookies) {
        LOG.debug("from {} got Set-Cookie: {}", domain, headerField.getValue());

        KeyValue headerKeyValue = getKeyValue(headerField);

        if (cookies == null) {
            cookies = getOrInitializeCookies(domain);
        }

        if (isExpired(headerField)) {
            LOG.debug("removing cookie.");
            cookies.remove(headerKeyValue.key());
            return cookies;
        }
        logJwtClaims(headerField);

        cookies.put(headerKeyValue.key(), headerKeyValue.value());
        return cookies;
    }

    private void logJwtClaims(HeaderField headerField) {
        try {
            String v = headerField.getValue();
            JwtClaims claims = nonVerifyingJwtConsumer().processToClaims(v.substring(0, v.indexOf("=")));
            claims.getClaimsMap().forEach((key, value) -> LOG.debug("{}: {}", key, value));
        } catch (InvalidJwtException e) {
            //ignore
        }
    }

    private static JwtConsumer nonVerifyingJwtConsumer() {
        return new JwtConsumerBuilder()
                .setSkipSignatureVerification()
                .build();
    }

    private static @NotNull KeyValue getKeyValue(HeaderField headerField) {
        String headerValue = headerField.getValue().substring(0, headerField.getValue().indexOf(";"));

        return new KeyValue(
                getHeaderName(headerValue),
                getHeaderValue(headerValue)
        );
    }

    private static @NotNull String getHeaderValue(String headerValue) {
        return headerValue.substring(headerValue.indexOf("=") + 1).trim();
    }

    private static @NotNull String getHeaderName(String headerValue) {
        return headerValue.substring(0, headerValue.indexOf("=")).trim();
    }

    private record KeyValue(String key, String value) {
    }

    /**
     * Retrieves the cookie map for the specified domain, initializing it if absent.
     *
     * <p>This method synchronizes on the global cookie storage to ensure thread safety.
     * If no cookie map exists for the given domain, a new {@link HashMap} is created and stored.</p>
     *
     * @param domain the domain for which to retrieve or initialize the cookie map
     * @return a non-null map of cookie names and values associated with the domain
     */
    private @NotNull Map<String, String> getOrInitializeCookies(String domain) {
        synchronized (cookie) {
            return cookie.computeIfAbsent(domain, k -> new HashMap<>());
        }
    }

    /**
     * Extracts the host domain from the first destination URL in the provided Exchange.
     * <p>
     * This method converts the first destination in the Exchange's destination list into a URI,
     * then into a URL, and returns its host component.
     * </p>
     *
     * @param exc the Exchange object containing destination URLs
     * @return the host domain extracted from the destination URL
     * @throws RuntimeException if the destination URL is malformed or has invalid URI syntax
     */
    private static String getDomain(Exchange exc) {
        try {
            return new URI(exc.getDestinations().getFirst()).toURL().getHost();
        } catch (MalformedURLException | URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }

    private static boolean isExpired(HeaderField headerField) {
        return headerField.getValue().contains("01 Jan 1970");
    }

    private @NotNull Exchange handleRedirect(Function<Exchange, Exchange> consumer, Exchange exc, ArrayList<Object> urls) {
        if (urls.size() == 19)
            throw new RuntimeException("Too many redirects: " + urls);
        exc = consumer.apply(exc);

        String location = extractRedirectLocation(exc.getResponse());
        if (location == null)
            return exc;
        String nextDestination = exc.getDestinations().getFirst();
        if (!location.contains("://"))
            location = ResolverMap.combine(nextDestination, location);
        LOG.debug("redirected to {}", nextDestination);
        try {
            return handleRedirect(consumer, new Request.Builder().get(location).buildExchange(), append(urls, location));
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }

    private static <T> ArrayList<T> append(final ArrayList<T> list, T element) {
        var result = new ArrayList<>(list);
        result.add(element);
        return result;
    }

    private String extractRedirectLocation(Response response) {
        int statusCode = response.getStatusCode();
        String location = response.getHeader().getFirstValue("Location");
        if (statusCode < 300 || statusCode >= 400 || location == null)
            return null;
        return location;
    }

    private Function<Exchange, Exchange> httpClient() {
        return new Function<>() {
            final HttpClient httpClient = new HttpClient(getHttpClientConfiguration());

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

    private static @NotNull HttpClientConfiguration getHttpClientConfiguration() {
        HttpClientConfiguration configuration = new HttpClientConfiguration();
        ConnectionConfiguration connection = new ConnectionConfiguration();
        connection.setTimeout(10000);
        configuration.setConnection(connection);
        return configuration;
    }

    @Override
    public Exchange apply(Exchange exchange) {
        return cookieHandlingRedirectingHttpClient.apply(exchange);
    }

    public Exchange apply(Request.Builder rb) {
        return apply(rb.buildExchange());
    }

    public Exchange applyWithoutRedirect(Exchange exchange) {
        return cookieHandlingHttpClient.apply(exchange);
    }

    public Exchange applyWithoutRedirect(Request.Builder rb) {
        return applyWithoutRedirect(rb.buildExchange());
    }

    public void clearCookies() {
        synchronized (cookie) {
            cookie.clear();
        }
    }

    public int getCookieCount() {
        synchronized (cookie) {
            return cookie.values().stream().map(Map::size).reduce(0, Integer::sum);
        }
    }
}
