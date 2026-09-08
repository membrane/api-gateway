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

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.channels.ClosedChannelException;

/**
 * Serves a prefix of bytes and then fails, simulating a client that aborts while sending its body.
 * Counts the read attempts made after the first failure, so tests can assert that a dead stream is not
 * touched again.
 */
public class ThrowingInputStream extends InputStream {

    private final InputStream prefix;
    private final IOException toThrow;
    private boolean failed;
    private int readCallsAfterFailure;

    public ThrowingInputStream(byte[] prefix, IOException toThrow) {
        this.prefix = new ByteArrayInputStream(prefix);
        this.toThrow = toThrow;
    }

    /**
     * Reproduces the AJP/Undertow case: the channel to the client is gone.
     */
    public static ThrowingInputStream closedChannel(String prefix) {
        return new ThrowingInputStream(prefix.getBytes(), new ClosedChannelException());
    }

    @Override
    public int read() throws IOException {
        countAndFail();
        int b = prefix.read();
        if (b == -1)
            fail();
        return b;
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
        countAndFail();
        int read = prefix.read(b, off, len);
        if (read == -1)
            fail();
        return read;
    }

    @Override
    public long skip(long n) throws IOException {
        countAndFail();
        long skipped = prefix.skip(n);
        if (skipped == 0)
            fail();
        return skipped;
    }

    private void countAndFail() throws IOException {
        if (failed) {
            readCallsAfterFailure++;
            fail();
        }
    }

    private void fail() throws IOException {
        failed = true;
        throw toThrow;
    }

    /**
     * @return how often this stream was read after it had already failed. Expected to stay 0.
     */
    public int getReadCallsAfterFailure() {
        return readCallsAfterFailure;
    }
}
