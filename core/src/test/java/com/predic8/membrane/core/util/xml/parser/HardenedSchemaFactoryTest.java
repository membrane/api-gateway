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
package com.predic8.membrane.core.util.xml.parser;

import org.junit.jupiter.api.Test;

import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.SchemaFactory;
import javax.xml.validation.Validator;
import java.io.StringReader;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.predic8.membrane.core.util.RecordingServerTestUtil.freePort;
import static com.predic8.membrane.core.util.RecordingServerTestUtil.startRecordingServer;
import static javax.xml.XMLConstants.W3C_XML_SCHEMA_NS_URI;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class HardenedSchemaFactoryTest {

    private static final String SIMPLE_XSD = """
            <xs:schema xmlns:xs='http://www.w3.org/2001/XMLSchema'>
              <xs:element name='r'/>
            </xs:schema>
            """;

    @Test
    void validatesConformingDocument() throws Exception {
        SchemaFactory sf = HardenedSchemaFactory.newInstance(W3C_XML_SCHEMA_NS_URI);
        Validator v = sf.newSchema(new StreamSource(new StringReader(SIMPLE_XSD))).newValidator();
        HardenedSchemaFactory.hardenValidator(v);
        v.validate(new StreamSource(new StringReader("<r/>")));
    }

    @Test
    void rejectsNonConformingDocument() throws Exception {
        SchemaFactory sf = HardenedSchemaFactory.newInstance(W3C_XML_SCHEMA_NS_URI);
        Validator v = sf.newSchema(new StreamSource(new StringReader(SIMPLE_XSD))).newValidator();
        HardenedSchemaFactory.hardenValidator(v);
        assertThrows(Exception.class, () ->
            v.validate(new StreamSource(new StringReader("<notAllowed/>")))
        );
    }

    @Test
    void schemaFactoryDoesNotFetchExternalDtd() throws Exception {
        var received = new AtomicBoolean(false);
        int port = freePort();
        var router = startRecordingServer(port, received);
        try {
            SchemaFactory sf = HardenedSchemaFactory.newInstance(W3C_XML_SCHEMA_NS_URI);
            String maliciousXsd = """
                    <?xml version='1.0'?>
                    <!DOCTYPE xs:schema SYSTEM 'http://127.0.0.1:%d/x.dtd'>
                    <xs:schema xmlns:xs='http://www.w3.org/2001/XMLSchema'>
                      <xs:element name='r'/>
                    </xs:schema>
                    """.formatted(port);
            try {
                sf.newSchema(new StreamSource(new StringReader(maliciousXsd)));
            } catch (Exception ignored) {
                // rejection is fine; a network fetch is not
            }
        } finally {
            router.stop();
        }
        assertFalse(received.get(), "SchemaFactory must not fetch external DTD");
    }

    @Test
    void validatorDoesNotFetchExternalDtdInInstanceDocument() throws Exception {
        SchemaFactory sf = HardenedSchemaFactory.newInstance(W3C_XML_SCHEMA_NS_URI);
        Validator validator = sf.newSchema(new StreamSource(new StringReader(SIMPLE_XSD))).newValidator();
        HardenedSchemaFactory.hardenValidator(validator);

        var received = new AtomicBoolean(false);
        int port = freePort();
        var router = startRecordingServer(port, received);
        try {
            String malicious = "<?xml version='1.0'?><!DOCTYPE r SYSTEM 'http://127.0.0.1:%d/x.dtd'><r/>".formatted(port);
            try {
                validator.validate(new StreamSource(new StringReader(malicious)));
            } catch (Exception ignored) {
                // rejection is fine; a network fetch is not
            }
        } finally {
            router.stop();
        }
        assertFalse(received.get(), "Validator must not fetch external DTD from instance document");
    }
}
