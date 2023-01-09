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

package com.predic8.membrane.core.graphql;

import java.io.IOException;
import java.io.Reader;

public class CountingReader extends Reader {
    private final Reader reader;
    private long markedPosition;
    private long position;

    public CountingReader(Reader reader) {
        this.reader = reader;
    }

    @Override
    public int read(char[] cbuf, int off, int len) throws IOException {
        throw new RuntimeException("not implemented");
    }

    @Override
    public int read() throws IOException {
        int c = reader.read();
        if (c != -1)
            position++;
        return c;
    }

    @Override
    public void mark(int readAheadLimit) throws IOException {
        reader.mark(readAheadLimit);
        markedPosition = position;
    }

    @Override
    public void reset() throws IOException {
        reader.reset();
        position = markedPosition;
    }

    @Override
    public void close() throws IOException {
        reader.close();
    }

    public long position() {
        return position;
    }
}
