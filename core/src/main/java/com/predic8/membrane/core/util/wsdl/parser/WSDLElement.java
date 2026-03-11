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

import org.jetbrains.annotations.*;
import org.w3c.dom.*;

import javax.xml.namespace.*;
import java.util.*;
import java.util.function.*;

import static com.predic8.membrane.annot.Constants.*;
import static org.w3c.dom.Node.*;

public class WSDLElement {

    protected WSDLParserContext ctx;
    protected final Element element;

    public WSDLElement(WSDLParserContext ctx, Node node) {
        this.ctx = ctx;
        if (!(node instanceof Element element)) {
            throw new RuntimeException("Not an element: " + node.getClass());
        }
        this.element = element;
    }

    public String getName() {
        return getAttribute("name");
    }

    public Element getDefinitions() {
        return element.getOwnerDocument().getDocumentElement();
    }

    public Element getElement() {
        return element;
    }

    protected <T extends WSDLElement> Optional<T> instantiateChild(String name, Class<T> clazz) {
        var children = instantiateChildren(name, clazz);
        return children.isEmpty() ? Optional.empty() : Optional.of(children.getFirst());
    }

    protected <T extends WSDLElement> List<T> instantiateChildren(String name, Class<T> clazz) {
        return instantiateElements(element,name,clazz);
    }

    protected <T extends WSDLElement> List<T> instantiateElements(Node node, String name, Class<T> clazz) {
        return instantiateElementsInternal(node, name, clazz, WSDLElement::isElementWithName);
    }

    protected <T extends WSDLElement> List<T> instantiateWSDLChildren(String name, Class<T> clazz) {
        return instantiateWSDLElements(element,name,clazz);
    }

    protected <T extends WSDLElement> List<T> instantiateWSDLElements(Node node, String name, Class<T> clazz) {
        return instantiateElementsInternal(node, name, clazz, WSDLElement::isWSDLElementWithName);
    }

     protected <T extends WSDLElement> List<T> instantiateXSDChildren(String name, Class<T> clazz) {
        return instantiateXSDElements(element,name,clazz);
     }

    protected <T extends WSDLElement> List<T> instantiateXSDElements(Node node, String name, Class<T> clazz) {
        return instantiateElementsInternal(node, name, clazz, WSDLElement::isXSDElementWithName);
    }

    protected <T extends WSDLElement> List<T> instantiateElementsInternal(Node node, String name, Class<T> clazz, BiPredicate<Node, String> predicate) {
        var result = new ArrayList<T>();
        var children = node.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            var child = children.item(i);
            if (predicate.test(child, name)) {
                try {
                    result.add(clazz.getConstructor(WSDLParserContext.class, Node.class).newInstance(ctx, child));
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
        }
        return result;
    }

    protected static boolean isElementWithName(Node node, String name) {
        return node.getNodeType() == ELEMENT_NODE && name.equals(node.getLocalName());
    }

    protected static boolean isWSDLElementWithName(Node node, String name) {
        return isWSDLElement(node) && name.equals(node.getLocalName());
    }

    protected static boolean isXSDElementWithName(Node node, String name) {
        return isXSDElement(node) && name.equals(node.getLocalName());
    }

    protected static boolean isWSDLElement(Node node) {
        return node.getNodeType() == ELEMENT_NODE && WSDL11_NS.equals(node.getNamespaceURI());
    }

    protected static boolean isXSDElement(Node node) {
        return node.getNodeType() == ELEMENT_NODE && XSD_NS.equals(node.getNamespaceURI());
    }

    protected boolean isWsdlSoap12Element() {
        return WSDL_SOAP12_NS.equals(element.getNamespaceURI());
    }

    protected boolean isWsdlSoap11Element() {
        return WSDL_SOAP11_NS.equals(element.getNamespaceURI());
    }

    protected @NotNull String getAttribute(String name) {
        return element.getAttribute(name);
    }

    public QName resolveQName(String value) {
        if (value == null)
            return null;
        return WSDLParserUtil.resolveQName(value, element);
    }
}
