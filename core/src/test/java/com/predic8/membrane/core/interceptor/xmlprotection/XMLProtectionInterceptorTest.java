/* Copyright 2012 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */

package com.predic8.membrane.core.interceptor.xmlprotection;

import com.predic8.membrane.core.*;
import com.predic8.membrane.core.exchange.*;
import com.predic8.membrane.core.http.*;
import com.predic8.membrane.core.interceptor.*;
import org.junit.jupiter.api.*;

import static com.predic8.membrane.core.http.MimeType.*;
import static com.predic8.membrane.core.interceptor.Outcome.*;
import static org.junit.jupiter.api.Assertions.*;

public class XMLProtectionInterceptorTest {
    private static Exchange exc;
    private static XMLProtectionInterceptor interceptor;

    @BeforeAll
    public static void setUp() throws Exception {
        exc = new Exchange(null);
        exc.setRequest(Request.get("/axis2/services/BLZService").build());
        exc.setOriginalHostHeader("thomas-bayer.com:80");

        interceptor = new XMLProtectionInterceptor();
        interceptor.init(new Router());
    }

    private void runOn(String resource, boolean expectSuccess) throws Exception {
        exc.getRequest().getHeader().setContentType(APPLICATION_XML);
        try (var is = this.getClass().getResourceAsStream(resource)) {
            assertNotNull(is, "Test resource not found: " + resource);
            exc.getRequest().setBodyContent(is.readAllBytes());
        }
        Outcome outcome = interceptor.handleRequest(exc);
        assertEquals(expectSuccess ? CONTINUE : ABORT, outcome);
    }

    @Test
    void invariant() throws Exception {
        runOn("/customer.xml", true);
    }

    @Test
    void notWellformed() throws Exception {
        runOn("/xml/not-wellformed.xml", false);
    }

    @Test
    void removeDTD() throws Exception {
        exc.setRequest(Request.post("/").body("""
                <?xml  version="1.0" encoding="ISO-8859-1"?>
                <!DOCTYPE foo [
                     <!ELEMENT foo ANY >
                   ]>
                <foo/>
                """).contentType(APPLICATION_XML).build());

        // Should pass
        assertEquals(CONTINUE, interceptor.handleRequest(exc));

        // Should still contain the XML
        assertTrue(exc.getRequest().getBodyAsStringDecoded().contains("<foo"));

        // DTD should be removed
        assertFalse(exc.getRequest().getBodyAsStringDecoded().contains("DOCTYPE"));
    }
}
