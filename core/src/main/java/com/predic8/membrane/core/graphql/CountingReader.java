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
