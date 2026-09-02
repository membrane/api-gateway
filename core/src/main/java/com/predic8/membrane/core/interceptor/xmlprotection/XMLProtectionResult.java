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

package com.predic8.membrane.core.interceptor.xmlprotection;

/**
 * The outcome of scanning one XML document. A rejection carries the reason it was rejected for, so
 * that every violation - a limit that was exceeded, a DOCTYPE pointing outside the document, or XML
 * that is not well-formed - travels back to the caller on one channel.
 */
public sealed interface XMLProtectionResult {

    /** The document is within the configured limits and may be passed on. */
    XMLProtectionResult ACCEPTED = new Accepted();

    record Accepted() implements XMLProtectionResult {
    }

    /**
     * The document violates the policy and must not be passed on. The document written so far is
     * incomplete and has to be discarded.
     *
     * @param reason what was violated, for the log and for the error response outside production
     */
    record Rejected(String reason) implements XMLProtectionResult {
    }
}
