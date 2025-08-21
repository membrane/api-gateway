/* Copyright 2012 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */
package com.predic8.membrane.core.interceptor.ws_addressing;

import com.predic8.membrane.core.util.URI;
import com.predic8.membrane.core.util.*;
import com.predic8.xml.beautifier.*;
import org.jetbrains.annotations.*;
import org.slf4j.*;

import javax.xml.namespace.*;
import javax.xml.stream.*;
import javax.xml.stream.events.*;
import java.io.*;
import java.net.*;
import java.util.*;


public class WsaEndpointRewriter {

    private static final Logger log = LoggerFactory.getLogger(WsaEndpointRewriter.class);

    private static final String ADDRESSING_URI_2005_08 = "http://www.w3.org/2005/08/addressing";
    private static final String ADDRESSING_URI_2004_08 = "http://schemas.xmlsoap.org/ws/2004/08/addressing";
    public static final String REPLY_TO = "ReplyTo";
    public static final String ADDRESS = "Address";

    private final XMLInputFactory inputFactory;
    private final XMLEventFactory ef;

    public WsaEndpointRewriter() {
        inputFactory = XMLInputFactoryFactory.inputFactory();
        ef = XMLEventFactory.newFactory();
    }

    public void rewriteEndpoint(InputStream reader, OutputStream writer, WsaEndpointRewriterInterceptor.Location location) throws XMLStreamException {
        XMLEventReader parser = inputFactory.createXMLEventReader(reader);
        XMLEventWriter eventWriter = XMLOutputFactory.newInstance().createXMLEventWriter(writer);

        String url;

        boolean inReplyTo = false;

        while (parser.hasNext()) {
            XMLEvent e = parser.nextEvent();

            if (e.isStartElement()) {
                StartElement se = e.asStartElement();
                if (isReplyTo(se)) {
                    inReplyTo = true;
                }
                if (inReplyTo && isAddress(se)) {
                    url = parser.getElementText();
                    try {
                        addRewrittenAddressElement(eventWriter, url, location, se);
                    } catch (URISyntaxException ex) {
                        log.info("Error parsing addressing URI: {}", url, ex);
                        throw new RuntimeException(ex);
                    }
                    continue;
                }
            }
            if (e.isEndElement()) {
                if (isReplyTo(e.asEndElement())) {
                    inReplyTo = false;
                }
            }
            eventWriter.add(e);
        }
    }

    private void addRewrittenAddressElement(XMLEventWriter writer, String address, WsaEndpointRewriterInterceptor.Location location, StartElement se) throws XMLStreamException, URISyntaxException {
        writer.add(ef.createStartElement("", se.getName().getNamespaceURI(),
                se.getName().getLocalPart(), se.getAttributes(), se.getNamespaces(),
                se.getNamespaceContext()));
        writer.add(ef.createCharacters(rewrite(address, location)));
        writer.add(ef.createEndElement("", se.getName().getNamespaceURI(),
                se.getName().getLocalPart(), se.getNamespaces()));
    }

    private static @NotNull String rewrite(String address, WsaEndpointRewriterInterceptor.Location location) throws URISyntaxException {
        URI orig = new URIFactory(false).create(address);
        return location.protocol() + "://" + location.host() + ":" + location.port() + orig.getPath();
    }

    private boolean isReplyTo(StartElement startElement) {
        return isElement(startElement.getName(), REPLY_TO);
    }

    private boolean isReplyTo(EndElement endElement) {
        return isElement(endElement.getName(), REPLY_TO);
    }

    private boolean isAddress(StartElement startElement) {
        return isElement(startElement.getName(), ADDRESS);
    }

    private static boolean isElement(QName name, String elementName) {
        return List.of(ADDRESSING_URI_2004_08, ADDRESSING_URI_2005_08).stream()
                .anyMatch(ns -> (name.getNamespaceURI().equals(ns) && name.getLocalPart().equals(elementName)));
    }
}
