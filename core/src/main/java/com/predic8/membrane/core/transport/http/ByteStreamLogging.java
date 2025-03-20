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

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

import static java.nio.charset.StandardCharsets.US_ASCII;

/**
 * Traffic Logging ("Byte Stream Logging") can be enabled by setting the log level to <code>TRACE</code> on the
 * <code>com.predic8.membrane.core.transport.http.ByteStreamLogging</code> logger.
 * <p>
 * It will print all bytes transmitted or received by the HttpClient on a HTTP or HTTPS connection.
 * <p>
 * HTTPS content will be printed <b>unencrypted</b>.
 * <p>
 * This will create log entries like the following:
 * <code>
 * 08:15:50,047 TRACE 56 router /127.0.0.1:50835 ByteStreamLogging:52 {} - [c-569960249 out] [ 71 69 84 32 47 32 ... ] GET / HTTP/1.1
 * User-Agent: Jakarta Commons-HttpClient/3.1
 * ...
 * </code>
 * <p>
 * In this line,
 *
 * <ul>
 *     <li><code>c-569960249</code> is the identifier of the TCP (or TLS) connection. The number is randomly generated.</li>
 *     <li><code>out</code> (or <code>in</code>) identifies the direction of the data (from HttpClient's point of view).</li>
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
        log(name, b,0,b.length);
    }

    public static void log(String name, byte[] b, int off, int len){
        StringBuilder sb = new StringBuilder();
        sb.append("[").append(name).append("] ").append("[ ");
        for(int i = off; i < off+len; i++) {
            sb.append(b[i]).append(" ");
        }
        sb.append("] ");
        sb.append(new String(b,off,len, US_ASCII));
        log.trace(sb.toString());
    }

    public static OutputStream wrapConnectionOutputStream(OutputStream out, String name) {
        return new OutputStream() {
            @Override
            public void write(int b) throws IOException {
                log(name, b);
                out.write(b);
            }

            @Override
            public void write(byte[] b) throws IOException {
                log(name, b);
                out.write(b);
            }

            @Override
            public void write(byte[] b, int off, int len) throws IOException {
                log(name, b,off,len);
                out.write(b, off, len);
            }

            @Override
            public void close() throws IOException {
                out.close();
            }

            @Override
            public void flush() throws IOException {
                out.flush();
            }
        };
    }

    public static InputStream wrapConnectionInputStream(InputStream in, String name) {
        return new InputStream() {
            @Override
            public int read() throws IOException {
                int res = in.read();
                log(name, res);
                return res;
            }

            @Override
            public int read(byte[] b) throws IOException {
                int res = in.read(b);
                log(name, b);
                return res;
            }

            @Override
            public int read(byte[] b, int off, int len) throws IOException {
                int res = in.read(b, off, len);
                log(name, b,off,len);
                return res;
            }

            @Override
            public void close() throws IOException {
                in.close();
            }

            @Override
            public boolean markSupported() {
                return in.markSupported();
            }

            @Override
            public int available() throws IOException {
                return in.available();
            }

            @Override
            public long skip(long n) throws IOException {
                return in.skip(n);
            }

            @Override
            public synchronized void mark(int readlimit) {
                in.mark(readlimit);
            }

            @Override
            public synchronized void reset() throws IOException {
                in.reset();
            }
        };
    }

    public static boolean isLoggingEnabled() {
        return log.isTraceEnabled();
    }
}
