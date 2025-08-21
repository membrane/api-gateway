/* Copyright 2009, 2012 predic8 GmbH, www.predic8.com

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

import com.predic8.membrane.annot.*;
import com.predic8.membrane.core.*;
import com.predic8.membrane.core.http.*;
import org.jetbrains.annotations.*;
import org.slf4j.*;

import javax.xml.stream.*;

/**
 * @description Extracts a session ID from an XML HTTP request body based on the qualified name of an XML element.
 */
@MCElement(name = "xmlSessionIdExtractor")
public class XMLElementSessionIdExtractor extends AbstractSessionIdExtractor {

    private static final Logger log = LoggerFactory.getLogger(XMLElementSessionIdExtractor.class.getName());

    private String localName;
    private String namespace;

    /*
     * Use XMLInputFactory thread safe
     */
    private static final ThreadLocal<XMLInputFactory> XML_INPUT_FACTORY_THREAD_LOCAL = ThreadLocal.withInitial(XMLInputFactory::newInstance);

    @Override
    public String getSessionId(Message msg) throws Exception {
        if (!msg.isXML()) {
            log.debug("Did not search for an XML element in non-XML message.");
            return null;
        }

        log.debug("searching for sessionid");

        XMLStreamReader reader = getXmlStreamReader(msg);
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
        if (msg.getCharset() != null) {
            return new FixedStreamReader(XML_INPUT_FACTORY_THREAD_LOCAL.get().createXMLStreamReader(msg.getBodyAsStreamDecoded(), msg.getCharset()));
        }
        return new FixedStreamReader(XML_INPUT_FACTORY_THREAD_LOCAL.get().createXMLStreamReader(msg.getBodyAsStream()));
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
    @Required
    @MCAttribute
    public void setNamespace(String namespace) {
        this.namespace = namespace;
    }

    @Override
    public void write(XMLStreamWriter out)
            throws XMLStreamException {

        out.writeStartElement("xmlSessionIdExtractor");

        out.writeAttribute("localName", localName);
        out.writeAttribute("namespace", namespace);

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
