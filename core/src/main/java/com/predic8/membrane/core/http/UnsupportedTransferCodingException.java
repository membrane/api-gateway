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
 * Thrown when a message carries a transfer coding other than a single <tt>chunked</tt>, e.g.
 * <tt>Transfer-Encoding: gzip, chunked</tt> (RFC 9112 &sect;7). Membrane does not decode such codings,
 * so it cannot get at the content. A request is answered with 501 Not Implemented
 * (RFC 9112 &sect;6.1), a backend response with 502 Bad Gateway.
 */
public class UnsupportedTransferCodingException extends MalformedHeaderException {

    private static final long serialVersionUID = 1L;

    public UnsupportedTransferCodingException(String message) {
        super(message);
    }
}
