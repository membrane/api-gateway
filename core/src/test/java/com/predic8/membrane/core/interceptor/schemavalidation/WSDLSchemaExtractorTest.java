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

package com.predic8.membrane.core.interceptor.schemavalidation;

import com.predic8.membrane.annot.*;
import org.junit.jupiter.api.*;
import org.w3c.dom.*;
import org.xml.sax.*;

import javax.xml.parsers.*;
import javax.xml.transform.dom.*;
import java.io.*;

import static com.predic8.membrane.core.Constants.WSDL_SOAP11_NS;
import static com.predic8.membrane.core.Constants.XSD_NS;
import static com.predic8.membrane.core.interceptor.schemavalidation.WSDLSchemaExtractor.*;
import static org.junit.jupiter.api.Assertions.*;

class WSDLSchemaExtractorTest {

    @Test
    void extractWithCorrectNamespaceFromWSDLDefinitionElement() throws Exception {

        var schemas = getSchemas(parse( this.getClass().getResourceAsStream("/ws/cities.wsdl"))
                .getDocumentElement());
        assertEquals(1, schemas.size());

        var schema = schemas.getFirst().getDocumentElement();
        assertEquals(XSD_NS , schema.getNamespaceURI());
        assertEquals("schema", schema.getLocalName());

        assertEquals(XSD_NS, schema.getAttribute("xmlns:xsd"));
        assertEquals(WSDL_SOAP11_NS, schema.getAttribute("xmlns:s"));
    }

    private static Document parse(InputStream wsdl) throws Exception {
        var fac = DocumentBuilderFactory.newInstance();
        fac.setNamespaceAware(true);
        return fac.newDocumentBuilder().parse(wsdl);
    }
}