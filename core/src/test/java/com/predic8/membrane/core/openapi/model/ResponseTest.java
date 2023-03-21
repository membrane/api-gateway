/*
 *  Copyright 2022 predic8 GmbH, www.predic8.com
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */

package com.predic8.membrane.core.openapi.model;

import jakarta.mail.internet.*;
import org.junit.jupiter.api.*;

import static com.predic8.membrane.core.http.MimeType.*;
import static org.junit.jupiter.api.Assertions.*;

class ResponseTest {

    Response res1;

    @BeforeEach
    void setup() throws ParseException {
        res1 = new Response(200, APPLICATION_JSON);
    }

    @Test
    void getType() {
        assertEquals(APPLICATION_JSON, res1.getMediaType().getBaseType());
    }

    @Test
    void match() {
        assertTrue( res1.isOfMediaType(APPLICATION_JSON));
    }

    @Test
    void matchWildcard() {
        assertTrue( new Response(200).matchesWildcard("2XX"));
        assertTrue( new Response(200).matchesWildcard("2XX"));
        assertTrue( new Response(299).matchesWildcard("2XX"));
        assertTrue( new Response(404).matchesWildcard("4XX"));
        assertFalse( new Response(300).matchesWildcard("2XX"));
    }
}