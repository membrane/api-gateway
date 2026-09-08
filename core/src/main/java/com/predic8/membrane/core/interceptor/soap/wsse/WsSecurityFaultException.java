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
package com.predic8.membrane.core.interceptor.soap.wsse;

/**
 * Thrown when a WS-Security check on a message fails, i.e. the message is well-formed SOAP but not
 * acceptable. {@link WsSecurityInterceptor} turns this into the {@code soap:Fault} named by
 * {@link #getCode()}; the exception's message becomes the fault detail, which production mode
 * suppresses.
 */
class WsSecurityFaultException extends RuntimeException {

    private final WsSecurityFaultCode code;

    WsSecurityFaultException(WsSecurityFaultCode code, String message) {
        super(message);
        this.code = code;
    }

    WsSecurityFaultException(WsSecurityFaultCode code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    WsSecurityFaultCode getCode() {
        return code;
    }
}
