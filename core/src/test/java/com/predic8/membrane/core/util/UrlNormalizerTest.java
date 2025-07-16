/* Copyright 2025 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */

package com.predic8.membrane.core.util;

import org.junit.jupiter.api.Test;

import java.net.URISyntaxException;

import static com.predic8.membrane.core.util.UrlNormalizer.normalizeBaseUrl;
import static org.junit.jupiter.api.Assertions.*;

class UrlNormalizerTest {

    @Test
    void defaultPortsAreOmitted() throws URISyntaxException {
        assertEquals("http://example.com", normalizeBaseUrl("http://example.com"));
        assertEquals("http://example.com", normalizeBaseUrl("http://example.com:80"));
        assertEquals("https://example.com", normalizeBaseUrl("https://example.com:443"));
    }

    @Test
    void nonDefaultPortsAreIncluded() throws URISyntaxException {
        assertEquals("http://example.com:8080", normalizeBaseUrl("http://example.com:8080"));
        assertEquals("https://example.com:8443", normalizeBaseUrl("https://example.com:8443"));
    }

    @Test
    void caseInsensitivityInHostAndScheme() throws URISyntaxException {
        assertEquals("http://example.com", normalizeBaseUrl("HTTP://EXAMPLE.COM"));
        assertEquals("https://example.com", normalizeBaseUrl("https://EXAMPLE.COM:443"));
    }

    @Test
    void iPv6Host() throws URISyntaxException {
        assertEquals("http://[2001:db8::1]", normalizeBaseUrl("http://[2001:db8::1]"));
        assertEquals("http://[2001:db8::1]:8080", normalizeBaseUrl("http://[2001:db8::1]:8080"));
    }

    @Test
    void throwsOnMalformedUrl() {
        assertThrows(URISyntaxException.class, () -> normalizeBaseUrl("not a url"));
    }

    @Test
    void trailingSlashesAreIgnored() throws URISyntaxException {
        // default ports
        assertEquals("http://example.com",  UrlNormalizer.normalizeBaseUrl("http://example.com/"));
        assertEquals("https://example.com", UrlNormalizer.normalizeBaseUrl("https://example.com/"));

        // non-default port
        assertEquals("http://example.com:8080",
                UrlNormalizer.normalizeBaseUrl("http://example.com:8080/"));

        // IPv6 host
        assertEquals("http://[2001:db8::1]",
                UrlNormalizer.normalizeBaseUrl("http://[2001:db8::1]/"));
    }
}
