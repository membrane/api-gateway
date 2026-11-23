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

package com.predic8.membrane.tutorials.rate_limiting.redis;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * Speaks just enough of the Redis protocol for the Redis rate limit store: <code>INCR</code> and
 * <code>PEXPIREAT</code> inside <code>MULTI</code>/<code>EXEC</code>. Expiry is ignored, as a test runs within one
 * period. Every other command is answered with <code>OK</code>.
 */
class FakeRedisServer implements AutoCloseable {

    private final ServerSocket serverSocket = new ServerSocket(0);
    private final Map<String, Long> counters = new ConcurrentHashMap<>();
    private final ExecutorService executor = Executors.newCachedThreadPool();

    FakeRedisServer() throws IOException {
        executor.execute(this::accept);
    }

    int getPort() {
        return serverSocket.getLocalPort();
    }

    private void accept() {
        while (!serverSocket.isClosed()) {
            try {
                final var socket = serverSocket.accept();
                executor.execute(() -> serve(socket));
            } catch (IOException e) {
                return;
            }
        }
    }

    private void serve(Socket socket) {
        try (socket) {
            final var in = new BufferedInputStream(socket.getInputStream());
            final var out = new BufferedOutputStream(socket.getOutputStream());
            final var queued = new ArrayList<List<String>>();
            var inTransaction = false;
            List<String> command;
            while ((command = readCommand(in)) != null) {
                final var name = command.getFirst().toUpperCase(Locale.ROOT);
                if (name.equals("MULTI")) {
                    inTransaction = true;
                    write(out, "+OK\r\n");
                } else if (name.equals("EXEC")) {
                    inTransaction = false;
                    final var replies = new StringBuilder("*" + queued.size() + "\r\n");
                    queued.forEach(c -> replies.append(execute(c)));
                    queued.clear();
                    write(out, replies.toString());
                } else if (inTransaction) {
                    queued.add(command);
                    write(out, "+QUEUED\r\n");
                } else {
                    write(out, execute(command));
                }
            }
        } catch (IOException ignored) {
            // The client closed the connection
        }
    }

    private String execute(List<String> command) {
        return switch (command.getFirst().toUpperCase(Locale.ROOT)) {
            case "INCR" -> ":" + counters.merge(command.get(1), 1L, Long::sum) + "\r\n";
            case "PEXPIREAT" -> ":1\r\n";
            default -> "+OK\r\n";
        };
    }

    /**
     * @return the arguments of the next command, or <code>null</code> at the end of the stream
     */
    private static List<String> readCommand(InputStream in) throws IOException {
        final var header = readLine(in);
        if (header == null)
            return null;
        final var count = Integer.parseInt(header.substring(1));
        final var args = new ArrayList<String>(count);
        for (int i = 0; i < count; i++) {
            final var length = Integer.parseInt(readLine(in).substring(1));
            final var arg = in.readNBytes(length);
            in.readNBytes(2);
            args.add(new String(arg, UTF_8));
        }
        return args;
    }

    private static String readLine(InputStream in) throws IOException {
        final var line = new ByteArrayOutputStream();
        int b;
        while ((b = in.read()) != -1) {
            if (b == '\r') {
                in.read();
                return line.toString(UTF_8);
            }
            line.write(b);
        }
        return null;
    }

    private static void write(OutputStream out, String reply) throws IOException {
        out.write(reply.getBytes(UTF_8));
        out.flush();
    }

    @Override
    public void close() throws IOException {
        serverSocket.close();
        executor.shutdownNow();
    }
}
