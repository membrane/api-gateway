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
package com.predic8.membrane.core.interceptor.adminApi;

import com.predic8.membrane.core.transport.ws.WebSocketConnection;
import com.predic8.membrane.core.transport.ws.WebSocketFrame;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AdminApiObserver extends WebSocketConnection {

    private static final Logger log = LoggerFactory.getLogger(AdminApiObserver.class);

    @Override
    public void onMessage(WebSocketFrame frame) {
        log.info("Received message from client: " + frame);
    }
}
