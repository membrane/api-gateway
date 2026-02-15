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

package com.predic8.membrane.core.interceptor.acl.targets;

import com.predic8.membrane.core.interceptor.*;
import com.predic8.membrane.core.interceptor.acl.*;
import com.predic8.membrane.core.openapi.serviceproxy.*;
import com.predic8.membrane.core.router.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.*;
import org.junit.jupiter.params.provider.*;

import java.net.*;

import static com.predic8.membrane.core.http.Request.*;
import static org.junit.jupiter.api.Assertions.*;

class TargetTest {

    @ParameterizedTest(name = "byMatch(\"{0}\") -> Ipv4Target")
    @ValueSource(strings = {
            "192.168.0.1",
            "10.0.0.0/8",
            "0.0.0.0/0",
            "203.0.113.7/32"
    })
    void byMatch_creates_ipv4_target(String raw) {
        Target t = Target.byMatch(raw);
        assertInstanceOf(Ipv4Target.class, t);

        // Sanity: should match itself
        assertTrue(t.peerMatches(IpAddress.parse(raw.trim().split("/")[0])));
    }

    @ParameterizedTest(name = "byMatch(\"{0}\") -> Ipv6Target")
    @ValueSource(strings = {
            "2001:db8::1",
            "2001:db8::/64",
            "::/0",
            "[2001:db8::]/64",
            "[2001:db8::1]"
    })
    void byMatch_creates_ipv6_target(String raw) {
        Target t = Target.byMatch(raw);
        assertInstanceOf(Ipv6Target.class, t);
    }

    @ParameterizedTest(name = "byMatch(\"{0}\") -> HostnameTarget")
    @ValueSource(strings = {
            "^example\\.com$",
            "^([a-z0-9-]+\\.)*example\\.com$",
            ".*"
    })
    void byMatch_creates_hostname_target(String raw) {
        Target t = Target.byMatch(raw);
        assertInstanceOf(HostnameTarget.class, t);

        IpAddress ip = IpAddress.parse("127.0.0.1");
        ip.setHostname("example.com");
        assertTrue(t.peerMatches(ip));
    }

    @Test
    void byMatch_rejects_invalid_ipv4_ipv6_and_invalid_hostname_regex() {
        assertThrows(IllegalArgumentException.class, () -> Target.byMatch("999.1.1.1/33"));
        assertThrows(IllegalArgumentException.class, () -> Target.byMatch("2001:db8::1/129"));
        assertThrows(IllegalArgumentException.class, () -> Target.byMatch("["));
    }

    @Test
    void targetWithExpression() throws URISyntaxException {
        var exc = get("http://localhost:2000/").buildExchange();
        var api = new APIProxy() {{
            setTarget(new com.predic8.membrane.core.proxies.Target() {{
                // { and } are illegal characters in URLs. Make sure they are accepted at that point
                setUrl("http://localhost/${1+2}");
            }});
        }};

        var di = new DispatchingInterceptor();
        exc.setProxy(api);
        di.handleRequest(exc);
        assertEquals(1, exc.getDestinations().size());

        // Expression should not be evaluated at this point.
        assertEquals("http://localhost/${1+2}", exc.getDestinations().getFirst());
    }

}
