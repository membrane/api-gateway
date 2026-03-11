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

package com.predic8.membrane.core.ws.relocator;

import com.predic8.xml.beautifier.*;
import org.slf4j.*;

import javax.annotation.concurrent.*;
import javax.xml.namespace.*;
import javax.xml.stream.*;
import javax.xml.stream.events.*;
import java.io.*;
import java.net.*;
import java.util.*;

@NotThreadSafe
public class Relocator {
    private static final Logger log = LoggerFactory.getLogger(Relocator.class);

    private final XMLEventFactory fac = XMLEventFactory.newInstance();

    private final String host;
    private final int port;
    private final String contextPath;
    private final String protocol;
    private final XMLEventWriter writer;
    private final PathRewriter pathRewriter;

    private final Map<QName, String> relocatingAttributes = new HashMap<>();

    public Relocator(OutputStreamWriter osw, String protocol, String host,
                     int port, String contextPath, PathRewriter pathRewriter) throws Exception {
        this.writer = XMLOutputFactory.newInstance().createXMLEventWriter(osw);
        this.host = host;
        this.port = port;
        this.protocol = protocol;
        this.contextPath = contextPath;
        this.pathRewriter = pathRewriter;
    }

    public static String getNewLocation(String addr, String protocol, String host, int port, String contextPath) {
        try {
            var oldURL = new URL(addr);
            if (port == -1) {
                return new URL(protocol, host, contextPath + oldURL.getFile()).toString();
            }
            if ("http".equals(protocol) && port == 80)
                port = -1;
            if ("https".equals(protocol) && port == 443)
                port = -1;
            return new URL(protocol, host, port, contextPath + oldURL.getFile()).toString();
        } catch (MalformedURLException e) {
            log.error("", e);
        }
        return "";
    }

    public void relocate(InputStream is) throws Exception {
        relocate(XMLInputFactoryFactory.inputFactory().createXMLEventReader(is));
    }

    private void relocate(XMLEventReader parser) throws XMLStreamException {
        try {
            while (parser.hasNext()) {
                writer.add(process(parser));
            }
            writer.flush();
        } finally {
            close(parser);
        }
    }

    private XMLEvent process(XMLEventReader parser) throws XMLStreamException {
        var event = parser.nextEvent();
        if (!event.isStartElement())
            return event;

        var attr = relocatingAttributes.get(getElementName(event));
        return attr != null ? replace(event, attr) : event;
    }

    private QName getElementName(XMLEvent event) {
        return event.asStartElement().getName();
    }

    private XMLEvent replace(XMLEvent event, String attribute) {
        var start = event.asStartElement();
        return fac.createStartElement(start.getName(),
                new ReplaceIterator(fac, attribute, start.getAttributes()),
                start.getNamespaces());
    }

    public Map<QName, String> getRelocatingAttributes() {
        return relocatingAttributes;
    }

    public interface PathRewriter {
        String rewrite(String path);
    }

    private class ReplaceIterator implements Iterator<Attribute> {

        private final XMLEventFactory fac;
        private final Iterator<Attribute> attrs;
        private final String replace;

        public ReplaceIterator(XMLEventFactory fac, String replace, Iterator<Attribute> attrs) {
            this.fac = fac;
            this.replace = replace;
            this.attrs = attrs;
        }

        public boolean hasNext() {
            return attrs.hasNext();
        }

        public Attribute next() {
            var atr = attrs.next();
            if (atr.getName().equals(new QName(replace))) {
                var value = atr.getValue();
                if (pathRewriter != null) {
                    value = pathRewriter.rewrite(value);
                    if (value.startsWith("http"))
                        return fac.createAttribute(replace, getNewLocation(value, protocol, host, port, contextPath));
                    return fac.createAttribute(replace, value);
                }
                if (value.startsWith("http"))
                    return fac.createAttribute(replace, getNewLocation(value, protocol, host, port, contextPath));
            }
            return atr;
        }

        public void remove() {
            attrs.remove();
        }
    }

    private static void close(XMLEventReader parser) {
        try {
            parser.close();
        } catch (Exception ignore) {
        }
    }
}