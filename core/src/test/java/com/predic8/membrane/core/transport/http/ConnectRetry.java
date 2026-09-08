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

package com.predic8.membrane.core.transport.http;

import com.predic8.membrane.core.exchange.Exchange;

import java.net.ConnectException;

/**
 * Repeats a call whose connect was refused, for tests that open several hundred connections at once.
 * <p>
 * Windows caps the effective listen backlog at 200 regardless of {@link HttpTransport#setBacklog(int)} and
 * answers a connect that overflows the accept queue with RST. Linux instead drops the SYN, so the client
 * kernel retransmits and the connect eventually succeeds. Without this retry, a burst of more than ~200
 * connections loses everything the acceptor has not drained yet, on Windows only.
 * <p>
 * Nothing has been sent when a connect is refused, so repeating the call is safe for any request method.
 * Retrying the same {@link Exchange} is what {@link com.predic8.membrane.core.transport.http.client.RetryHandler}
 * does internally as well.
 */
public final class ConnectRetry {

    private static final int ATTEMPTS = 50;
    private static final long DELAY_MS = 50;

    private ConnectRetry() {
    }

    public static void call(HttpClient client, Exchange exchange) throws Exception {
        for (var attempt = 1; ; attempt++) {
            try {
                client.call(exchange);
                return;
            } catch (ConnectException e) {
                if (attempt == ATTEMPTS)
                    throw e;
                Thread.sleep(DELAY_MS);
            }
        }
    }
}
