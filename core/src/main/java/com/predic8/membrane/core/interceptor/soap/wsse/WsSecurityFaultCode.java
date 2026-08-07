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
 * The WS-Security fault codes and their prescribed fault strings, as specified by
 * <i>WS-Security 1.1, Table 12 (Error Handling)</i>. The fault string is deliberately the
 * spec-mandated, non-specific text: a WS-Security fault must not reveal <i>why</i> a check failed,
 * since that turns the fault into an oracle. The concrete reason goes into the fault detail, which
 * production mode suppresses.
 */
enum WsSecurityFaultCode {

    /** A missing, malformed, or otherwise unprocessable {@code wsse:Security} header. */
    INVALID_SECURITY("InvalidSecurity", "An error was discovered processing the <wsse:Security> header."),

    /** A security token that is present and resolvable, but not well-formed or not of a usable kind. */
    INVALID_SECURITY_TOKEN("InvalidSecurityToken", "An invalid security token was provided."),

    /** A token reference that cannot be resolved to the token it names. */
    SECURITY_TOKEN_UNAVAILABLE("SecurityTokenUnavailable", "Referenced security token could not be retrieved."),

    /** A credential that does not authenticate: wrong password or digest, stale or replayed nonce. */
    FAILED_AUTHENTICATION("FailedAuthentication", "The security token could not be authenticated or authorized."),

    /** A signature that does not verify, or that fails to cover a required element. */
    FAILED_CHECK("FailedCheck", "The signature or decryption was invalid.");

    private final String localName;
    private final String faultString;

    WsSecurityFaultCode(String localName, String faultString) {
        this.localName = localName;
        this.faultString = faultString;
    }

    /**
     * The local name of the fault code, to be qualified with the {@code wsse} namespace.
     */
    String getLocalName() {
        return localName;
    }

    String getFaultString() {
        return faultString;
    }
}
