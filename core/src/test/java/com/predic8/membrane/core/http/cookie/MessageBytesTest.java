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

package com.predic8.membrane.core.http.cookie;

import org.jetbrains.annotations.*;
import org.junit.jupiter.api.*;

import java.nio.charset.*;

import static org.junit.jupiter.api.Assertions.*;

public class MessageBytesTest {

    @Test
    @Disabled // See implemtation of method
    void isNull() {
        assertTrue(MessageBytes.newInstance().isNull());
    }

    @Test
    void equalsTestWithString() {
        String message = "äöüÄÖÜß";

        //noinspection AssertBetweenInconvertibleTypes
        assertEquals(getMessageBytes(message), message);
    }

    @Test
    void testEquals() {
        assertEquals(getMessageBytes("äöüÄÖÜß"), getMessageBytes("äöüÄÖÜß"));
    }

    @NotNull
    private static MessageBytes getMessageBytes(String s) {
        MessageBytes b1 = MessageBytes.newInstance();
        byte[] buf = s.getBytes(StandardCharsets.ISO_8859_1);
        b1.setBytes(buf, 0, buf.length);
        return b1;
    }
}