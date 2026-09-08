/* Copyright 2020 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */
package com.predic8.membrane.core.sslinterceptor;

import com.predic8.membrane.core.interceptor.Outcome;
import com.predic8.membrane.core.router.*;
import com.predic8.membrane.core.transport.ssl.SSLExchange;
import com.predic8.membrane.core.transport.ssl.TLSError;

/**
 * A plugin of {@link com.predic8.membrane.core.proxies.SSLProxy SSLProxy}: it sees every TLS
 * connection before it is forwarded to the backend and can reject it.
 * <p>
 * An sslProxy does not terminate TLS, so an implementation only gets what is known before the
 * handshake is handed on - the client's IP address and port, see {@link SSLExchange}. The encrypted
 * payload is never available, and neither is the server name the client requested.
 * <p>
 * Implementations are configuration elements themselves and are added as children of an sslProxy,
 * which accepts them through {@code allowForeign}. Membrane ships {@link SSLLogInterceptor} and
 * {@link RouterIpResolverInterceptor}.
 */
public interface SSLInterceptor {

    /**
     * Called once while the enclosing sslProxy is initialized, before any connection is handled.
     */
    void init(Router router);

    /**
     * Decides whether a connection may be forwarded.
     * <p>
     * Returning anything other than {@link Outcome#CONTINUE} ends the connection: the sslProxy
     * sends a fatal TLS alert carrying {@link SSLExchange#getError()} and closes the socket.
     * An implementation that rejects a connection must therefore call
     * {@link SSLExchange#setError(TLSError)} before returning.
     *
     * @param exc the connection being accepted; may be modified, e.g. to correct the remote IP
     * @return {@link Outcome#CONTINUE} to hand the connection to the next interceptor and finally
     *         to the backend, any other outcome to reject it
     * @throws Exception rejects the connection as well
     */
    Outcome handleRequest(SSLExchange exc) throws Exception;
}
