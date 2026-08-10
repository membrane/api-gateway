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
 */
public class FaultDetailXMLFilter extends XMLFilterImpl {

    private final SoapVersion version;
    private boolean inDetail;

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
        if (isDetail(uri, localName)) {
            inDetail = true;
            return;
        }
        if (!inDetail)
            return;
        super.startElement(uri, localName, qName, atts);
    }

    @Override
    public void endElement(String uri, String localName, String qName) throws SAXException {
        if (isDetail(uri, localName)) {
            inDetail = false;
            return;
        }
        if (!inDetail)
            return;
        super.endElement(uri, localName, qName);
    }
}
