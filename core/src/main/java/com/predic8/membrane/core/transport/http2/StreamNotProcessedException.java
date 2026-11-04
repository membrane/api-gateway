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

package com.predic8.membrane.core.transport.http2;

import java.io.IOException;

/**
 * The peer did not process the request, e.g. because it announced with GOAWAY that it is shutting down. Sending the
 * request again on another connection is safe for every method (RFC 9113 &sect;8.7).
 */
public class StreamNotProcessedException extends IOException {

    public StreamNotProcessedException(String message) {
        super(message);
    }

    public StreamNotProcessedException(String message, Throwable cause) {
        super(message, cause);
    }
}
