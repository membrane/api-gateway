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
package com.predic8.membrane.core.interceptor.soap;

import io.swagger.v3.oas.models.media.ArraySchema;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.apache.commons.io.IOUtils.toInputStream;
import static org.junit.jupiter.api.Assertions.*;

class Xsd2OasSchemaTest {

    @Test
    void testConvertSimpleElement() throws Exception {
        String xsd = """
                <?xml version="1.0" encoding="UTF-8"?>
                <xs:element name="testElement" type="xs:string" xmlns:xs="http://www.w3.org/2001/XMLSchema"/>
                """;
        Schema<?> schema = new Xsd2OasSchema().convert(toInputStream(xsd, StandardCharsets.UTF_8));
        assertInstanceOf(StringSchema.class, schema);
        assertEquals("testElement", schema.getXml().getName());
    }

    @Test
    void testConvertComplexElementWithSequenceAndArray() throws Exception {
        String xsd = """
                <?xml version="1.0" encoding="UTF-8"?>
                <xs:element name="order" xmlns:xs="http://www.w3.org/2001/XMLSchema">
                  <xs:complexType>
                    <xs:sequence>
                      <xs:element name="item" type="xs:string" maxOccurs="unbounded"/>
                    </xs:sequence>
                  </xs:complexType>
                </xs:element>
                """;
        Schema<?> schema = new Xsd2OasSchema().convert(toInputStream(xsd, StandardCharsets.UTF_8));
        assertInstanceOf(ObjectSchema.class, schema);

        Map<String, Schema> properties = schema.getProperties();
        assertTrue(properties.containsKey("item"));
        Schema<?> itemSchema = properties.get("item");
        assertInstanceOf(ArraySchema.class, itemSchema);

        ArraySchema arraySchema = (ArraySchema) itemSchema;
        assertInstanceOf(StringSchema.class, arraySchema.getItems());
        assertEquals("item", arraySchema.getXml().getName());
        assertTrue(arraySchema.getXml().getWrapped());
    }

    @Test
    void testConvertNoElement() throws Exception {
        String xsd = """
                <?xml version="1.0" encoding="UTF-8"?>
                <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
                </xs:schema>
                """;
        Schema<?> schema = new Xsd2OasSchema().convert(toInputStream(xsd, StandardCharsets.UTF_8));
        assertInstanceOf(ObjectSchema.class, schema);
    }

    @ParameterizedTest
    @ValueSource(strings = { "xs:date", "xs:dateTime", "xs:time" })
    void testCreateSchemaFromTypeDateFormats(String type) throws Exception {
        String elementName = "elementTest";
        String xsd = """
                <?xml version="1.0" encoding="UTF-8"?>
                <xs:element name="%s" type="%s" xmlns:xs="http://www.w3.org/2001/XMLSchema"/>
                """.formatted(elementName, type);
        Schema<?> schema = new Xsd2OasSchema().convert(toInputStream(xsd, StandardCharsets.UTF_8));
        assertInstanceOf(StringSchema.class, schema);
        String expectedFormat = type.split(":")[1];
        assertEquals(expectedFormat, schema.getFormat());
    }
}