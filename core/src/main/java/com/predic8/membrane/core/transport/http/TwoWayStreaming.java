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
package com.predic8.membrane.core.transport.http;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.SocketException;

/**
 * Something supporting Two-Way-Streaming (like an incoming TCP connection).
 */
public interface TwoWayStreaming {
    InputStream getSrcIn();
    OutputStream getSrcOut();

    /**
     * Human-readable description of the remote interface. (e.g. IP+Port)
     */
    String getRemoteDescription();

    /**
     * Remove the SO_TIMEOUT (by setting it to 0). This allows for read() calls to hang indefinitely.
     * This allows connections to stay open while no data is transferred.
     */
    void removeSocketSoTimeout() throws SocketException;

    boolean isClosed();
    void close() throws IOException;
}
