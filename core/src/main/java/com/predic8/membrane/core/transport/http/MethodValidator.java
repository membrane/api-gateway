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

/**
 * Decides which HTTP request methods (verbs) Membrane accepts on the request line. A request whose method is
 * rejected is answered with {@code 501 Not Implemented} and never reaches the API flow.
 * <p>
 * Declare one of the implementations ({@link KnownMethodValidator}, {@link RFC9110MethodValidator},
 * {@link UppercaseMethodValidator}) as a top-level component to override the policy for every transport. When
 * none is declared, the {@link #permissive() permissive default} applies.
 */
public interface MethodValidator {

    boolean isValid(String method);

    /**
     * The policy used when no validator component is declared: any RFC 9110 token up to 20 characters.
     */
    static MethodValidator permissive() {
        return new RFC9110MethodValidator();
    }
}
