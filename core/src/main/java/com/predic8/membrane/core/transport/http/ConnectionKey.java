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
package com.predic8.membrane.core.transport.http;

import com.google.common.base.Objects;
import com.predic8.membrane.core.transport.http.client.ProxyConfiguration;
import com.predic8.membrane.core.transport.ssl.SSLProvider;

import javax.annotation.Nullable;

public class ConnectionKey {
    // SSLProvider and ProxyConfiguration do not override equals() or hashCode(), but this is OK, as only a few will exist and are used read-only

    public final String host;
    public final int port;
    @Nullable
    private final SSLProvider sslProvider;
    @Nullable
    public final String serverName;
    @Nullable
    public final ProxyConfiguration proxy;
    @Nullable
    private final SSLProvider proxySSLProvider;
    @Nullable
    private final String[] applicationProtocols;

    public ConnectionKey(String host, int port, SSLProvider sslProvider, String serverName, ProxyConfiguration proxy,
                         @Nullable SSLProvider proxySSLProvider, @Nullable String[] applicationProtocols) {
        this.host = host;
        this.port = port;
        this.sslProvider = sslProvider;
        this.serverName = serverName;
        this.proxy = proxy;
        this.proxySSLProvider = proxySSLProvider;
        this.applicationProtocols = applicationProtocols;
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(host, port, sslProvider, serverName, proxy, proxySSLProvider, applicationProtocols);
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof ConnectionKey) || obj == null)
            return false;
        ConnectionKey other = (ConnectionKey) obj;
        return host.equals(other.host)
                && port == other.port
                && Objects.equal(sslProvider, other.sslProvider)
                && Objects.equal(serverName, other.serverName)
                && Objects.equal(proxy, other.proxy)
                && Objects.equal(proxySSLProvider, other.proxySSLProvider)
                && Objects.equal(applicationProtocols, other.applicationProtocols);
    }

    @Override
    public String toString() {
        return host + ":" + port + (sslProvider != null ? " with SSL" : "") + (proxy != null ? " via proxy" : "");
    }
}
