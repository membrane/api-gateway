/* Copyright 2009, 2012 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */

package com.predic8.membrane.core.transport.http;

import com.predic8.membrane.core.exceptions.*;
import com.predic8.membrane.core.transport.*;
import com.predic8.membrane.core.transport.ssl.*;
import com.predic8.membrane.core.util.*;
import org.slf4j.*;

import javax.annotation.*;
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

import static java.lang.Boolean.*;

public class HttpEndpointListener extends Thread {

    private static final Logger log = LoggerFactory.getLogger(HttpEndpointListener.class.getName());
    private static final byte[] TLS_ALERT_INTERNAL_ERROR = {21 /* alert */, 3, 1 /* TLS 1.0 */, 0, 2 /* length: 2 bytes */,
            2 /* fatal */, 80 /* unrecognized_name */};


    private final ServerSocket serverSocket;
    private final HttpTransport transport;
    private final SSLProvider sslProvider;
    private final ConcurrentHashMap<Socket, Boolean> idleSockets = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Socket, Boolean> openSockets = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<InetAddress, ClientInfo> ipConnectionCount = new ConcurrentHashMap<>();
    private TimerManager timerManager; // a TimerManager we have created ourselves

    private volatile boolean closed;

    private static class ClientInfo {
        public final AtomicInteger count;
        public volatile long lastUse;

        public ClientInfo() {
            count = new AtomicInteger();
            lastUse = System.currentTimeMillis();
        }

        public int get() {
            return count.get();
        }

        public void decrementAndGet() {
            count.decrementAndGet();
            lastUse = System.currentTimeMillis();
        }

        public boolean compareAndSet(int expected, int update) {
            boolean b = count.compareAndSet(expected, update);
            // TODO: fix this for timezone switch (and wherever System.currentTimeMillis() is used)
            lastUse = System.currentTimeMillis();
            return b;
        }
    }

    public HttpEndpointListener(IpPort p, HttpTransport transport, SSLProvider sslProvider, @Nullable TimerManager timerManager) throws IOException {
        this.transport = transport;
        this.sslProvider = sslProvider;
        try {
            serverSocket = getServerSocket(p);

            if (timerManager == null)
                timerManager = this.timerManager = new TimerManager();
            timerManager.schedulePeriodicTask(new TimerTask() {
                @Override
                public void run() {
                    Collection<ClientInfo> values = ipConnectionCount.values();
                    for (ClientInfo v : values) {

                        if (v.count.get() > 0)
                            continue;
                        // TODO: set count to -1 to signalize that removal is in progress
                        if (System.currentTimeMillis() - v.lastUse < 10 * 60 * 1000)
                            continue;
                        values.remove(v);
                    }
                }
            }, 60000, "HttpEndpointListener removing old IPs");

            final String s = p.toShortString();
            setName("Connection Acceptor " + s);
            log.info("listening at {}", s);
        } catch (BindException e) {
            throw new PortOccupiedException(p);
        }
    }

    private ServerSocket getServerSocket(IpPort p) throws IOException {
        if (sslProvider != null)
            return sslProvider.createServerSocket(p.port(), transport.getBacklog(), p.ip());

        return new ServerSocket(p.port(), transport.getBacklog(), p.ip());
    }

    @Override
    public void run() {
        while (!closed) {
            try {
                Socket socket = serverSocket.accept();

                InetAddress remoteIp = getRemoteIp(socket);
                ClientInfo connectionCount = getClientInfo(remoteIp);
                if (isConnectionWithinLimit(socket, remoteIp, connectionCount)) {
                    openSockets.put(socket, TRUE);
                    try {
                        if (log.isDebugEnabled())
                            log.debug("Accepted connection from {}", socket.getRemoteSocketAddress());
                        transport.getExecutorService().execute(new HttpServerHandler(socket, this));
                    } catch (RejectedExecutionException e) {
                        connectionCount.decrementAndGet();
                        openSockets.remove(socket);
                        log.error("HttpServerHandler execution rejected. Might be due to a proxies.xml hot deployment in progress or a low"
                                  + " value for <transport maxThreadPoolSize=\"...\">.");
                        socket.close();
                    }
                }
            } catch (SocketException e) {
                String message = e.getMessage();
                if (message != null && (message.endsWith("socket closed") || message.endsWith("Socket closed"))) {
                    log.debug("socket closed.");
                    break;
                } else {
                    log.error("", e);
                }
            } catch (NullPointerException e) {
                // Ignore this. serverSocket variable is set null during a loop in the process of closing server socket.
                log.error("", e);
            } catch (Exception e) {
                log.error("", e);
            } catch (Error e) {
                try {
                    log.error("", e);
                } catch (Throwable ignored) {
                }
                try {
                    System.err.println(e.getMessage());
                    System.err.println("Terminating because of Error in HttpEndpointListener.");
                } catch (Throwable ignored) {
                }
                System.exit(1);
            }
        }
    }

    private ClientInfo getClientInfo(InetAddress remoteIp) {
        ClientInfo connectionCount = ipConnectionCount.get(remoteIp);

        if (connectionCount != null)
            return connectionCount;

        connectionCount = new ClientInfo();
        ClientInfo oldconnectionCount = connectionCount;
        connectionCount = ipConnectionCount.putIfAbsent(remoteIp, connectionCount);

        if (connectionCount == null)
            return oldconnectionCount;

        return connectionCount;
    }

    private boolean isConnectionWithinLimit(Socket socket, InetAddress remoteIp, ClientInfo connectionCount) throws IOException {
        int concurrentConnectionLimitPerIp = transport.getConcurrentConnectionLimitPerIp();

        // -1 == NoLimit => Ok
        if (concurrentConnectionLimitPerIp == -1)
            return true;

        boolean connectionWithinLimit = true;
        while (true) {
            int currentConnections = connectionCount.get();
            // TODO: if count == -1 -> try to get the counter in a while loop
            if (currentConnections >= concurrentConnectionLimitPerIp) {
                log.warn(constructLogMessage(new StringBuilder(), remoteIp, NetworkUtil.readUpTo1KbOfDataFrom(socket, new byte[1023])));
                writeRateLimitReachedToSource(socket);
                socket.close();
                connectionWithinLimit = false;
                break;
            }
            if (connectionCount.compareAndSet(currentConnections, currentConnections + 1))
                break;
        }
        return connectionWithinLimit;
    }

    public void closePort() throws IOException {
        closed = true;
        if (serverSocket != null && !serverSocket.isClosed())
            serverSocket.close();
        if (sslProvider != null)
            sslProvider.stop();
    }

    /**
     * Closes all connections or those that are <i>currently</i> idle.
     *
     * @param onlyIdle whether to close only idle connections
     * @return true, if there are no more open connections
     */
    public boolean closeConnections(boolean onlyIdle) {
        if (!closed)
            throw new IllegalStateException("please call closePort() fist.");
        for (Socket s : (onlyIdle ? idleSockets : openSockets).keySet()) {
            if (s.isClosed())
                continue;
            try {
                s.close();
            } catch (IOException e) {
                // Do not throw to let other sockets close as well
                log.error("Error closing connection to {}", s.getRemoteSocketAddress());
            }
        }
        if (timerManager != null)
            timerManager.shutdown();
        return openSockets.isEmpty();
    }

    void setIdleStatus(Socket socket, boolean isIdle) throws IOException {
        if (isIdle) {
            if (closed) {
                socket.close();
                throw new SocketException();
            }
            idleSockets.put(socket, TRUE);
        } else {
            idleSockets.remove(socket);
        }
    }

    void setOpenStatus(Socket socket) {
        openSockets.remove(socket);

        ClientInfo clientInfo = ipConnectionCount.get(getRemoteIp(socket));
        if (clientInfo != null) {
            clientInfo.count.decrementAndGet();
        }
    }

    public int getNumberOfOpenConnections() {
        return openSockets.size();
    }

    public HttpTransport getTransport() {
        return transport;
    }

    public boolean isClosed() {
        return closed;
    }

    public SSLProvider getSslProvider() {
        return sslProvider;
    }

    private void writeRateLimitReachedToSource(Socket sourceSocket) throws IOException {
        if (sslProvider != null)
            sourceSocket.getOutputStream().write(TLS_ALERT_INTERNAL_ERROR, 0, TLS_ALERT_INTERNAL_ERROR.length);
        else {
            log.warn("Limit of {} concurrent connections per client is reached.", transport.getConcurrentConnectionLimitPerIp());
            ProblemDetails.internal(false)
                    .statusCode(429)
                    .addSubType("rate-limit")
                    .addSubType("write-limit-reached")
                    .title("Limit of concurrent connections per client is reached.")
                    .detail("There is a limit of concurrent connections per client to avoid denial of service attacks.")
                    .build()
                    .write(sourceSocket.getOutputStream(), false);
        }
        sourceSocket.getOutputStream().flush();
    }

    private String constructLogMessage(StringBuilder sb, InetAddress ip, Pair<byte[], Integer> receivedContent) {
        return sb
                .append("Concurrent connection limit reached for IP: ").append(ip.toString()).append(System.lineSeparator())
                .append("Received the following content").append(System.lineSeparator())
                .append("===START===").append(System.lineSeparator())
                .append(new String(receivedContent.first(), 0, receivedContent.second())).append(System.lineSeparator())
                .append("===END===").toString();
    }

    private InetAddress getRemoteIp(Socket socket) {
        return ((InetSocketAddress) socket.getRemoteSocketAddress()).getAddress();
    }
}
