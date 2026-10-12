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

package com.predic8.membrane.core.interceptor.tunnel;

import com.predic8.membrane.core.util.ConfigurationException;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertThrows;

class WebSocketInterceptorTest {

    @ParameterizedTest
    @ValueSource(ints = {0, -1, Integer.MAX_VALUE})
    void invalidMaxFramePayloadSizeIsRejected(int size) {
        final var webSocket = new WebSocketInterceptor();
        webSocket.setMaxFramePayloadSize(size);
        assertThrows(ConfigurationException.class, webSocket::init);
    }
}
