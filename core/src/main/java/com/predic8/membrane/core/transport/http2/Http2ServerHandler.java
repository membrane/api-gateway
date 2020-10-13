package com.predic8.membrane.core.transport.http2;

import org.apache.commons.lang.NotImplementedException;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;

public class Http2ServerHandler {
    private final Socket sourceSocket;
    private final InputStream srcIn;
    private final OutputStream srcOut;

    public Http2ServerHandler(Socket sourceSocket, InputStream srcIn, OutputStream srcOut) {
        this.sourceSocket = sourceSocket;
        this.srcIn = srcIn;
        this.srcOut = srcOut;
    }

    public void handle() {
        throw new NotImplementedException("handling HTTP/2 requests."); // TODO
    }
}
