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

package com.predic8.membrane.core.transport.http.method;

import com.predic8.membrane.annot.MCAttribute;

/**
 * Base for {@link MethodValidator} implementations: rejects {@code null}, empty and overlong methods, then
 * delegates the grammar check to {@link #matches(String)}.
 */
public abstract class AbstractMethodValidator implements MethodValidator {

    protected int maxLength = 20;
    protected boolean allowTrace = false;

    @Override
    public boolean isValid(String method) {
        if (method == null)
            return false;
        int length = method.length();
        if (length == 0 || length > maxLength)
            return false;
        if (!allowTrace && method.equalsIgnoreCase("TRACE"))
            return false;
        return matches(method);
    }

    /**
     * Checks the method against the concrete policy's grammar. Called only for non-empty methods within the
     * length limit.
     */
    protected abstract boolean matches(String method);

    public int getMaxLength() {
        return maxLength;
    }

    /**
     * @description Maximum length of an accepted method. Methods longer than this are rejected.
     * @default 20
     * @example 16
     */
    @MCAttribute
    public void setMaxLength(int maxLength) {
        this.maxLength = maxLength;
    }

    public boolean isAllowTrace() {
        return allowTrace;
    }

    /**
     * @description Whether to allow the TRACE HTTP method. TRACE echoes the request back in the response body
     * and is a classic vector for cross-site tracing attacks, so it is rejected unless explicitly allowed.
     * @default false
     * @example true
     */
    @MCAttribute
    public void setAllowTrace(boolean allowTrace) {
        this.allowTrace = allowTrace;
    }
}
