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
package com.predic8.membrane.core.acl;

import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.Request;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static com.predic8.membrane.core.interceptor.Outcome.CONTINUE;
import static com.predic8.membrane.core.interceptor.Outcome.ABORT;
import static com.predic8.membrane.core.interceptor.acl.ParseType.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class AccessControlListTest extends ACLTest {
    private static Exchange exc;

    @BeforeAll
    public static void setUp() {
        exc = new Request.Builder().buildExchange();
        exc.setRemoteAddrIp("127.0.0.1");
        exc.setRemoteAddr("localhost");
        exc.setOriginalRequestUri("http://localhost:2000");
    }

    @Test
    public void matchesHostname() throws Exception {
        var aci = createHostnameACI("local.*", false);
        assertEquals(CONTINUE, aci.handleRequest(exc));
    }

    @Test
    public void notMatchesHostname() throws Exception {
        var aci = createHostnameACI("hostlocal", false);
        assertEquals(ABORT, aci.handleRequest(exc));
    }

    @Test
    public void matchesGlobIp() throws Exception {
        var aci = createIpACI("127.0.0.*", GLOB, false, false);
        assertEquals(CONTINUE, aci.handleRequest(exc));
    }

    @Test
    public void notMatchesGlobIp() throws Exception {
        var aci = createIpACI("128.0.1.*", GLOB, false, false);
        assertEquals(ABORT, aci.handleRequest(exc));
    }

    @Test
    public void matchesRegexIp() throws Exception {
        var aci = createIpACI("127.0.0.(2|1)", REGEX, false, false);
        assertEquals(CONTINUE, aci.handleRequest(exc));
    }

    @Test
    public void notMatchesRegexIp() throws Exception {
        var aci = createIpACI("127.0.0.\\s", REGEX, false, false);
        assertEquals(ABORT, aci.handleRequest(exc));
    }

    @Test
    public void matchesCidrIp() throws Exception {
        var aci = createIpACI("127.0.0.0/20", CIDR, false, false);
        assertEquals(CONTINUE, aci.handleRequest(exc));
    }

    @Test
    public void notMatchesCidrIp() throws Exception {
        var aci = createIpACI("127.0.0.0/32", CIDR, false, false);
        assertEquals(ABORT, aci.handleRequest(exc));
    }
}
