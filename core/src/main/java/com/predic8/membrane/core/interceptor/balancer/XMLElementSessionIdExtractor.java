/* Copyright 2009, 2012, 2025 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */
package com.predic8.membrane.core.interceptor.balancer;

import com.predic8.membrane.annot.MCAttribute;
import com.predic8.membrane.annot.MCElement;
import com.predic8.membrane.annot.Required;
import com.predic8.membrane.core.FixedStreamReader;
import com.predic8.membrane.core.config.AbstractXmlElement;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.Message;
import com.predic8.membrane.core.interceptor.Interceptor;
import com.predic8.membrane.core.interceptor.Interceptor.Flow;
import com.predic8.xml.beautifier.XMLInputFactoryFactory;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import javax.xml.stream.XMLStreamWriter;

/**
 * @description Extracts a session ID from an XML HTTP request body based on the qualified name of an XML element.
 */
@MCElement(name = "xmlSessionIdExtractor")
public class XMLElementSessionIdExtractor extends AbstractXmlElement implements SessionIdExtractor {

    private static final Logger log = LoggerFactory.getLogger(XMLElementSessionIdExtractor.class.getName());

    private String localName;
    private String namespace;

    @Override
    public String getSessionId(Exchange exc, Flow flow) throws Exception {
        if (!exc.getMessage(flow).isXML()) {
            log.debug("Did not search for an XML element in non-XML message.");
            return null;
        }

        log.debug("searching for sessionid");

        XMLStreamReader reader = getXmlStreamReader(exc.getMessage(flow));
        try {
            while (reader.hasNext()) {
                reader.next();
                if (isSessionIdElement(reader)) {
                    log.debug("sessionid element found");
                    return reader.getElementText();
                }
            }
        } finally {
            try {
                reader.close();
            } catch (Exception e) {
                log.debug("Failed to close XMLStreamReader", e);
            }
        }

        log.debug("no sessionid element found");
        return null;
    }

    private @NotNull XMLStreamReader getXmlStreamReader(Message msg) throws XMLStreamException {
        return new FixedStreamReader(XMLInputFactoryFactory.inputFactory().createXMLStreamReader(msg.getBodyAsStreamDecoded()));
    }

    private boolean isSessionIdElement(XMLStreamReader reader) {
        return reader.isStartElement() &&
               localName.equals(reader.getLocalName()) &&
               (namespace == null || namespace.equals(reader.getNamespaceURI()));
    }

    public String getLocalName() {
        return localName;
    }

    /**
     * @description Specifies local name of session element.
     * @example session
     */
    @Required
    @MCAttribute
    public void setLocalName(String localName) {
        this.localName = localName;
    }

    public String getNamespace() {
        return namespace;
    }

    /**
     * @description Specifies namespace of session element.
     * @example <a href="http://chat.predic8.com/">http://chat.predic8.com/</a>
     */
    @MCAttribute
    public void setNamespace(String namespace) {
        this.namespace = namespace;
    }

    @Override
    public void write(XMLStreamWriter out)
            throws XMLStreamException {

        out.writeStartElement("xmlSessionIdExtractor");

        out.writeAttribute("localName", localName);

        if (namespace != null) {
            out.writeAttribute("namespace", namespace);
        }

        out.writeEndElement();
    }

    @Override
    protected void parseAttributes(XMLStreamReader token)
            throws XMLStreamException {
        localName = token.getAttributeValue("", "localName");
        namespace = token.getAttributeValue("", "namespace");
    }

    @Override
    protected String getElementName() {
        return "xmlSessionIdExtractor";
    }


}
