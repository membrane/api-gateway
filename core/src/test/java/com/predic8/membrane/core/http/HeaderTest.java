/* Copyright 2009, 2012 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */

package com.predic8.membrane.core.http;

import jakarta.activation.MimeType;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.*;
import org.junit.jupiter.params.provider.*;

import static com.predic8.membrane.core.http.Header.*;
import static com.predic8.membrane.core.http.MimeType.*;
import static java.nio.charset.StandardCharsets.*;
import static org.junit.jupiter.api.Assertions.*;

public class HeaderTest {

    private static final Header header = new Header();

    private static Header h1;

    @BeforeEach
    public void setUp() throws Exception {
        header.setContentType("text/xml; charset=utf-8");
        header.add(HOST, "127.0.0.1:2000");
        header.setAccept("application/soap+xml, application/dime, multipart/related, text/*");
        header.setAuthorization("alice", "secret");

        h1 = new Header();

        h1.add(X_FORWARDED_FOR, "192.3.14.1");
        h1.add(X_FORWARDED_FOR, "10.0.0.1");
        h1.add(X_FORWARDED_FOR, "2001:db8:85a3:8d3:1319:8a2e:370:7348");

        Header h2 = new Header();
        h2.add(X_FORWARDED_FOR, "  192.3.14.1 , 	10.0.0.1,2001:db8:85a3:8d3:1319:8a2e:370:7348	 ");
    }

    @Test
    public void testGetHeader() {
        assertNotNull(header.getFirstValue("ACCEPT"));
        assertNotNull(header.getFirstValue("accept"));
        assertEquals("127.0.0.1:2000", header.getFirstValue("host"));
    }

    @Test
    public void testAuthorization() {
        assertEquals("Basic YWxpY2U6c2VjcmV0",
                header.getFirstValue(AUTHORIZATION));
    }

    @Test
    public void testGetMimeType() throws Exception {
        assertTrue(new MimeType(header.getContentType()).match(TEXT_XML));
    }

    @Test
    @DisplayName("Find containing headers")
    void headerContains() {
        var header = new Header();
        header.setValue("X-Foo", "123");

        assertTrue(header.contains("x-foo"));
        assertFalse(header.contains("x-bar"));
    }

    @Test
    public void testGetCharsetNull() {
        Header header = new Header();
        header.setContentType(TEXT_XML);
        assertEquals(UTF_8.name(), header.getCharset());
    }

    @Test
    public void testStringCharset() {
        Header header = new Header();
        header.setContentType("text/xml ;charset=\"UTF-8\"");
        assertEquals(UTF_8.name(), header.getCharset());
    }

    @Test
    public void testGetCharsetCTNull() {
        assertEquals(UTF_8.name(), new Header().getCharset());
    }

    @Test
    public void testGetCharset() {
        header.setContentType("text/xml; charset=utf-8");
        assertEquals(UTF_8.name(), header.getCharset());
    }

    @ParameterizedTest
    @ValueSource(strings = {"zip", "octet-stream"})
    void isBinaryContentTypeSubtypes(String subtype) {
        assertTrue(isBinary("foo/" + subtype), subtype);
    }

    @ParameterizedTest
    @ValueSource(strings = {"audio", "image", "video"})
    void isBinaryContentTypePrimaryTypes(String primary) {
        assertTrue(isBinary(primary + "/foo"), primary);
    }

    @Test
    void getNormalizedValueFromMultipleHeaders() {
        assertEquals("192.3.14.1,10.0.0.1,2001:db8:85a3:8d3:1319:8a2e:370:7348", h1.getNormalizedValue(X_FORWARDED_FOR));
    }

    @Test
    void getNormalizedValueFromOneHeader() {
        assertEquals("192.3.14.1,10.0.0.1,2001:db8:85a3:8d3:1319:8a2e:370:7348", h1.getNormalizedValue(X_FORWARDED_FOR));
    }

    @Test
    void multipleHeaderWithSameNameNormalized() {
        Header h = new Header();
        h.add("Foo", "1");
        h.add("Foo", "2");
        h.add("Foo", "3");
        assertEquals("1,2,3", h.getNormalizedValue("Foo"));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/90.0.4430.85 Safari/537.36",
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10.15; rv:88.0) Gecko/20100101 Firefox/88.0",
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/90.0.4430.85 Safari/537.36 Edg/90.0.818.49",
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/90.0.4430.85 Safari/537.36 OPR/75.0.3969.149 SNI/hostname.example.com",
            "Mozilla/5.0 (iPhone; CPU iPhone OS 14_3 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Mobile/15E148 Brave/1.23.1 Chrome/89.0.4389.105",
            "Mozilla/5.0 (X11; Linux x86_64; rv:88.0) Gecko/20100101 Firefox/88.0",
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/14.0 Safari/605.1.15 SNI/hostname.example.com"
    })
    void isUserAgentSupportsSNITrue(String userAgent) {
        Header h = new Header();
        h.add(USER_AGENT, userAgent);
        assertTrue(h.isUserAgentSupportsSNI());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "Mozilla/5.0 (iPhone; CPU iPhone OS 14_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/14.0 Mobile/15E148 Safari/604.1",
            "Mozilla/5.0 (Linux; Android 10; SM-A205U) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/89.0.4389.105 Mobile Safari/537.36",
            "Mozilla/5.0 (SMART-TV; Linux; Tizen 5.0) AppleWebKit/537.36 (KHTML, like Gecko) SamsungBrowser/2.2 Chrome/56.0.2924.0 TV Safari/537.36",
    })
    void isUserAgentSupportsSNIFalse(String userAgent) {
        Header h = new Header();
        h.add(USER_AGENT, userAgent);
        assertFalse(h.isUserAgentSupportsSNI());
    }

    @Test
    void isUserAgentSupportsSNIMinimalUserAgent() {
        Header h = new Header();
        h.add(USER_AGENT, "curl/7.64.1");
        assertFalse(h.isUserAgentSupportsSNI());
    }

    @Test
    void isUserAgentSupportsSNIEmptyUserAgent() {
        Header h = new Header();
        h.add(USER_AGENT, "");
        assertFalse(h.isUserAgentSupportsSNI());
    }
}
