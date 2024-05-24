/* Copyright 2023 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */
package com.predic8.membrane.core.openapi.model;


import com.fasterxml.jackson.core.*;
import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;

class MessageTest {

    private Message<JsonBody,?> message;

    @BeforeEach
    void setup() throws JsonProcessingException {

        message = Request.<JsonBody>post().path("/star-star").json().body(new JsonBody("{}"));
    }

    @Test
    void starStarTest() {
        assertTrue(message.isOfMediaType("*/*"));
    }

    @Test
    void typeStarTest() {
        assertTrue(message.isOfMediaType("application/*"));
    }

    @Test
    void starTypeTest() {
        assertFalse(message.isOfMediaType("*/json"));
    }

    @Test
    void simple() {
        assertEquals(new Message.Headers.HeaderKey("foo"),new Message.Headers.HeaderKey("Foo"));
    }
}
