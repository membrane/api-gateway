/* Copyright 2026 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */

package com.predic8.membrane.core.transport.http.client;

import com.predic8.membrane.core.config.security.SSLParser;
import com.predic8.membrane.core.transport.http.HostColonPort;
import com.predic8.membrane.core.util.ConfigurationException;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Predicate;
import java.util.regex.Pattern;

import static com.predic8.membrane.core.util.NetworkUtil.addBracketsIfIPv6;
import static com.predic8.membrane.core.util.NetworkUtil.removeBracketsIfPresent;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Locale.ROOT;

/**
 * The outbound proxy defined outside the configuration file: by the JVM system properties
 * <code>http(s).proxyHost</code>, <code>http(s).proxyPort</code> and <code>http.nonProxyHosts</code>, or else by the
 * environment variables <code>http_proxy</code>, <code>https_proxy</code> and <code>no_proxy</code>. Only one source
 * is used, so a bypass list never applies to the proxy of the other source.
 */
public final class SystemProxy {

    private static final Logger log = LoggerFactory.getLogger(SystemProxy.class);

    // What the JDK uses when http.nonProxyHosts is not set
    static final String DEFAULT_NON_PROXY_HOSTS = "localhost|127.*|[::1]|0.0.0.0|[::0]";

    private static final List<String> PROPERTY_NAMES = List.of("http.proxyHost", "https.proxyHost");
    private static final List<String> VARIABLE_NAMES = List.of("http_proxy", "HTTP_PROXY", "https_proxy", "HTTPS_PROXY");

    private static final AtomicBoolean usageLogged = new AtomicBoolean();

    private final String source;
    private final @Nullable ProxyConfiguration httpProxy;
    private final @Nullable ProxyConfiguration httpsProxy;
    private final Predicate<String> bypass;

    private SystemProxy(String source, @Nullable ProxyConfiguration httpProxy, @Nullable ProxyConfiguration httpsProxy, Predicate<String> bypass) {
        this.source = source;
        this.httpProxy = httpProxy;
        this.httpsProxy = httpsProxy;
        this.bypass = bypass;
    }

    /**
     * @return the proxy defined by the JVM system properties or the environment, or null if neither defines one
     * @throws ConfigurationException if a proxy setting cannot be parsed
     */
    public static @Nullable SystemProxy read() {
        final var proxy = read(System.getenv(), System.getProperties());
        if (proxy != null && usageLogged.compareAndSet(false, true))
            log.info("Using the outbound proxy from {}: {}", proxy.source, proxy);
        return proxy;
    }

    static @Nullable SystemProxy read(Map<String, String> env, Properties properties) {
        final var fromProperties = fromProperties(properties);
        return fromProperties != null ? fromProperties : fromEnvironment(env);
    }

    /**
     * Tells users that a proxy is set in the environment but not used, as Membrane ignored those settings
     * before <code>useSystemProxy</code> existed.
     */
    public static void logIfIgnored() {
        if (isDefined(System.getenv(), System.getProperties()))
            log.info("An outbound proxy is set in the JVM system properties or environment variables, but it is not used. "
                    + "Set useSystemProxy: true on httpClientConfig to route outgoing requests through it.");
    }

    static boolean isDefined(Map<String, String> env, Properties properties) {
        return PROPERTY_NAMES.stream().anyMatch(name -> !isBlank(properties.getProperty(name)))
                || VARIABLE_NAMES.stream().anyMatch(name -> !isBlank(env.get(name)));
    }

    /**
     * @return the proxy for the target, or null if the target is reached directly
     */
    public @Nullable ProxyConfiguration select(HostColonPort target) {
        if (bypass.test(target.host()))
            return null;
        return target.useSSL() ? httpsProxy : httpProxy;
    }

    public List<ProxyConfiguration> getProxies() {
        final var proxies = new ArrayList<ProxyConfiguration>();
        if (httpProxy != null)
            proxies.add(httpProxy);
        if (httpsProxy != null)
            proxies.add(httpsProxy);
        return List.copyOf(proxies);
    }

    private static @Nullable SystemProxy fromProperties(Properties properties) {
        final var httpProxy = proxyFromProperties(properties, "http", 80);
        final var httpsProxy = proxyFromProperties(properties, "https", 443);
        if (httpProxy == null && httpsProxy == null)
            return null;
        return new SystemProxy("JVM system properties", httpProxy, httpsProxy,
                nonProxyHosts(properties.getProperty("http.nonProxyHosts", DEFAULT_NON_PROXY_HOSTS)));
    }

    private static @Nullable ProxyConfiguration proxyFromProperties(Properties properties, String scheme, int defaultPort) {
        final var host = properties.getProperty(scheme + ".proxyHost");
        if (isBlank(host))
            return null;
        final var proxy = new ProxyConfiguration();
        proxy.setHost(removeBracketsIfPresent(host.trim()));
        proxy.setPort(parsePort(properties.getProperty(scheme + ".proxyPort"), scheme + ".proxyPort", defaultPort));
        return proxy;
    }

    private static int parsePort(@Nullable String port, String name, int defaultPort) {
        if (isBlank(port))
            return defaultPort;
        try {
            return Integer.parseInt(port.trim());
        } catch (NumberFormatException e) {
            throw new ConfigurationException("The system property %s=%s is not a port number.".formatted(name, port));
        }
    }

    /**
     * The http.nonProxyHosts syntax: patterns separated by <code>|</code>, where <code>*</code> stands for any
     * characters.
     */
    static Predicate<String> nonProxyHosts(String nonProxyHosts) {
        final var patterns = new ArrayList<Pattern>();
        for (var entry : nonProxyHosts.split("\\|")) {
            entry = entry.trim().toLowerCase(ROOT);
            if (entry.isEmpty())
                continue;
            final var regex = new StringBuilder();
            for (var part : entry.split("\\*", -1)) {
                if (!regex.isEmpty())
                    regex.append(".*");
                regex.append(Pattern.quote(part));
            }
            patterns.add(Pattern.compile(regex.toString()));
        }
        return host -> {
            final var bare = removeBracketsIfPresent(host.toLowerCase(ROOT));
            final var bracketed = addBracketsIfIPv6(bare);
            return patterns.stream().anyMatch(p -> p.matcher(bare).matches() || p.matcher(bracketed).matches());
        };
    }

    private static @Nullable SystemProxy fromEnvironment(Map<String, String> env) {
        final var httpProxy = proxyFromUrl(env, "http_proxy");
        final var httpsProxy = proxyFromUrl(env, "https_proxy");
        if (httpProxy == null && httpsProxy == null)
            return null;
        final var noProxy = getenv(env, "no_proxy");
        final Predicate<String> bypass = noProxy != null ? NoProxyList.parse(noProxy)::matches : host -> false;
        return new SystemProxy("environment variables", httpProxy, httpsProxy, bypass);
    }

    /**
     * Error messages name the variable but never its value, which may contain the proxy password.
     */
    private static @Nullable ProxyConfiguration proxyFromUrl(Map<String, String> env, String name) {
        final var value = getenv(env, name);
        if (value == null)
            return null;

        final URI uri;
        try {
            uri = new URI(value.contains("://") ? value : "http://" + value);
        } catch (URISyntaxException e) {
            throw new ConfigurationException("The environment variable %s is not a valid proxy URL.".formatted(name));
        }

        final var scheme = uri.getScheme().toLowerCase(ROOT);
        if (!scheme.equals("http") && !scheme.equals("https"))
            throw new ConfigurationException("The environment variable %s uses the scheme %s, but only http:// and https:// proxies are supported."
                    .formatted(name, scheme));
        if (uri.getHost() == null)
            throw new ConfigurationException("The environment variable %s does not contain a proxy host.".formatted(name));

        final var proxy = new ProxyConfiguration();
        proxy.setHost(removeBracketsIfPresent(uri.getHost()));
        proxy.setPort(uri.getPort() != -1 ? uri.getPort() : scheme.equals("https") ? 443 : 80);
        if (scheme.equals("https"))
            proxy.setSslParser(new SSLParser());
        setCredentials(proxy, uri.getRawUserInfo());
        return proxy;
    }

    private static void setCredentials(ProxyConfiguration proxy, @Nullable String rawUserInfo) {
        if (rawUserInfo == null)
            return;
        final var colon = rawUserInfo.indexOf(':');
        proxy.setAuthentication(true);
        proxy.setUsername(decode(colon == -1 ? rawUserInfo : rawUserInfo.substring(0, colon)));
        proxy.setPassword(colon == -1 ? "" : decode(rawUserInfo.substring(colon + 1)));
    }

    // URLDecoder decodes form data, where '+' is a space. In a URL's user info it is a literal '+'.
    private static String decode(String s) {
        return URLDecoder.decode(s.replace("+", "%2B"), UTF_8);
    }

    /**
     * The lowercase name wins, as with curl.
     */
    private static @Nullable String getenv(Map<String, String> env, String lowercaseName) {
        final var lower = env.get(lowercaseName);
        if (!isBlank(lower))
            return lower.trim();
        final var upper = env.get(lowercaseName.toUpperCase(ROOT));
        return isBlank(upper) ? null : upper.trim();
    }

    private static boolean isBlank(@Nullable String s) {
        return s == null || s.isBlank();
    }

    @Override
    public String toString() {
        return "http=%s, https=%s".formatted(describe(httpProxy), describe(httpsProxy));
    }

    private static String describe(@Nullable ProxyConfiguration proxy) {
        return proxy == null ? "direct" : proxy.getHost() + ":" + proxy.getPort();
    }
}
