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
package com.predic8.membrane.core.interceptor.soap.wsse;

import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import javax.xml.parsers.DocumentBuilderFactory;
import java.util.ArrayList;
import java.util.List;

import static com.predic8.membrane.core.interceptor.soap.wsse.WsSecurityXmlUtil.forEachDescendantElement;
import static org.junit.jupiter.api.Assertions.assertEquals;

class WsSecurityXmlUtilTest extends AbstractWsSecurityTest {

    /**
     * Deep enough to overflow the default thread stack with a recursive traversal (which needs a
     * frame per level), while still building and walking in well under a second. Not an arbitrary
     * number: lower it far and the test stops proving anything.
     */
    private static final int OVERFLOWING_DEPTH = 50_000;

    private static Document newDocument() throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        return factory.newDocumentBuilder().newDocument();
    }

    /**
     * Built programmatically rather than parsed, and never serialized: both of those paths recurse
     * on their own, so either one would mask what is being tested here.
     */
    private static Document nested(int depth) throws Exception {
        Document doc = newDocument();
        Element current = doc.createElementNS(null, "level");
        doc.appendChild(current);
        for (int i = 1; i < depth; i++) {
            Element child = doc.createElementNS(null, "level");
            current.appendChild(child);
            current = child;
        }
        return doc;
    }

    @Test
    void deeplyNestedDocumentIsTraversedWithoutOverflowingTheStack() throws Exception {
        Document doc = nested(OVERFLOWING_DEPTH);

        int[] visited = {0};
        forEachDescendantElement(doc.getDocumentElement(), element -> visited[0]++);

        assertEquals(OVERFLOWING_DEPTH, visited[0]);
    }

    /**
     * Pre-order document order, which the recursive version produced and
     * {@code SignatureValidatePart} relies on when it reports the first of several matches.
     */
    @Test
    void visitsElementsInDocumentOrder() throws Exception {
        Document doc = newDocument();
        Element root = doc.createElementNS(null, "root");
        doc.appendChild(root);
        Element first = doc.createElementNS(null, "first");
        first.appendChild(doc.createElementNS(null, "firstChild"));
        root.appendChild(first);
        root.appendChild(doc.createElementNS(null, "second"));
        // Text nodes must not be visited, only elements.
        root.appendChild(doc.createTextNode("ignored"));
        root.appendChild(doc.createElementNS(null, "third"));

        List<String> visited = new ArrayList<>();
        forEachDescendantElement(root, element -> visited.add(element.getLocalName()));

        assertEquals(List.of("root", "first", "firstChild", "second", "third"), visited);
    }
}
