package com.predic8.membrane.core.util;

import org.jetbrains.annotations.*;

import java.io.*;

public class HttpTestUtil {

    /**
     * Converts a String with an HTTP message into an InputStream
     * @param request String with HTTP Message like GET / or HTTP/1.1 200 Ok
     * @return InputStream with the HTTP Message
     */
    public static @NotNull InputStream convertMessage(String request) {
        return new ByteArrayInputStream(request.stripIndent().replace("\n", "\r\n").getBytes());
    }
}
