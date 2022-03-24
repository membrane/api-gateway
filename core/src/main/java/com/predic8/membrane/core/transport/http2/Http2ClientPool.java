/* Copyright 2022 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */
package com.predic8.membrane.core.transport.http2;

import com.predic8.membrane.core.transport.http.ConnectionKey;
import com.predic8.membrane.core.transport.http.client.ProxyConfiguration;
import com.predic8.membrane.core.transport.ssl.SSLContext;
import com.predic8.membrane.core.transport.ssl.SSLProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.concurrent.GuardedBy;
import java.util.*;

public class Http2ClientPool {

    private static Logger log = LoggerFactory.getLogger(Http2ClientPool.class.getName());

    private final long keepAliveTimeout;
    @GuardedBy("availableConnections")
    private final HashMap<ConnectionKey, ArrayList<Http2Client>> availableConnections = new HashMap<>();
    private final Timer timer;
    private volatile boolean shutdownWhenDone = false;

    public Http2ClientPool(long keepAliveTimeout) {
        this.keepAliveTimeout = keepAliveTimeout;
        long autoCloseInterval = keepAliveTimeout * 2;
        timer = new Timer("Connection Closer", true);
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                if (closeOldConnections() == 0 && shutdownWhenDone)
                    timer.cancel();
            }
        }, autoCloseInterval, autoCloseInterval);
    }

    public Http2Client reserveStream(String host, int port, SSLProvider sslProvider, String sniServerName, ProxyConfiguration proxy, SSLContext proxySSLContext) {
        ConnectionKey key = new ConnectionKey(host, port, sslProvider, sniServerName, proxy, proxySSLContext, null);
        synchronized(availableConnections) {
            ArrayList<Http2Client> http2Clients = availableConnections.get(key);
            if (http2Clients == null)
                return null;
            for (int i = 0; i < http2Clients.size(); i++) {
                Http2Client h2c = http2Clients.get(i);
                if (h2c.reserveStream())
                    return h2c;
            }
        }
        return null;
    }

    public void share(String host, int port, SSLProvider sslProvider, String sniServerName, ProxyConfiguration proxy, SSLContext proxySSLContext, Http2Client h2c) {
        ConnectionKey key = new ConnectionKey(host, port, sslProvider, sniServerName, proxy, proxySSLContext, null);
        synchronized(availableConnections) {
            ArrayList<Http2Client> http2Clients = availableConnections.computeIfAbsent(key, k -> new ArrayList<>());
            http2Clients.add(h2c);
        }
    }

    public void shutdownWhenDone() {
        shutdownWhenDone = true;
    }

    private int closeOldConnections() {
        ArrayList<ConnectionKey> toRemove = new ArrayList<>();
        ArrayList<Http2Client> toClose = new ArrayList<>();
        log.trace("closing old connections");
        int closed = 0, remaining;
        synchronized(availableConnections) {
            // close connections after their timeout
            for (Map.Entry<ConnectionKey, ArrayList<Http2Client>> e : availableConnections.entrySet()) {
                ArrayList<Http2Client> l = e.getValue();
                for (int i = 0; i < l.size(); i++) {
                    Http2Client o = l.get(i);
                    if (o.isIdle()) {
                        // replace [i] by [last]
                        if (i == l.size() - 1)
                            l.remove(i);
                        else
                            l.set(i, l.remove(l.size() - 1));
                        --i;
                        closed++;
                        toClose.add(o);
                    }
                }
                if (l.isEmpty())
                    toRemove.add(e.getKey());
            }
            for (ConnectionKey remove : toRemove)
                availableConnections.remove(remove);
            remaining = availableConnections.size();
        }
        for (Http2Client c : toClose) {
            try {
                c.close();
            } catch (Exception e) {
                // do nothing
            }
        }
        if (closed != 0)
            log.debug("closed " + closed + " connections");
        return remaining;
    }

}
