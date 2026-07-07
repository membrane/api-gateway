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

package com.predic8.membrane.core.http;

/**
 * Thrown when an HTTP message has invalid framing that must be rejected rather
 * than interpreted, e.g. conflicting or malformed {@code Content-Length}
 * headers (RFC 9112 &sect;6.3).
 * <p>
 * Silently guessing which value to use would open the door to HTTP request
 * smuggling, so such messages are treated as an unrecoverable error.
 */
public class MalformedHeaderException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public MalformedHeaderException(String message) {
        super(message);
    }
}
