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
package com.predic8.membrane.core.http;

import org.junit.jupiter.api.Test;

import java.io.EOFException;

import static org.junit.jupiter.api.Assertions.*;

class DecodingExceptionTest {

    /**
     * The Content-Encoding comes from the sender and the message is logged at info.
     */
    @Test
    void messageEscapesControlCharactersInContentEncoding() {
        final var e = new DecodingException("gzip\u001b[31m\r\nforged", new EOFException());

        assertFalse(e.getMessage().contains("\u001b"), e.getMessage());
        assertFalse(e.getMessage().contains("\n"), e.getMessage());
        // Kept as sent where it is not logged
        assertEquals("gzip\u001b[31m\r\nforged", e.getContentEncoding());
    }
}
