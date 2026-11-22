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
 * Thrown when a request line carries a well-formed HTTP version whose major version is not 1, e.g. <tt>HTTP/2.0</tt>
 * or <tt>HTTP/0.9</tt>. The request is answered with 505 HTTP Version Not Supported (RFC 9110 &sect;15.6.6).
 * HTTP/2 is negotiated with TLS (ALPN) and does not use a request line.
 */
public class UnsupportedHttpVersionException extends MalformedHeaderException {

    private static final long serialVersionUID = 1L;

    public UnsupportedHttpVersionException(String message) {
        super(message);
    }
}
