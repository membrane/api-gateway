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
package com.predic8.membrane.core.util.xml;

import org.junit.jupiter.api.*;
import org.w3c.dom.*;

import javax.xml.parsers.*;

import java.io.ByteArrayInputStream;

import static org.junit.jupiter.api.Assertions.*;

class NodeListWrapperTest {

    private DocumentBuilder builder;

    @BeforeEach
    void setUp() throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        builder = factory.newDocumentBuilder();
    }

    @Test
    void nodeListWrapperToString() throws Exception {
        assertEquals("Text 1 Text 2 Text 3", new NodeListWrapper(createNodeListFromXML("""
            <root>
                <child>Text 1</child>
                <child>Text 2</child>
                <child>
                    <subchild>Text 3</subchild>
                </child>
            </root>
            """)).toString());
    }

    @Test
    void testEmptyNodeList() throws Exception {
        assertEquals("", new NodeListWrapper(createNodeListFromXML("<root></root>")).toString());
    }

    @Test
    void testNodeListWithMixedNodes() throws Exception {
        assertEquals("Text 1 Extra Text 2 Between Text 3", new NodeListWrapper(createNodeListFromXML("""
            <root>
                <child>Text 1</child>
                Extra
                <child>Text 2</child>
                Between
                <!-- This is a comment -->
                <child>
                    <subchild>Text 3</subchild>
                </child>
            </root>
            """)).toString());
    }

    private NodeList createNodeListFromXML(String xml) throws Exception {
        return builder.parse(new ByteArrayInputStream(xml.getBytes())).getDocumentElement().getChildNodes();
    }
}
