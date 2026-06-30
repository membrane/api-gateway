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

import com.predic8.membrane.annot.MCElement;
import com.predic8.membrane.core.http.Request;

import java.util.Set;

/**
 * @description Accepts only the request methods Membrane knows: GET, POST, PUT, PATCH, DELETE, HEAD, OPTIONS,
 * CONNECT, TRACE. Every other method is answered with <code>501 Not Implemented</code>.
 */
@MCElement(name = "knownMethodValidator")
public class KnownMethodValidator extends AbstractMethodValidator {

    /**
     * The set of methods named by the {@code METHOD_*} constants in {@link Request}.
     */
    private static final Set<String> KNOWN_METHODS = Set.of(
            Request.METHOD_GET, Request.METHOD_POST, Request.METHOD_PUT, Request.METHOD_PATCH,
            Request.METHOD_DELETE, Request.METHOD_HEAD, Request.METHOD_OPTIONS, Request.METHOD_CONNECT,
            Request.METHOD_TRACE);

    @Override
    protected boolean matches(String method) {
        return KNOWN_METHODS.contains(method);
    }
}
