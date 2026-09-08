/* Copyright 2025 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */

package com.predic8.membrane.core.transport.http.client.protocol;

import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.transport.http.ConnectionFactory;
import com.predic8.membrane.core.transport.http.ConnectionFactory.OutgoingConnectionType;
import com.predic8.membrane.core.transport.http.HostColonPort;
import com.predic8.membrane.core.transport.http.client.HttpClientConfiguration;

import static com.predic8.membrane.core.exchange.Exchange.ALLOW_TCP;
import static java.lang.Boolean.TRUE;

public class TcpProtocolHandler extends AbstractProtocolHandler {

    public static final String TCP = "tcp";

    public TcpProtocolHandler(HttpClientConfiguration configuration, ConnectionFactory connectionFactory) {
        super(configuration, connectionFactory);
    }

    @Override
    public boolean canHandle(Exchange exchange, String protocol) {
        if (protocol == null) {
            return false;
        }
        return protocol.equalsIgnoreCase(TCP) && exchange.getProperty(ALLOW_TCP) == TRUE;
    }

    @Override
    public void handle(Exchange exchange, OutgoingConnectionType connectionType, HostColonPort target) {
        throw new IllegalStateException(
                "TCP handler should not be called directly. " +
                "Use HTTP/1.1 with Upgrade header.");
    }

}
