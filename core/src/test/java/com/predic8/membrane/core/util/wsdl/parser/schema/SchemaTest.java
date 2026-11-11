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

package com.predic8.membrane.core.util.wsdl.parser.schema;

import com.predic8.membrane.core.resolver.ResolverMap;
import com.predic8.membrane.core.util.wsdl.parser.WSDLParserException;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class SchemaTest {

    @Test
    void aStandaloneSchemaFollowsItsIncludesAndImports() throws Exception {
        final var byNamespace = Schema.schemasByNamespace(List.of(
                Schema.parse(new ResolverMap(), "classpath:/xml-schema/shop.xsd")));

        assertEquals(Set.of("https://example.com/shop", "https://example.com/common"), byNamespace.keySet());
        assertEquals(2, byNamespace.get("https://example.com/shop").size(), "shop.xsd and the included shop-types.xsd");
    }

    @Test
    void aSchemaWithoutTargetNamespaceIsKeyedByTheEmptyNamespace() throws Exception {
        final var byNamespace = Schema.schemasByNamespace(List.of(
                Schema.parse(new ResolverMap(), "classpath:/xml-schema/library.xsd")));

        assertEquals(Set.of(""), byNamespace.keySet());
    }

    @Test
    void aDocumentThatIsNoSchemaIsRejected() {
        final var e = assertThrows(WSDLParserException.class,
                () -> Schema.parse(new ResolverMap(), "classpath:/ws/hello-soap12.wsdl"));
        assertTrue(e.getMessage().contains("not an XML Schema"), e.getMessage());
    }
}
