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
 * @description Accepts any method matching the RFC 9110 token grammar (the spec's definition of a valid method),
 * up to <code>maxLength</code> characters. The most permissive of the built-in policies: it also accepts
 * lowercase methods and punctuation tchars such as <code>PROPFIND</code> or <code>my.method</code>. Declare it
 * explicitly to widen validation beyond the {@link DefaultMethodValidator built-in default}. See
 * examples/configuration for a runnable config.
 * @yaml <pre><code>
 * components:
 *   methodValidator:
 *     rfc9110MethodValidator:
 *       maxLength: 32
 * </code></pre>
 */
@MCElement(name = "rfc9110MethodValidator")
public class RFC9110MethodValidator extends AbstractMethodValidator {

    /**
     * RFC 9110 §5.6.2 token: {@code 1*tchar}.
     */
    private static final Pattern TOKEN = Pattern.compile("[-!#$%&'*+.^_`|~0-9A-Za-z]+");

    @Override
    protected boolean matches(String method) {
        return TOKEN.matcher(method).matches();
    }
}
