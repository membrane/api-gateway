package com.predic8.membrane.core.transport.http;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.SocketException;

/**
 * Something supporting Two-Way-Streaming (like an incoming TCP connection).
 */
public interface TwoWayStreaming {
    InputStream getSrcIn();
    OutputStream getSrcOut();

    /**
     * Human-readable description of the remote interface. (e.g. IP+Port)
     */
    String getRemoteDescription();

    /**
     * Remove the SO_TIMEOUT (by setting it to 0). This allows for read() calls to hang indefinitely.
     * This allows connections to stay open while no data is transferred.
     */
    void removeSocketSoTimeout() throws SocketException;

    boolean isClosed();
    void close() throws IOException;
}
