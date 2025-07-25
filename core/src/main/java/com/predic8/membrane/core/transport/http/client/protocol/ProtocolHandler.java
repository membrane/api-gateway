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

public interface ProtocolHandler {

    String UPGRADED_PROTOCOL = "UPGRADED_PROTOCOL";

    default boolean canHandle(Exchange exchange, String protocol) throws ProtocolUpgradeDeniedException {
        throw new ProtocolUpgradeDeniedException(protocol);
    }

    Exchange handle(Exchange exchange, ConnectionFactory.OutgoingConnectionType connectionType, HostColonPort target) throws Exception;

    default void checkUpgradeRequest(Exchange exchange) throws ProtocolUpgradeDeniedException {
    }

    default void checkUpgradeResponse(Exchange exchange) {
    }

    default void cleanup(Exchange exchange) {

    }
}