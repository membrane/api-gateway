/* Copyright 2011 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */
package com.predic8.membrane.core.transport.http;

import org.junit.jupiter.api.*;

import java.net.*;

import static com.predic8.membrane.core.transport.http.HostColonPort.fromURI;
import static org.junit.jupiter.api.Assertions.*;

@SuppressWarnings("HttpUrlsUsage")
public class HostColonPortTest {

    static final HostColonPort HCP_LOCALHOST = new HostColonPort(false, "localhost", 80);
    static final HostColonPort HCP_LOCALHOST_8080 = new HostColonPort(false, "localhost", 8080);
    static final HostColonPort HCP_HTTPS_LOCALHOST = new HostColonPort(true, "localhost", 443);
    static final HostColonPort HCP_HTTPS_LOCALHOST_8443 = new HostColonPort(true, "localhost", 8443);

	static URI uriLocalhost, uriLocalhost8080, uriHttpsLocalhost, uriHttpsLocalhost8443;

    @BeforeAll
    static void setUp() throws URISyntaxException {
        uriLocalhost = new java.net.URI("http", null, "localhost", 80, null, null, null);
        uriLocalhost8080 = new java.net.URI("http", null, "localhost", 8080, null, null, null);
        uriHttpsLocalhost = new java.net.URI("https", null, "localhost", 443, null, null, null);
        uriHttpsLocalhost8443 = new java.net.URI("https", null, "localhost", 8443, null, null, null);
    }

    @Test
    void testDefaultPort() {
        HostColonPort hcp = new HostColonPort(false, "predic8.com");
        assertEquals("predic8.com", hcp.host());
        assertEquals(80, hcp.port());
    }

    @Test
    void testGetHost() {
        assertEquals("predic8.com", new HostColonPort(false, "predic8.com:80").host());
    }

    @Test
    void testGetPort() {
        assertEquals(80, new HostColonPort(false, "predic8.com:80").port());
    }

    @Test
    void noNumber() {
        Assertions.assertThrowsExactly(NumberFormatException.class, () -> new HostColonPort(false, "foo:no-number"));
    }

    @Test
    void getProtocol() {
        assertEquals("http", new HostColonPort("foo", 80).getProtocol());
        assertEquals("https", new HostColonPort("foo", 443).getProtocol());
    }

    @Test
    void getUrl() {
        assertEquals("http://foo:80", new HostColonPort("foo", 80).getUrl());
        assertEquals("https://foo:443", new HostColonPort("foo", 443).getUrl());
    }

    @Test
    void fromURITests() throws Exception {
        assertEquals(HCP_LOCALHOST, fromURI("http://localhost"));
        assertEquals(HCP_LOCALHOST_8080, fromURI("http://localhost:8080"));
        assertEquals(HCP_LOCALHOST, fromURI("http://localhost/foo"));
        assertEquals(HCP_HTTPS_LOCALHOST, fromURI("https://localhost"));
        assertEquals(HCP_HTTPS_LOCALHOST_8443, fromURI("https://localhost:8443"));
    }

    @Test
    void toURITests() throws URISyntaxException {
        assertEquals(uriLocalhost, HCP_LOCALHOST.toURI());
        assertEquals(uriLocalhost8080, HCP_LOCALHOST_8080.toURI());
        assertEquals(uriHttpsLocalhost, HCP_HTTPS_LOCALHOST.toURI());
        assertEquals(uriHttpsLocalhost8443, HCP_HTTPS_LOCALHOST_8443.toURI());
    }
}
