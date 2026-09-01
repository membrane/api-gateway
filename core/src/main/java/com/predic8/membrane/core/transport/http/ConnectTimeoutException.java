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

import java.net.SocketTimeoutException;

/**
 * Indicates that establishing the connection to the target timed out, as opposed to the target
 * accepting the connection but not answering in time. The JDK reports both as
 * {@link SocketTimeoutException} and only distinguishes them by message text.
 * <p>
 * The distinction matters for retries: no byte of the request has been sent yet, so the target
 * cannot have processed it and a retry is safe for any request method.
 * <p>
 * Extends {@link SocketTimeoutException} so that code catching the timeout in general keeps working.
 */
public class ConnectTimeoutException extends SocketTimeoutException {

    private static final long serialVersionUID = 1L;

    public ConnectTimeoutException(String message, Throwable cause) {
        super(message);
        initCause(cause);
    }
}
