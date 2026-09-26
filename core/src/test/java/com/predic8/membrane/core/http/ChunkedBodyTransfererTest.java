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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A zero-length chunk on the wire ({@code 0\r\n\r\n}) is the chunked-body terminator, so a
 * zero-length write must not emit anything; only {@code finish()} may terminate the body.
 * See <a href="https://github.com/membrane/api-gateway/issues/3347">#3347</a>.
 */
class ChunkedBodyTransfererTest {

    private ByteArrayOutputStream out;
    private ChunkedBodyTransferer transferer;

    @BeforeEach
    void setUp() {
        out = new ByteArrayOutputStream();
        transferer = new ChunkedBodyTransferer(out);
    }

    @Test
    void zeroLengthByteArrayWriteDoesNotTerminateBody() throws IOException {
        transferer.write("abc".getBytes(UTF_8), 0, 3);
        transferer.write(new byte[0], 0, 0);
        transferer.write("def".getBytes(UTF_8), 0, 3);
        transferer.finish(null);

        assertEquals("3\r\nabc\r\n3\r\ndef\r\n0\r\n\r\n", out.toString(UTF_8));
    }

    @Test
    void emptyChunkWriteDoesNotTerminateBody() throws IOException {
        transferer.write(new Chunk("abc".getBytes(UTF_8)));
        transferer.write(new Chunk(new byte[0]));
        transferer.write(new Chunk("def".getBytes(UTF_8)));
        transferer.finish(null);

        assertEquals("3\r\nabc\r\n3\r\ndef\r\n0\r\n\r\n", out.toString(UTF_8));
    }

    @Test
    void onlyZeroLengthWritesEmitSingleTerminator() throws IOException {
        transferer.write(new byte[0], 0, 0);
        transferer.write(new Chunk(new byte[0]));
        transferer.finish(null);

        assertEquals("0\r\n\r\n", out.toString(UTF_8));
    }
}
