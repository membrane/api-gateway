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
package com.predic8.membrane.core.util.xml;

import org.junit.jupiter.api.Test;
import org.w3c.dom.Node;

import javax.xml.namespace.QName;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.Transformer;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.StringWriter;
import java.util.concurrent.CompletableFuture;

import static javax.xml.transform.OutputKeys.OMIT_XML_DECLARATION;
import static org.junit.jupiter.api.Assertions.*;

class XMLUtilTest {

    private static final String TRANSFORMER_FACTORY_PROPERTY = "javax.xml.transform.TransformerFactory";

    static final QName JA = new QName("a","a");
    static final groovy.namespace.QName GA = new groovy.namespace.QName("a","a");

    static final QName PREFIX_A = new QName("ns","local","a");
    static final QName PREFIX_B = new QName("ns","local","b");

    @Test
    void groovyToJavaxQName() {
        assertEquals(JA, XMLUtil.groovyToJavaxQName(GA));
    }

    @Test
    void equalsIgnorePrefix() {
        assertEquals(PREFIX_A, PREFIX_B);
    }

    @Test
    void newSerializerWritesDom() throws Exception {
        final var doc = DocumentBuilderFactory.newDefaultInstance().newDocumentBuilder().newDocument();
        doc.appendChild(doc.createElement("a")).setTextContent("b");

        assertEquals("<a>b</a>", serialize(XMLUtil.newSerializer(), doc));
    }

    /**
     * The serializer must not depend on which TransformerFactory the classpath or the system
     * property selects, e.g. Saxon instead of the JDK's own.
     */
    @Test
    void newSerializerIgnoresTransformerFactorySelection() throws Exception {
        final var previous = System.getProperty(TRANSFORMER_FACTORY_PROPERTY);
        System.setProperty(TRANSFORMER_FACTORY_PROPERTY, "does.not.Exist");
        try {
            // A fresh thread, so the thread-local factory is created while the property is set.
            final var serializer = CompletableFuture.supplyAsync(() -> {
                try {
                    return XMLUtil.newSerializer();
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }, runnable -> new Thread(runnable).start()).get();
            assertTrue(serializer.getClass().getName().startsWith("com.sun.org.apache.xalan.internal."),
                    serializer.getClass().getName());
        } finally {
            if (previous == null)
                System.clearProperty(TRANSFORMER_FACTORY_PROPERTY);
            else
                System.setProperty(TRANSFORMER_FACTORY_PROPERTY, previous);
        }
    }

    private static String serialize(Transformer transformer, Node node) throws Exception {
        transformer.setOutputProperty(OMIT_XML_DECLARATION, "yes");
        final var writer = new StringWriter();
        transformer.transform(new DOMSource(node), new StreamResult(writer));
        return writer.toString();
    }
}
