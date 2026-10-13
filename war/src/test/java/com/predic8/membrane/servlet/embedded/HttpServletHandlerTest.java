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
package com.predic8.membrane.servlet.embedded;

import org.junit.jupiter.api.Test;

import static com.predic8.membrane.servlet.embedded.HttpServletHandler.getRequestTarget;
import static org.junit.jupiter.api.Assertions.assertEquals;

class HttpServletHandlerTest {

    @Test
    void contextPathIsRemoved() {
        assertEquals("/api/x?a=b", getRequestTarget("/membrane/api/x", "a=b", "/membrane", true));
    }

    /**
     * Containers that do not redirect "/membrane" to "/membrane/" pass the bare context root to the servlet.
     * Without the context path nothing would be left, which is not a valid request target.
     */
    @Test
    void bareContextRootBecomesSlash() {
        assertEquals("/", getRequestTarget("/membrane", null, "/membrane", true));
    }

    @Test
    void bareContextRootWithQueryBecomesSlashWithQuery() {
        assertEquals("/?x=1", getRequestTarget("/membrane", "x=1", "/membrane", true));
    }

    @Test
    void rootContextIsLeftAsItIs() {
        assertEquals("/api/x", getRequestTarget("/api/x", null, "", true));
    }

    @Test
    void contextPathIsKeptWhenNotRemoved() {
        assertEquals("/membrane?x=1", getRequestTarget("/membrane", "x=1", "/membrane", false));
    }
}
