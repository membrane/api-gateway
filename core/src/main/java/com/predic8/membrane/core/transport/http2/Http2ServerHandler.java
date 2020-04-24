package com.predic8.membrane.core.transport.http2;

import com.predic8.membrane.core.transport.http2.frame.Frame;
import com.predic8.membrane.core.util.ByteUtil;
import org.apache.commons.lang.NotImplementedException;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;

public class Http2ServerHandler {
    private static final byte[] PREFACE = new byte[] { 0x50, 0x52, 0x49, 0x20, 0x2a, 0x20, 0x48, 0x54, 0x54, 0x50, 0x2f, 0x32, 0x2e,
            0x30, 0x0d, 0x0a, 0x0d, 0x0a, 0x53, 0x4d, 0x0d, 0x0a, 0x0d, 0x0a };

    private final Socket sourceSocket;
    private final InputStream srcIn;
    private final OutputStream srcOut;

    public Http2ServerHandler(Socket sourceSocket, InputStream srcIn, OutputStream srcOut) {
        this.sourceSocket = sourceSocket;
        this.srcIn = srcIn;
        this.srcOut = srcOut;
    }

    public void handle() throws IOException {
        byte[] preface = ByteUtil.readByteArray(srcIn, 24);

        if (!isCorrectPreface(preface))
            throw new RuntimeException("Incorrect Preface.");

        Frame frame = new Frame();
        frame.read(srcIn);
        System.out.println(frame);
        // TODO: handle frame

        while(true) {
            frame = new Frame();
            frame.read(srcIn);
            System.out.println(frame);
            // TODO: handle frame

        }
    }

    private boolean isCorrectPreface(byte[] preface) {
        if (preface.length != PREFACE.length)
            return false;
        for (int i = 0; i < PREFACE.length; i++)
            if (preface[i] != PREFACE[i])
                return false;
        return true;
    }
}
