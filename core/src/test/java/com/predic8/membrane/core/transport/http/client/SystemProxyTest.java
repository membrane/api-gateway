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

import com.predic8.membrane.core.transport.http.HostColonPort;
import com.predic8.membrane.core.util.ConfigurationException;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Map;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.*;

class SystemProxyTest {

    static final HostColonPort HTTP_TARGET = new HostColonPort(false, "api.example.com", 80);
    static final HostColonPort HTTPS_TARGET = new HostColonPort(true, "api.example.com", 443);

    @Test
    void nothingDefined() {
        assertNull(SystemProxy.read(Map.of(), new Properties()));
        assertFalse(SystemProxy.isDefined(Map.of(), new Properties()));
    }

    @Nested
    class EnvironmentVariables {

        @Test
        void schemeOfTargetSelectsProxy() {
            final var proxy = fromEnv(Map.of(
                    "HTTP_PROXY", "http://http-proxy.example.com:3128",
                    "HTTPS_PROXY", "http://https-proxy.example.com:3129"));

            assertProxy("http-proxy.example.com", 3128, proxy.select(HTTP_TARGET));
            assertProxy("https-proxy.example.com", 3129, proxy.select(HTTPS_TARGET));
        }

        @Test
        void targetWithoutProxyForItsSchemeIsCalledDirectly() {
            final var proxy = fromEnv(Map.of("HTTPS_PROXY", "http://proxy.example.com:3128"));

            assertNull(proxy.select(HTTP_TARGET));
            assertNotNull(proxy.select(HTTPS_TARGET));
        }

        @Test
        void lowercaseWins() {
            final var proxy = fromEnv(Map.of(
                    "http_proxy", "http://lower.example.com:3128",
                    "HTTP_PROXY", "http://upper.example.com:3128"));

            assertProxy("lower.example.com", 3128, proxy.select(HTTP_TARGET));
        }

        @Test
        void schemeAndPortDefaults() {
            final var proxy = fromEnv(Map.of(
                    "http_proxy", "proxy.example.com",
                    "https_proxy", "https://tls-proxy.example.com"));

            final var http = proxy.select(HTTP_TARGET);
            assertProxy("proxy.example.com", 80, http);
            assertNull(http.getSslParser());

            final var https = proxy.select(HTTPS_TARGET);
            assertProxy("tls-proxy.example.com", 443, https);
            assertNotNull(https.getSslParser());
        }

        @Test
        void credentialsInUrl() {
            final var proxy = fromEnv(Map.of("http_proxy", "http://alice:s%40cr+t%3A1@proxy.example.com:3128")).select(HTTP_TARGET);

            assertTrue(proxy.isAuthentication());
            assertEquals("alice", proxy.getUsername());
            assertEquals("s@cr+t:1", proxy.getPassword());
        }

        @Test
        void noProxy() {
            final var proxy = fromEnv(Map.of(
                    "http_proxy", "http://proxy.example.com:3128",
                    "NO_PROXY", "localhost,.internal.example.com"));

            assertNull(proxy.select(new HostColonPort(false, "orders.internal.example.com", 80)));
            assertNull(proxy.select(new HostColonPort(false, "localhost", 8080)));
            assertNotNull(proxy.select(HTTP_TARGET));
        }

        @ParameterizedTest
        @ValueSource(strings = {"socks5://proxy.example.com:1080", "http://", "http://proxy example.com"})
        void invalidUrlIsRejectedWithoutRevealingIt(String url) {
            final var e = assertThrows(ConfigurationException.class, () -> fromEnv(Map.of("http_proxy", url)));
            assertTrue(e.getMessage().contains("http_proxy"));
            assertFalse(e.getMessage().contains("proxy.example.com"));
        }

        @Test
        void passwordIsNotInToString() {
            final var proxy = fromEnv(Map.of("http_proxy", "http://alice:secret@proxy.example.com:3128"));
            assertFalse(proxy.toString().contains("secret"));
        }

        private static SystemProxy fromEnv(Map<String, String> env) {
            final var proxy = SystemProxy.read(env, new Properties());
            assertNotNull(proxy);
            return proxy;
        }
    }

    @Nested
    class SystemProperties {

        @Test
        void schemeOfTargetSelectsProxy() {
            final var proxy = fromProperties(
                    "http.proxyHost", "http-proxy.example.com", "http.proxyPort", "3128",
                    "https.proxyHost", "https-proxy.example.com");

            assertProxy("http-proxy.example.com", 3128, proxy.select(HTTP_TARGET));
            assertProxy("https-proxy.example.com", 443, proxy.select(HTTPS_TARGET));
        }

        @Test
        void winOverEnvironmentVariablesIncludingNoProxy() {
            final var properties = properties("https.proxyHost", "props.example.com");
            final var proxy = SystemProxy.read(Map.of(
                    "https_proxy", "http://env.example.com:3128",
                    "no_proxy", "*"), properties);

            assertNotNull(proxy);
            assertProxy("props.example.com", 443, proxy.select(HTTPS_TARGET));
            assertNull(proxy.select(HTTP_TARGET));
        }

        @Test
        void defaultNonProxyHostsMatchTheJdk() {
            final var proxy = fromProperties("http.proxyHost", "proxy.example.com");

            assertNull(proxy.select(new HostColonPort(false, "localhost", 8080)));
            assertNull(proxy.select(new HostColonPort(false, "127.0.0.2", 8080)));
            assertNull(proxy.select(new HostColonPort(false, "::1", 8080)));
            assertNotNull(proxy.select(HTTP_TARGET));
        }

        @Test
        void nonProxyHosts() {
            final var proxy = fromProperties(
                    "http.proxyHost", "proxy.example.com",
                    "http.nonProxyHosts", "*.internal.example.com|192.168.*");

            assertNull(proxy.select(new HostColonPort(false, "orders.internal.example.com", 80)));
            assertNull(proxy.select(new HostColonPort(false, "192.168.1.1", 80)));
            assertNotNull(proxy.select(new HostColonPort(false, "localhost", 80)), "the JDK default no longer applies");
            assertNotNull(proxy.select(HTTP_TARGET));
        }

        @Test
        void invalidPortIsRejected() {
            assertThrows(ConfigurationException.class,
                    () -> fromProperties("http.proxyHost", "proxy.example.com", "http.proxyPort", "abc"));
        }

        private static SystemProxy fromProperties(String... keysAndValues) {
            final var proxy = SystemProxy.read(Map.of(), properties(keysAndValues));
            assertNotNull(proxy);
            return proxy;
        }

        private static Properties properties(String... keysAndValues) {
            final var properties = new Properties();
            for (var i = 0; i < keysAndValues.length; i += 2)
                properties.setProperty(keysAndValues[i], keysAndValues[i + 1]);
            return properties;
        }
    }

    private static void assertProxy(String host, int port, ProxyConfiguration proxy) {
        assertNotNull(proxy);
        assertEquals(host, proxy.getHost());
        assertEquals(port, proxy.getPort());
    }
}
