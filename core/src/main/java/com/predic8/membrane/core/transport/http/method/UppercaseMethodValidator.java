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

import java.util.regex.Pattern;

/**
 * @description Accepts only methods consisting of uppercase letters A-Z, up to <code>maxLength</code> characters.
 * The strictest policy: <code>GET</code> and <code>POST</code> pass, but <code>PROPFIND2</code> (digit) and
 * <code>get</code> (lowercase) are answered with <code>501 Not Implemented</code>. See examples/configuration
 * for a runnable config.
 * @yaml <pre><code>
 * components:
 *   methodValidator:
 *     uppercaseMethodValidator:
 *       maxLength: 10
 * </code></pre>
 */
@MCElement(name = "uppercaseMethodValidator")
public class UppercaseMethodValidator extends AbstractMethodValidator {

    private static final Pattern UPPERCASE = Pattern.compile("[A-Z]+");

    @Override
    protected boolean matches(String method) {
        return UPPERCASE.matcher(method).matches();
    }
}
