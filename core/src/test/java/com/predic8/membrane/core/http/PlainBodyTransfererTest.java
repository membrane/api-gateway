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

import java.io.ByteArrayOutputStream;
import java.io.IOException;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;

class PlainBodyTransfererTest {

    @Test
    void writeChunk() throws IOException {
        var out = new ByteArrayOutputStream();
        new PlainBodyTransferer(out).write(new Chunk("hello".getBytes(UTF_8)));
        assertEquals("hello", out.toString(UTF_8));
    }

    /**
     * See <a href="https://github.com/membrane/api-gateway/issues/3350">#3350</a>
     */
    @Test
    void writeChunkWithNullContentWritesNothing() throws IOException {
        var out = new ByteArrayOutputStream();
        new PlainBodyTransferer(out).write(new Chunk(null));
        assertEquals(0, out.size());
    }

    /**
     * {@link ChunkedBodyTransferer} already skips a chunk with null content;
     * {@link PlainBodyTransferer} should behave the same.
     */
    @Test
    void chunkWithNullContentIsSkippedLikeChunkedBodyTransferer() throws IOException {
        var chunkedOut = new ByteArrayOutputStream();
        new ChunkedBodyTransferer(chunkedOut).write(new Chunk(null));
        assertEquals(0, chunkedOut.size());

        var plainOut = new ByteArrayOutputStream();
        new PlainBodyTransferer(plainOut).write(new Chunk(null));
        assertEquals(0, plainOut.size());
    }
}
