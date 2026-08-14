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

package com.predic8.membrane.core.util.wsdl.parser;

import com.predic8.membrane.core.resolver.ResolverMap;
import com.predic8.membrane.core.util.xml.parser.XmlParseException;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DefinitionsTest {

    @Test
    void parseWsdlWithExternalImports() throws Exception {
        var defs = Definitions.parse(new ResolverMap(), "classpath:/validation/ArticleService.wsdl");

        assertEquals(1, defs.getPortTypes().size());
        assertEquals(3, defs.getPortTypes().getFirst().getOperations().size());
    }

    @Test
    void operationsAreFlattenedAcrossPortTypes() throws Exception {
        var defs = Definitions.parse(new ResolverMap(), "classpath:/validation/ArticleService.wsdl");

        assertEquals(List.of("create", "get", "getAll"), defs.getOperations().stream().map(Operation::getName).toList());
    }

    @Test
    void findOperationByName() throws Exception {
        var defs = Definitions.parse(new ResolverMap(), "classpath:/validation/ArticleService.wsdl");

        assertEquals("get", defs.findOperation("get").orElseThrow().getName());
        assertTrue(defs.findOperation("doesNotExist").isEmpty());
        assertTrue(defs.findOperation(null).isEmpty());
    }

    @Test
    void findBindingOperationByName() throws Exception {
        var defs = Definitions.parse(new ResolverMap(), "classpath:/validation/ArticleService.wsdl");

        assertEquals("getAll", defs.findBindingOperation("getAll").orElseThrow().getName());
        assertTrue(defs.findBindingOperation("doesNotExist").isEmpty());
    }

    @Test
    void documentationIsReadPerElement() throws Exception {
        var defs = Definitions.parse(new ResolverMap(), "classpath:/ws/documented.wsdl");

        assertEquals("Documentation of the definitions.", defs.getDocumentation());
        assertEquals("Answers questions about cities.", defs.getServices().getFirst().getDocumentation());
        assertEquals("Looks a city up by its name.",
                defs.getPortTypes().getFirst().getOperations().getFirst().getDocumentation());
        assertNull(defs.getPortTypes().getFirst().getDocumentation(),
                "an undocumented element must not inherit the documentation of a descendant");
    }

    @Test
    void parseWsdlWithMissingImportThrows() {
        var e = assertThrows(WSDLParserException.class,
                () -> Definitions.parse(new ResolverMap(), "classpath:/ws/missing-import.wsdl"));

        assertTrue(e.getMessage().contains("does-not-exist.xsd"), e.getMessage());
        assertNotNull(e.getCause());
    }

    @Test
    void parseWsdlWithMalformedIncludedSchemaThrows() {
        var e = assertThrows(WSDLParserException.class,
                () -> Definitions.parse(new ResolverMap(), "classpath:/ws/malformed-include.wsdl"));

        // The schema was found; it is broken. Reporting it as unresolvable would send the user
        // looking for a wrong schemaLocation.
        assertTrue(e.getMessage().contains("parse schema \"malformed.xsd\""), e.getMessage());
        assertInstanceOf(XmlParseException.class, e.getCause());
    }
}
