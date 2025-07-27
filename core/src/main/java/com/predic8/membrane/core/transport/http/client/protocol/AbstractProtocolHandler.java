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
import com.predic8.membrane.core.transport.http.*;
import com.predic8.membrane.core.transport.http.client.*;

public abstract class AbstractProtocolHandler implements ProtocolHandler {

    protected final HttpClientConfiguration configuration;
    protected final ConnectionFactory connectionFactory;

    public AbstractProtocolHandler(HttpClientConfiguration hcc, ConnectionFactory cf) {
        this.configuration = hcc;
        this.connectionFactory = cf;
    }

    @Override
    public boolean canHandle(Exchange exchange, String protocol) {
        return false;
    }

    @Override
    public void checkUpgradeRequest(Exchange exchange) throws ProtocolUpgradeDeniedException {}

    @Override
    public void checkUpgradeResponse(Exchange exchange) {}

    @Override
    public void cleanup(Exchange exchange) {}

    protected static boolean isUpgradeRequest(Exchange exchange, String protocol) {
        String upgrade = exchange.getRequest().getHeader().getUpgradeProtocol();
        return upgrade != null && upgrade.equalsIgnoreCase(protocol);
    }
}
