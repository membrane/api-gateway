/* Copyright 2021 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */

package com.predic8.membrane.core.interceptor.xml;

import com.predic8.membrane.annot.MCAttribute;
import com.predic8.membrane.annot.MCChildElement;
import com.predic8.membrane.annot.MCElement;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.Message;
import com.predic8.membrane.core.interceptor.AbstractInterceptor;
import com.predic8.membrane.core.interceptor.Outcome;
import org.springframework.beans.factory.annotation.Required;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * @description Based on xpath it takes values from xml in request and puts them in exchange as properties
 * @topic 4. Interceptors/Features
 */


@MCElement(name="xpathExtractor")
public class XmlPathExtractorInterceptor extends AbstractInterceptor{


    private List<Property> properties = new ArrayList<>();

    public XmlPathExtractorInterceptor() {
        name = "XmlPath";
    }
    /**
     * @description Defines a xpath and name for exchange property.
     */
    @Required
    @MCChildElement
    public void setMappings(List<Property> properties) {
        this.properties = properties;
    }

    public List<Property> getMappings() {
        return properties;
    }


    @Override
    public Outcome handleRequest(Exchange exc) throws ParserConfigurationException, SAXException, XPathExpressionException, IOException {
        return handleInternal(exc, exc.getRequest());
    }

    @Override
    public Outcome handleResponse(Exchange exc) throws Exception {
        return handleInternal(exc, exc.getResponse());
    }

    private Outcome handleInternal(Exchange exc, Message msg) throws ParserConfigurationException, SAXException, XPathExpressionException, IOException {
        if(!msg.isXML()) {
            return Outcome.CONTINUE;
        }
        setProperties(exc, msg.getBodyAsStream());
        return Outcome.CONTINUE;
    }

    private void setProperties(Exchange exc, InputStream body) throws XPathExpressionException, ParserConfigurationException, SAXException, IOException {
        for (Property m : properties) {
            NodeList list = getEvaluate(getDocument(body), m);
            if(list.getLength() > 1){
                exc.setProperty(m.getName(), getPropertyAsList(list));
            }
            else {
                exc.setProperty(m.getName(), list.item(0).getTextContent());
            }
        }
    }

    private List<String> getPropertyAsList(NodeList list) {
        return IntStream.range(0, list.getLength())
                .mapToObj(i -> list.item(i).getTextContent()).collect(Collectors.toList());
    }

    private Document getDocument(InputStream body ) throws ParserConfigurationException, SAXException, IOException {
        return DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(body);
    }

    private NodeList getEvaluate(Document document, Property m) throws XPathExpressionException {
        return (NodeList) m.xpath.evaluate(document, XPathConstants.NODESET);
    }

    @MCElement(name="property", topLevel=false, id="xpath-map")
    public static class Property {
        String name;
        XPathExpression xpath;
        static XPathFactory xPathFactory = XPathFactory.newInstance();

        public Property() {
        }

        public Property(String xpath, String name) throws XPathExpressionException {
            this.name = name;
            this.setXpath(xpath);
        }

        public XPathExpression getXpath() {
            return xpath;
        }

        /**
         * @description Xpath expression
         * @example bar/foo
         */
        @Required
        @MCAttribute
        public void setXpath(String xpath) {
            try {
                this.xpath = xPathFactory.newXPath().compile(xpath);
            } catch (XPathExpressionException e) {
                throw new RuntimeException(String.format("Wrong xpath expression %s with property %s", xpath, name),e);
            }
        }

        public String getName() {
            return name;
        }

        /**
         * @description Name of property to put in exchange properties
         * @example amount
         */
        @Required
        @MCAttribute
        public void setName(String name) {
            this.name = name;
        }

    }








}
