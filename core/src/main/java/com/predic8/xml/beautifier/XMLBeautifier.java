/* Copyright 2008-2015 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */

package com.predic8.xml.beautifier;

import org.jetbrains.annotations.*;
import org.slf4j.*;

import javax.xml.stream.*;
import java.io.*;

import static java.lang.Boolean.FALSE;
import static javax.xml.stream.XMLInputFactory.*;
import static javax.xml.stream.XMLStreamConstants.*;

public class XMLBeautifier {

    private static final Logger log = LoggerFactory.getLogger(XMLBeautifier.class);
    private boolean empty;

    private boolean startTagClosed = true;

    private boolean charContent;

    private boolean endCalled;

    private boolean firstElement = true;

    private final XMLBeautifierFormatter formatter;

    private String detectedEncoding;

    public XMLBeautifier(XMLBeautifierFormatter formatter) {
        this.formatter = formatter;
    }

    public void parse(InputStream inputStream) throws IOException {
        try {
            parse(getXmlInputFactory().createXMLStreamReader(inputStream));
        } catch (Exception e) {
            throw new IOException(e);
        }
    }

    public void parse(Reader reader) throws Exception {
        parse(getXmlInputFactory().createXMLStreamReader(reader));
    }

    private static @NotNull XMLInputFactory getXmlInputFactory() {
        XMLInputFactory factory = XMLInputFactoryFactory.inputFactory();
        factory.setProperty(SUPPORT_DTD, FALSE);
        return factory;
    }

    private void parse(XMLStreamReader reader) throws Exception {
        while (reader.hasNext()) {
            displayEvent(reader.getEventType(), reader);
            reader.next();
        }
    }

    private void indent() throws IOException {
        formatter.indent();
    }

    private void displayEvent(int eventType, XMLStreamReader reader) throws Exception {

        switch (eventType) {
            case START_ELEMENT -> {
                if (!startTagClosed) {
                    formatter.closeTag();
                }

                if (!endCalled && !firstElement) {
                    printNewLine(false);
                }

                if (!charContent) {
                    indent();
                }

                formatter.startTag();

                formatter.writeTag(reader.getPrefix(), reader.getLocalName());
                formatter.incrementIndentBy(2);

                int indent = reader.getPrefix() != null ? reader.getPrefix().length() : 0;
                indent += reader.getLocalName().length();
                writeNamespaceAttributes(reader, indent);
                writeAttributes(reader, indent);

                startTagClosed = false;
                charContent = false;
                empty = true;
                endCalled = false;
                firstElement = false;
            }
            case END_ELEMENT -> {
                formatter.decrementIndentBy(2);
                if (empty) {
                    formatter.closeEmptyTag();
                    empty = false;
                    startTagClosed = true;
                    printNewLine(true);
                    break;
                }
                if (!charContent) {
                    indent();
                }

                formatter.closeTag(reader.getPrefix(), reader.getLocalName());
                startTagClosed = true;
                printNewLine(true);
                charContent = false;
            }
            case CHARACTERS -> {
                empty = false;
                if (!startTagClosed) {
                    formatter.closeTag();
                    startTagClosed = true;
                }

                charContent = containsNonWhitespaceCharacters(reader.getText());

                formatter.writeText(reader.getText()); // TODO check format with <foo> \t\n<bar>
            }
            case COMMENT -> {
                if (!startTagClosed) {
                    formatter.closeTag();
                    startTagClosed = true;
                }
                indent();
                writeComment(reader);
                charContent = false;
            }
            // The current Java XML parser does not send CDATA events. Instead it passes it as characters (See test)
            // The implementation here is for the case that a future or different parser suddenly supports CDATA to
            // prevent data loss.
            case CDATA -> {
                empty = false;
                if (!startTagClosed) {
                    formatter.closeTag();
                    startTagClosed = true;
                }
                formatter.writeText(reader.getText());
            }
            case START_DOCUMENT -> {
                detectedEncoding = reader.getEncoding();
                log.debug("Detected encoding: {}", detectedEncoding);
                writeStartDocument(reader);
            }
            default -> {
            }
        }
    }

    private void writeStartDocument(XMLStreamReader reader) throws IOException {
        formatter.writeVersionAndEncoding(reader.getVersion(), reader.getEncoding());
    }

    private boolean containsNonWhitespaceCharacters(String s) {
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c != 10 && c != 13 && c != 32 && c != 9) {
                return true;
            }
        }
        return false;
    }

    private void writeAttributes(XMLStreamReader parser, int indent) throws IOException {
        formatter.incrementIndentBy(indent);
        for (int i = 0; i < parser.getAttributeCount(); i++) {
            if (parser.getNamespaceCount() > 0 || i != 0) {
                formatter.printNewLine();
                formatter.indent();
            }

            formatter.writeText(" ");
            formatter.writeAttribute(parser.getAttributePrefix(i), parser.getAttributeLocalName(i), parser.getAttributeValue(i));
        }
        formatter.decrementIndentBy(indent);
    }

    private void writeComment(XMLStreamReader reader) throws IOException {
        formatter.writeComment(reader.getText());
    }

    private void writeNamespaceAttributes(XMLStreamReader reader, int indent) throws IOException {
        formatter.incrementIndentBy(indent);
        for (int j = 0; j < reader.getNamespaceCount(); j++) {
            if (j != 0) {
                formatter.printNewLine();
                formatter.indent();
            }
            formatter.writeNamespaceAttribute(reader.getNamespacePrefix(j), reader.getNamespaceURI(j));
        }
        formatter.decrementIndentBy(indent);
    }

    private void printNewLine(boolean endElement) throws IOException {
        endCalled = endElement;
        formatter.printNewLine();
    }

    public String getDetectedEncoding() {
        return detectedEncoding;
    }
}
