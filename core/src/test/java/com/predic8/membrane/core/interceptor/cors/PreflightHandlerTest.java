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

package com.predic8.membrane.core.interceptor.cors;

import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;

class PreflightHandlerTest {

    private PreflightHandler ph;

    @BeforeEach
    void setup2() {
        CorsInterceptor ci = new CorsInterceptor();
        ph = new PreflightHandler(ci);
    }

    @Test
    void headersAllowed() {
        assertTrue(ph.headersAllowed(   ""));
        assertTrue(ph.headersAllowed(   null));

        // fetch safe headers
        assertTrue(ph.headersAllowed(   "accept, accept-language, content-language, content-type, range"));

        assertFalse(ph.headersAllowed(   "Foo"));

        assertTrue(ph.headersAllowed("Accept")); // case insensitive
        assertTrue(ph.headersAllowed("  accept  ")); // whitespace handling
        assertFalse(ph.headersAllowed("x-custom-header")); // custom headers
        assertFalse(ph.headersAllowed("authorization")); // sensitive header
        assertTrue(ph.headersAllowed("Accept,content-Type")); // multiple safe headers
        assertFalse(ph.headersAllowed("accept,custom-header")); // mixed safe/unsafe
    }
}