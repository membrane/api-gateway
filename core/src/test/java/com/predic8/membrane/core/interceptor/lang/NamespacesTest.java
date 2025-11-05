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

package com.predic8.membrane.core.interceptor.lang;

import com.predic8.membrane.core.config.xml.*;
import com.predic8.membrane.core.util.*;
import org.junit.jupiter.api.*;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class NamespacesTest {

    Namespaces namespaces = new Namespaces();

    @BeforeEach
    void setup() {
        Namespaces.Namespace ns = new Namespaces.Namespace();
        ns.prefix = "p8";
        ns.uri = "https://predic8.de";
        namespaces.setNamespace(List.of(ns));
    }

    @Test
    void namespaceContext() {
        assertEquals("https://predic8.de", namespaces.getNamespaceContext().getNamespaceURI("p8"));
        assertEquals("p8", namespaces.getNamespaceContext().getPrefix("https://predic8.de"));
        assertEquals("p8", CollectionsUtil.toList(namespaces.getNamespaceContext().getPrefixes("https://predic8.de")).getFirst());
    }
}