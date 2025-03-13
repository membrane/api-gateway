/* Copyright 2025 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */
package com.predic8.membrane.core.transport.ssl.acme;

import org.junit.jupiter.api.Test;

import static com.predic8.membrane.core.transport.ssl.AcmeSSLContext.computeHostList;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class AcmeMiscTest {

    private final String[] FOO = new String[]{ "foo.com" };
    private final String[] AST = new String[]{ "*.com" };
    private final String[] AST2 = new String[]{ "*.de", "*.com" };

    @Test
    public void hostList() {
        assertArrayEquals(FOO, computeHostList(FOO, null));
        assertArrayEquals(FOO, computeHostList(FOO, "foo.com"));
        assertArrayEquals(AST, computeHostList(FOO, "*.com"));
        assertArrayEquals(AST2, computeHostList(FOO, "*.de *.com"));
        assertThrows(RuntimeException.class, () -> computeHostList(FOO, "*.org"));
    }
}
