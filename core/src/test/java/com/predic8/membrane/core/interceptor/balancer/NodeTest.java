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
package com.predic8.membrane.core.interceptor.balancer;

import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.Request;
import com.predic8.membrane.core.transport.ssl.SSLProvider;
import org.junit.jupiter.api.Test;

import static com.predic8.membrane.core.exchange.Exchange.SSL_CONTEXT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

class NodeTest {

    @Test
    void destinationURLWithoutOutboundTLS() {
        assertEquals("http://localhost:2001/path", new Node("localhost", 2001).getDestinationURL(exchange()));
    }

    /**
     * See <a href="https://github.com/membrane/api-gateway/issues/2440">#2440</a>
     */
    @Test
    void destinationURLWithOutboundTLS() {
        var exc = exchange();
        exc.setProperty(SSL_CONTEXT, mock(SSLProvider.class));
        assertEquals("https://localhost:2001/path", new Node("localhost", 2001).getDestinationURL(exc));
    }

    private static Exchange exchange() {
        var exc = new Exchange(null);
        exc.setRequest(new Request());
        exc.getRequest().setUri("/path");
        return exc;
    }
}
