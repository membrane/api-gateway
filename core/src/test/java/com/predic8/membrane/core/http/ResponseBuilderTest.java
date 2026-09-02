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

package com.predic8.membrane.core.http;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.*;

public class ResponseBuilderTest {

    @Test
    void okPlain() throws Exception {
        Response res = Response.ok().build();
        assertEquals(200,res.getStatusCode());
        assertEquals(res.header.getContentLength(), 0);
        assertInstanceOf( EmptyBody.class,res.getBody());
    }

    @Test
    void okWithBody() throws Exception {
        Response res = Response.ok("Hello").contentType("glue/instant").build();
        assertEquals(200,res.getStatusCode());
        assertEquals("Hello",res.getBodyAsStringDecoded());
        assertEquals("glue/instant",res.getHeader().getContentType());
        assertEquals(res.header.getContentLength(), 5);
        assertEquals("Hello", res.getBodyAsStringDecoded());
    }

    @Test
    void bodyEmpty() throws Exception {
        Response res = Response.ok("Empty me!").bodyEmpty().build();
        assertEquals(200,res.getStatusCode());
        assertEquals(res.header.getContentLength(), 0);
        assertInstanceOf( EmptyBody.class,res.getBody());
        assertEquals(0, res.getBody().getLength());
        assertEquals("", res.getBodyAsStringDecoded());
    }

    /**
     * The stream handed to body(stream, true) is ours to close once the body is done with it - whether
     * it was read completely or the read failed.
     */
    @Test
    void streamIsClosedWhenTheBodyIsComplete() throws Exception {
        CloseRecordingInputStream stream = new CloseRecordingInputStream(new ByteArrayInputStream("hi".getBytes()));

        Response res = Response.ok().body(stream, true).build();
        res.getBody().read();

        assertTrue(stream.closed);
    }

    @Test
    void streamIsClosedWhenReadingTheBodyFails() {
        CloseRecordingInputStream stream = new CloseRecordingInputStream(ThrowingInputStream.closedChannel("partial"));

        Response res = Response.ok().body(stream, true).build();

        assertThrows(ReadingBodyException.class, () -> res.getBody().read());
        assertTrue(stream.closed, "otherwise a failed response body leaks the stream");
    }

    private static class CloseRecordingInputStream extends FilterInputStream {
        boolean closed;

        CloseRecordingInputStream(InputStream in) {
            super(in);
        }

        @Override
        public void close() throws IOException {
            closed = true;
            super.close();
        }
    }
}
