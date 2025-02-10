/* Copyright 2013 predic8 GmbH, www.predic8.com

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

import org.jetbrains.annotations.*;
import org.junit.jupiter.api.*;

import java.util.*;

import static com.predic8.membrane.core.Constants.*;
import static com.predic8.membrane.core.http.ChunksBuilder.chunks;
import static org.junit.jupiter.api.Assertions.*;

class ChunksBuilderTest {

    @Test
    void simple() {
        assertEquals(List.of("5", "hello", "0"), getLines(chunks().add("hello")));
    }

    @Test
    void longer() {
        String[] poem = {"Garbage collection sweeps the memory's stage,",
                "As threads weave tales on each digital page.",
                "Exceptions are caught, errors refined,",
                "In the code of life, resilience we find."};

        assertEquals(List.of( "2d",poem[0], "2c",poem[1], "26", poem[2], "28", poem[3], "0"),
                getLines(chunks().add(poem[0]).add(poem[1]).add(poem[2]).add(poem[3])));
    }

    private static @NotNull List<String> getLines(ChunksBuilder builder) {
        byte[] bytes = builder.build();
        assertEquals((byte) '0', bytes[bytes.length - 5]);
        assertEquals(13, bytes[bytes.length - 4]);
        assertEquals(10, bytes[bytes.length - 3]);
        assertEquals(13, bytes[bytes.length - 2]);
        assertEquals(10, bytes[bytes.length - 1]);
        return Arrays.asList(new String(bytes).split(CRLF));
    }
}