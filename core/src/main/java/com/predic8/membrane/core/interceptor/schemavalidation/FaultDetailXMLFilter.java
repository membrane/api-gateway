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
package com.predic8.membrane.core.interceptor.schemavalidation;

import com.predic8.membrane.annot.Constants.SoapVersion;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;
import org.xml.sax.helpers.XMLFilterImpl;

import static com.predic8.membrane.annot.Constants.SOAP12_NS;

/**
 * Strips a SOAP fault message down to the content of its {@code detail} (SOAP 1.1, unqualified)
 * or {@code Detail} (SOAP 1.2, {@link com.predic8.membrane.annot.Constants#SOAP12_NS}) element, so
 * that only the WSDL-typed fault payload is passed on. Mirrors {@link SOAPXMLFilter}, which does
 * the same for {@code soap:Body}.
 * <p>
 * Unlike {@link SOAPXMLFilter}, the boundary is tracked by element depth, not by matching the
 * name a second time on the closing tag: a fault payload may well contain an element of its own
 * named {@code detail} (fault schemas usually leave {@code elementFormDefault} at its
 * {@code unqualified} default), which would otherwise end the payload halfway through and emit
 * an unbalanced document.
 */
public class FaultDetailXMLFilter extends XMLFilterImpl {

    private final SoapVersion version;

    /**
     * {@code -1} outside the detail element, {@code 0} on the detail element itself, {@code n}
     * when {@code n} levels deep inside its content.
     */
    private int depth = -1;

    public FaultDetailXMLFilter(XMLReader reader, SoapVersion version) {
        super(reader);
        this.version = version;
    }

    private boolean isDetail(String uri, String localName) {
        return switch (version) {
            case SOAP11 -> "detail".equals(localName) && uri.isEmpty();
            case SOAP12 -> "Detail".equals(localName) && SOAP12_NS.equals(uri);
            default -> false;
        };
    }

    @Override
    public void startElement(String uri, String localName, String qName, Attributes atts) throws SAXException {
        if (depth < 0) {
            if (isDetail(uri, localName))
                depth = 0;
            return;
        }
        depth++;
        super.startElement(uri, localName, qName, atts);
    }

    @Override
    public void endElement(String uri, String localName, String qName) throws SAXException {
        if (depth < 0)
            return;
        if (depth == 0) {
            depth = -1; // the detail element itself: consumed, not forwarded
            return;
        }
        depth--;
        super.endElement(uri, localName, qName);
    }

    @Override
    public void characters(char[] ch, int start, int length) throws SAXException {
        if (depth <= 0)
            return; // envelope text (faultcode/faultstring, whitespace) must not leak into the prolog
        super.characters(ch, start, length);
    }

    @Override
    public void ignorableWhitespace(char[] ch, int start, int length) throws SAXException {
        if (depth <= 0)
            return;
        super.ignorableWhitespace(ch, start, length);
    }
}
