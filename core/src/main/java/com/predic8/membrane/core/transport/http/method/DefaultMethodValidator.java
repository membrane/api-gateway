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

import com.predic8.membrane.annot.MCElement;

/**
 * @description Accepts methods starting with an ASCII letter and continuing with letters, digits, hyphens or
 * underscores, up to <code>maxLength</code> characters, e.g. <code>GET</code>, <code>PROPFIND</code> or
 * <code>X-CUSTOM_2</code>. Narrower than {@link RFC9110MethodValidator} (excludes most RFC 9110 punctuation
 * tchars) but wider than {@link UppercaseMethodValidator} (allows lowercase and digits). This is the built-in
 * default applied by {@link com.predic8.membrane.core.transport.Transport} when no validator component is
 * declared, so it rarely needs to be declared explicitly; declare it to change <code>maxLength</code> or
 * <code>allowTrace</code>. See examples/configuration for a runnable config.
 * @yaml <pre><code>
 * components:
 *   methodValidator:
 *     defaultMethodValidator:
 *       maxLength: 16
 * </code></pre>
 */
@MCElement(name = "defaultMethodValidator")
public class DefaultMethodValidator extends AbstractMethodValidator {

    @Override
    protected boolean matches(String method) {
        char first = method.charAt(0);
        if (!isAsciiLetter(first))
            return false;

        for (int i = 1; i < method.length(); i++) {
            char c = method.charAt(i);
            if (!isAsciiLetter(c) && !isDigit(c) && c != '-' && c != '_')
                return false;
        }

        return true;
    }

    private static boolean isAsciiLetter(char c) {
        return (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z');
    }

    private static boolean isDigit(char c) {
        return c >= '0' && c <= '9';
    }
}
