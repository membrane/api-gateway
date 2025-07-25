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

import com.predic8.membrane.core.exchange.*;
import com.predic8.membrane.core.http.*;
import com.predic8.membrane.core.transport.http.*;

import static com.predic8.membrane.core.exchange.Exchange.ALLOW_TCP;
import static java.lang.Boolean.TRUE;

public class TcpProtocolHandler implements ProtocolHandler {

    private static final String TCP = "tcp";

    @Override
    public boolean canHandle(Exchange exchange, String protocol) {
        return isTcpProtocolUpgradeAllowed(exchange, protocol);
    }

    @Override
    public Exchange handle(Exchange exchange, ConnectionFactory.OutgoingConnectionType connectionType, HostColonPort target) throws Exception {
        return null;
    }

    @Override
    public void checkUpgradeRequest(Exchange exchange) {

    }

    @Override
    public String checkUpgradeResponse(Exchange exchange, Response response) {
        return "";
    }

    public static boolean isTcpProtocolUpgradeAllowed(Exchange exc, String upgradeProtocol) {
        if (upgradeProtocol == null) {
            return false;
        }
        return upgradeProtocol.equalsIgnoreCase(TCP) && exc.getProperty(ALLOW_TCP) == TRUE;
    }
}
