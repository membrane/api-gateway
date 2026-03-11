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

package com.predic8.membrane.core.ws;

import com.predic8.membrane.core.interceptor.schemavalidation.*;
import com.predic8.membrane.core.resolver.*;
import com.predic8.membrane.core.util.wsdl.parser.*;
import com.predic8.membrane.core.util.wsdl.parser.schema.*;
import org.jetbrains.annotations.*;
import org.junit.jupiter.api.*;
import org.slf4j.*;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

public class WSDLIncludeImportTest {

    private static final Logger log = LoggerFactory.getLogger(WSDLIncludeImportTest.class);

    @Test
    void test() throws Exception {
        var defs = getDefinitions("classpath:/ws/include/include.wsdl");
        assertEquals(1, defs.getServices().size());
        assertEquals("http://example.com/test", defs.getTargetNamespace());
        assertEquals(1, defs.getSchemas().size());
        var embedded = defs.getSchemas().getFirst();
        assertEquals("http://example.com/test", embedded.getTargetNamespace());
        assertEquals(3, embedded.getSchemaElements().size());
        assertEquals(List.of("test", "testResponse", "du"), getElementNames(embedded.getSchemaElements()));
        assertEquals(1, embedded.getImports().size());
        var imported = embedded.getImports().getFirst();
        assertEquals("http://example.com/test/data-types", imported.getNamespace());
        assertEquals(List.of("B5", "B6"), getElementNames(imported.getSchema().getSchemaElements()));
    }

    @Test
    void multiple() throws Exception {
        assertEquals(List.of("test", "testResponse", "a", "b", "c"),
                getElementNames(getDefinitions("classpath:/ws/include/multiple.wsdl")
                        .getSchemas().getFirst().getSchemaElements()));
    }

    @Test
    void cyclicInclude() throws Exception {
        var defs = getDefinitions("classpath:/ws/include/cyclic.wsdl");
        assertEquals(1, defs.getSchemas().size());
        var embedded = defs.getSchemas().getFirst();
        assertEquals(1, embedded.getIncludes().size());
        assertEquals(List.of("test", "testResponse", "a", "b"), getElementNames(embedded.getSchemaElements()));
    }

    @Test
    void cyclicImport() throws Exception {
        var defs = getDefinitions("classpath:/ws/import/cyclic.wsdl");
        assertEquals(1, defs.getSchemas().size());
        var embedded = defs.getSchemas().getFirst();
        var a = embedded.getImports().getFirst();
        assertEquals(List.of("a"), getElementNames(a.getSchema().getSchemaElements()));
        var b = a.getSchema().getImports().getFirst();
        assertEquals(List.of("b"), getElementNames(b.getSchema().getSchemaElements()));
    }


    @Test
    void messageReferencesImport() throws Exception {
        var defs = getDefinitions("classpath:/ws/import/message-references-import.wsdl");
        assertEquals(1, defs.getSchemas().size());
    }

    @Test
    void embeddedImports() throws Exception {
        var defs = getDefinitions("classpath:/ws/import/embedded.wsdl");
        assertEquals(3, defs.getSchemas().size());
        var first = defs.getSchemas().getFirst();
        assertEquals(List.of("getCustomerRequest", "getCustomerResponse"), getElementNames(first.getSchemaElements()));
        assertEquals(2, first.getImports().size());
        var firstImport = first.getImports().getFirst();
        assertEquals(List.of("from2"), getElementNames(firstImport.getSchema().getSchemaElements()));
        var secondImport = first.getImports().get(1);
        assertEquals(List.of("from3"), getElementNames(secondImport.getSchema().getSchemaElements()));
        var second = defs.getSchemas().get(1);
        assertEquals(List.of("from2"), getElementNames(second.getSchemaElements()));
        var third = defs.getSchemas().get(2);
        assertEquals(List.of("from3"), getElementNames(third.getSchemaElements()));
    }

    @Test
    void validator() {
        var validator = new WSDLValidator(new ResolverMap(),
                WSDLIncludeImportTest.class.getResource("/ws/include/include.wsdl").toString(),
                "TestService",
                (msg, exc) -> log.info("Validation failure: {}", msg), true);
        validator.init();
    }

    private static @NotNull List<String> getElementNames(List<SchemaElement> schemaElements) {
        return schemaElements.stream().map(WSDLElement::getName).toList();
    }

    private static @NotNull Definitions getDefinitions(String location) throws Exception {
        return Definitions.parse(new ResolverMap(), location);
    }
}
