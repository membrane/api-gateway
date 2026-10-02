/*
 *  Copyright 2017 predic8 GmbH, www.predic8.com
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

package com.predic8.membrane.core.transport.http;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.util.concurrent.ThreadLocalRandom;

import static java.lang.System.lineSeparator;
import static java.nio.charset.StandardCharsets.US_ASCII;

/**
 * Traffic Logging ("Byte Stream Logging") can be enabled by setting the log level to <code>TRACE</code> on the
 * <code>com.predic8.membrane.core.transport.http.ByteStreamLogging</code> logger.
 * <p>
 * It will print all bytes transmitted or received by the HttpClient on a HTTP or HTTPS connection.
 * <p>
 * HTTPS content will be printed <b>unencryptedly</b>.
 * <p>
 * This will create log entries like the following:
 * <code>
 * 08:15:50,047 TRACE 56 router /127.0.0.1:50835 ByteStreamLogging:52 {} - [membrane=>backend 569960249] [ 71 69 84 32 47 32 ... ]
 * GET / HTTP/1.1
 * User-Agent: Jakarta Commons-HttpClient/3.1
 * ...
 * </code>
 * <p>
 * In this line,
 *
 * <ul>
 *     <li><code>membrane=>backend</code> identifies the direction of the data: <code>client=>membrane</code>,
 *     <code>membrane=>client</code>, <code>membrane=>backend</code> or <code>backend=>membrane</code>.</li>
 *     <li><code>569960249</code> is the identifier of the TCP (or TLS) connection. The number is randomly generated
 *     and the same for both directions of a connection.</li>
 *     <li>The numbers <code>71, 69, ...</code> are the integer values of all bytes.</li>
 *     <li><code>GET / HTTP/1.1...</code> is the ASCII representation of all bytes.</li>
 * </ul>
 */
public class ByteStreamLogging {

    static final Logger log = LoggerFactory.getLogger(ByteStreamLogging.class);

    public static void log(String name, int b){
        log(name, new byte[]{(byte)b});
    }

    public static void log(String name, byte... b){
        log(name, b, 0, b.length);
    }

    public static void log(String name, byte[] b, int off, int len){
        // TRACE can be switched off while connections wrapped earlier are still open
        if (!log.isTraceEnabled())
            return;
        StringBuilder sb = new StringBuilder();
        sb.append("[").append(name).append("] ").append("[ ");
        for(int i = off; i < off+len; i++) {
            sb.append(b[i]).append(" ");
        }
        sb.append("]");
        sb.append(lineSeparator());
        sb.append(new String(b, off, len, US_ASCII));
        log.trace(sb.toString());
    }

    public static OutputStream wrapConnectionOutputStream(OutputStream out, String name) {
        return new LoggingOutputStream(out, name);
    }

    public static InputStream wrapConnectionInputStream(InputStream in, String name) {
        return new LoggingInputStream(in, name);
    }

    /**
     * {@link FilterOutputStream} delegates everything but writes; the array write is overridden as
     * well, because the inherited one would forward the bytes one at a time.
     */
    private static class LoggingOutputStream extends FilterOutputStream {

        private final String name;

        LoggingOutputStream(OutputStream out, String name) {
            super(out);
            this.name = name;
        }

        @Override
        public void write(int b) throws IOException {
            log(name, b);
            out.write(b);
        }

        @Override
        public void write(byte[] b, int off, int len) throws IOException {
            log(name, b, off, len);
            out.write(b, off, len);
        }
    }

    /**
     * {@link FilterInputStream} delegates everything but reads. The inherited {@code read(byte[])}
     * goes through {@link #read(byte[], int, int)}.
     */
    private static class LoggingInputStream extends FilterInputStream {

        private final String name;

        LoggingInputStream(InputStream in, String name) {
            super(in);
            this.name = name;
        }

        @Override
        public int read() throws IOException {
            int res = in.read();
            if (res != -1)
                log(name, res);
            return res;
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            int res = in.read(b, off, len);
            if (res != -1)
                log(name, b, off, res);
            return res;
        }
    }

    public static boolean isLoggingEnabled() {
        return log.isTraceEnabled();
    }

    /**
     * @return a random identifier for a connection, shared by both of its directions in the log
     */
    public static int newConnectionId() {
        return ThreadLocalRandom.current().nextInt(Integer.MAX_VALUE);
    }
}
