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

package com.predic8.membrane.core.openapi.validators;

import com.predic8.membrane.core.openapi.model.InputStreamBody;
import com.predic8.membrane.core.openapi.util.OpenAPITestUtils;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static java.nio.charset.StandardCharsets.UTF_8;

public class InputStreamBodyTest {

    @Test
    public void asString() throws IOException {
        assertEquals("foo", new InputStreamBody(OpenAPITestUtils.toInputStrom("foo")).asString());
    }

    @Test
    void asStringClosesTheStream() throws IOException {
        final var in = new CloseFlagStream("foo");
        new InputStreamBody(in).asString();
        assertTrue(in.closed);
    }

    @Test
    void asBytesClosesTheStream() throws IOException {
        final var in = new CloseFlagStream("foo");
        new InputStreamBody(in).asBytes();
        assertTrue(in.closed);
    }

    @Test
    void asStringClosesTheStreamWhenReadingFails() {
        final var in = new FailingStream();
        assertThrows(IOException.class, () -> new InputStreamBody(in).asString());
        assertTrue(in.closed);
    }

    @Test
    void asBytesClosesTheStreamWhenReadingFails() {
        final var in = new FailingStream();
        assertThrows(IOException.class, () -> new InputStreamBody(in).asBytes());
        assertTrue(in.closed);
    }

    private static class FailingStream extends InputStream {
        boolean closed;

        @Override
        public int read() throws IOException {
            throw new IOException("read failed");
        }

        @Override
        public void close() {
            closed = true;
        }
    }

    private static class CloseFlagStream extends ByteArrayInputStream {
        boolean closed;

        CloseFlagStream(String content) {
            super(content.getBytes(UTF_8));
        }

        @Override
        public void close() {
            closed = true;
        }
    }

    /**
     * Only the first JSON value would be validated while the whole body is forwarded.
     */
    @Test
    void trailingJsonValue() {
        assertThrows(IOException.class, () -> new InputStreamBody(OpenAPITestUtils.toInputStrom("""
                {"name":"Bob"}
                {"age":-1}
                """)).getJson());
    }
}