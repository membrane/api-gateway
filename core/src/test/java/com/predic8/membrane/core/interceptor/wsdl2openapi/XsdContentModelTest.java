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

package com.predic8.membrane.core.interceptor.wsdl2openapi;

import org.junit.jupiter.api.Test;
import org.w3c.dom.Element;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.StringReader;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import static com.predic8.membrane.core.interceptor.wsdl2openapi.XsdContentModel.*;
import static com.predic8.membrane.core.interceptor.wsdl2openapi.XsdDomUtil.findXsdChildWithName;
import static com.predic8.membrane.core.interceptor.wsdl2openapi.XsdDomUtil.firstParticle;
import static org.junit.jupiter.api.Assertions.*;

class XsdContentModelTest {

    private static final String NS = "urn:example:person";

    private static final String SCHEMA = """
            <xsd:schema xmlns:xsd="http://www.w3.org/2001/XMLSchema" xmlns:tns="urn:example:person"
                        targetNamespace="urn:example:person">
              <xsd:group name="Names">
                <xsd:sequence>
                  <xsd:element name="first" type="xsd:string"/>
                  <xsd:element name="last" type="xsd:string"/>
                </xsd:sequence>
              </xsd:group>
              <xsd:group name="Loop">
                <xsd:sequence>
                  <xsd:group ref="tns:Loop"/>
                  <xsd:element name="deep" type="xsd:string"/>
                </xsd:sequence>
              </xsd:group>
              <xsd:complexType name="Person">
                <xsd:sequence>
                  <xsd:element name="id" type="xsd:string"/>
                  <xsd:group ref="tns:Names"/>
                  <xsd:sequence><xsd:element name="nested" type="xsd:string"/></xsd:sequence>
                  <xsd:choice>
                    <xsd:element name="a" type="xsd:string"/>
                    <xsd:element name="b" type="xsd:string"/>
                  </xsd:choice>
                  <xsd:any/>
                </xsd:sequence>
              </xsd:complexType>
              <xsd:complexType name="Recursive">
                <xsd:sequence><xsd:group ref="tns:Loop"/></xsd:sequence>
              </xsd:complexType>
              <xsd:complexType name="Dangling">
                <xsd:sequence><xsd:group ref="tns:NoSuchGroup"/></xsd:sequence>
              </xsd:complexType>
              <xsd:complexType name="ChoiceOnly">
                <xsd:choice>
                  <xsd:element name="x" type="xsd:string"/>
                  <xsd:sequence><xsd:element name="y" type="xsd:string"/></xsd:sequence>
                </xsd:choice>
              </xsd:complexType>
            </xsd:schema>
            """;

    private static final Element SCHEMA_ROOT = parse();

    private static Element parse() {
        try {
            var factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            return factory.newDocumentBuilder()
                    .parse(new InputSource(new StringReader(SCHEMA))).getDocumentElement();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /** The content model of the named complexType. */
    private static ContentNode nodeOf(String complexTypeName) {
        var model = new XsdContentModel(Map.of(NS, List.of(SCHEMA_ROOT)));
        Element complexType = findXsdChildWithName(SCHEMA_ROOT, "complexType", complexTypeName);
        return model.nodeOf(firstParticle(complexType), SCHEMA_ROOT, new HashSet<>());
    }

    private static List<String> fieldNames(String complexTypeName) {
        return fieldsOf(nodeOf(complexTypeName)).stream()
                .map(field -> field.declaration().getAttribute("name"))
                .toList();
    }

    @Test
    void groupsAreExpandedAndNestedParticlesFlattenedInDeclarationOrder() {
        // The group's two fields sit where the reference stood, the nested sequence contributes its
        // field inline, both choice alternatives appear, and xsd:any declares no field at all.
        assertEquals(List.of("id", "first", "last", "nested", "a", "b"), fieldNames("Person"));
    }

    @Test
    void aChoiceSurvivesAsItsOwnNode() {
        var children = ((Container) nodeOf("Person")).children();

        var choices = children.stream()
                .filter(child -> child instanceof Container container && container.isChoice())
                .toList();
        // The one particle whose boundary carries meaning stays visible; the sequences around it do
        // not have to, which is why the group and the nested sequence are indistinguishable above.
        assertEquals(1, choices.size());
        assertEquals(2, ((Container) choices.getFirst()).children().size());
    }

    @Test
    void aTopLevelChoiceIsTheNodeItself() {
        var node = nodeOf("ChoiceOnly");

        assertInstanceOf(Container.class, node);
        assertTrue(((Container) node).isChoice());
        // A direct element alternative and a sequence alternative are different kinds of node: the
        // schema builder requires exactly one of them, not one of their fields.
        assertInstanceOf(Field.class, ((Container) node).children().getFirst());
        assertInstanceOf(Container.class, ((Container) node).children().get(1));
    }

    @Test
    void aGroupReferencingItselfIsExpandedOnceAndThenCut() {
        // Terminates, and the field declared after the recursive reference is still collected.
        assertEquals(List.of("deep"), fieldNames("Recursive"));
    }

    @Test
    void aGroupReferenceNamingNoReachableGroupContributesNothing() {
        assertEquals(List.of(), fieldNames("Dangling"));
    }
}
