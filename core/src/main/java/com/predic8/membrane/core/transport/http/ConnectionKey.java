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

import com.predic8.membrane.core.transport.http.client.ProxyConfiguration;
import com.predic8.membrane.core.transport.ssl.SSLProvider;

import javax.annotation.Nullable;

public record ConnectionKey (
    // SSLProvider and ProxyConfiguration do not override equals() or hashCode(), but this is OK, as only a few will exist and are used read-only
    String host,
    int port,
    @Nullable SSLProvider sslProvider,
    @Nullable String serverName,
    @Nullable ProxyConfiguration proxy,
    @Nullable SSLProvider proxySSLProvider
    // 'applicationProtocols' is not part of the key, as HTTP/2 connections are never returned to the ConnectionManager
    // pool: They are shared using the Http2ClientPool instead.
) {

    @Override
    public String toString() {
        return host + ":" + port + (sslProvider != null ? " with SSL" : "") + (proxy != null ? " via proxy" : "");
    }
}
