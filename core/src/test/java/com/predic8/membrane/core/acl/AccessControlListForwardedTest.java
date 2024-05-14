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

import static com.predic8.membrane.core.http.Header.X_FORWARDED_FOR;
import static com.predic8.membrane.core.interceptor.Outcome.CONTINUE;
import static com.predic8.membrane.core.interceptor.acl.ParseType.GLOB;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class AccessControlListForwardedTest extends ACLTest {
    private static Exchange exc;
    private static Exchange excNoHeader;

    @BeforeAll
    public static void setUp() {
        exc = new Request.Builder().header(X_FORWARDED_FOR, "10.10.10.10").buildExchange();
        excNoHeader = new Request.Builder().buildExchange();
        initExchange(exc);
        initExchange(excNoHeader);
    }

    private static void initExchange(Exchange exc) {
        exc.setRemoteAddrIp("127.0.0.1");
        exc.setRemoteAddr("localhost");
        exc.setOriginalRequestUri("http://localhost:2000");
    }

    @Test
    public void matchesIpWithHeader() throws Exception {
        var aci = createIpACI("10.10.10.10", GLOB, false);
        assertEquals(CONTINUE, aci.handleRequest(exc));
    }

    @Test
    public void matchesIpFallbackToNoHeader() throws Exception {
        var aci = createIpACI("127.0.0.1", GLOB, false);
        assertEquals(CONTINUE, aci.handleRequest(excNoHeader));
    }
}
