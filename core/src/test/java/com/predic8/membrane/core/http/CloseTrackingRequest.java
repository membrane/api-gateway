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

import java.io.ByteArrayOutputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.GZIPOutputStream;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * A gzip-encoded request that records whether every raw body stream handed out was closed. A decoder such as
 * {@code GZIPInputStream} closes the stream it wraps, so this tells a test whether the code under test closed the
 * decoded stream it opened (which holds a native zlib {@code Inflater}).
 */
public class CloseTrackingRequest extends Request {

    private final List<Boolean> closed = new ArrayList<>();

    public CloseTrackingRequest(String contentType, byte[] plainBody) throws IOException {
        final var compressed = new ByteArrayOutputStream();
        try (var gzip = new GZIPOutputStream(compressed)) {
            gzip.write(plainBody);
        }
        setBody(new Body(compressed.toByteArray()));
        getHeader().setContentType(contentType);
        getHeader().setValue(Header.CONTENT_ENCODING, "gzip");
    }

    public CloseTrackingRequest(String contentType, String plainBody) throws IOException {
        this(contentType, plainBody.getBytes(UTF_8));
    }

    @Override
    public InputStream getBodyAsStream() throws ReadingBodyException {
        final int index;
        synchronized (closed) {
            index = closed.size();
            closed.add(false);
        }
        return new FilterInputStream(super.getBodyAsStream()) {
            @Override
            public void close() throws IOException {
                synchronized (closed) {
                    closed.set(index, true);
                }
                super.close();
            }
        };
    }

    /** @return whether at least one stream was opened and all of them were closed */
    public boolean allStreamsClosed() {
        synchronized (closed) {
            return !closed.isEmpty() && !closed.contains(false);
        }
    }
}
