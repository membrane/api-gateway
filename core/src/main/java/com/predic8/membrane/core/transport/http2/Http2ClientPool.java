package com.predic8.membrane.core.transport.http2;

import com.predic8.membrane.core.transport.http.ConnectionKey;
import com.predic8.membrane.core.transport.http.ConnectionManager;
import com.predic8.membrane.core.transport.http.client.ProxyConfiguration;
import com.predic8.membrane.core.transport.ssl.SSLContext;
import com.predic8.membrane.core.transport.ssl.SSLProvider;

import javax.annotation.concurrent.GuardedBy;
import java.util.ArrayList;
import java.util.HashMap;

public class Http2ClientPool {
    @GuardedBy("availableConnections")
    private final HashMap<ConnectionKey, ArrayList<Http2Client>> availableConnections = new HashMap<>();

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
}
