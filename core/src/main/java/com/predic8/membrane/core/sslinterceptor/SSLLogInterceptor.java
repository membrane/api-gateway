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
package com.predic8.membrane.core.sslinterceptor;

import com.predic8.membrane.annot.MCElement;
import com.predic8.membrane.core.interceptor.Outcome;
import com.predic8.membrane.core.proxies.SSLProxy;
import com.predic8.membrane.core.router.Router;
import com.predic8.membrane.core.transport.ssl.SSLExchange;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.predic8.membrane.core.interceptor.Outcome.CONTINUE;

/**
 * @description Logs every TLS connection an sslProxy forwards, with the client it came from and the
 * backend it goes to. A passthrough connection is never decrypted and leaves no other trace, so
 * this is the way to see which proxy handled which connection when several share a port.
 * <p>Logged at INFO. Only the connection is logged, never its content: Membrane does not terminate
 * TLS here and never sees the plaintext. The name the client requested through the TLS SNI
 * extension is not logged either - the gateway matched it against the proxy's
 * <code>host</code> before the connection got here, and that host opens the log line.</p>
 * <p>See tutorials/ssl-tls/30-TLS-Passthrough.yaml.</p>
 * @yaml
 * <pre><code>
 * sslProxy:
 *   host: api.example.com
 *   port: 8443
 *   sslInterceptors:
 *     - sslLog: {}
 *   target:
 *     host: api.example.com
 *     port: 443
 * </code></pre>
 */
@MCElement(id = "sslProxy-sslLog", name = "sslLog", component = false)
public class SSLLogInterceptor implements SSLInterceptor {

    private static final Logger log = LoggerFactory.getLogger(SSLLogInterceptor.class);

    @Override
    public void init(Router router) {
    }

    @Override
    public Outcome handleRequest(SSLExchange exc) {
        if (exc.getRule() instanceof SSLProxy proxy)
            log.info("{}:{}: TLS connection from {}:{} to {}:{}", proxy.getHost(), proxy.getPort(),
                    exc.getRemoteAddrIp(), exc.getRemotePort(), proxy.getTarget().getHost(), proxy.getTargetPort());
        return CONTINUE;
    }
}
