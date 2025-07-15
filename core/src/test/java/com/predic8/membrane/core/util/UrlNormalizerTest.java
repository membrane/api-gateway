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
