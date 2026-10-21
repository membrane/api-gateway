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

import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;
import org.xml.sax.helpers.AttributesImpl;
import org.xml.sax.helpers.XMLFilterImpl;

import static com.predic8.membrane.annot.Constants.SOAP11_NS;
import static com.predic8.membrane.annot.Constants.SOAP12_NS;

/**
 * Strips a SOAP message down to a single header block - the direct child of {@code soap:Header}
 * at a given position - so that it can be schema-validated on its own. The header usually holds
 * several blocks, which together are no well-formed document, and each is described by its own
 * schema.
 * <p>
 * The attributes SOAP defines for every header block ({@code mustUnderstand}, {@code actor}/
 * {@code role}, {@code relay}, {@code encodingStyle}) are removed from the block's root element:
 * they belong to the envelope, and the block's own schema does not declare them.
 */
public class SOAPHeaderBlockXMLFilter extends XMLFilterImpl {

    private static final int HEADER_DEPTH = 2;
    private static final int BLOCK_DEPTH = 3;

    /**
     * Position among the header blocks, starting with {@code 0}.
     */
    private final int index;

    /**
     * Depth of the current element: {@code 1} for the envelope, {@code 2} for its children,
     * {@code 3} for the header blocks.
     */
    private int depth;

    private boolean inHeader;

    private int blocksSeen;

    private boolean inBlock;

    public SOAPHeaderBlockXMLFilter(XMLReader reader, int index) {
        super(reader);
        this.index = index;
    }

    private static boolean isSoapNamespace(String uri) {
        return SOAP11_NS.equals(uri) || SOAP12_NS.equals(uri);
    }

    @Override
    public void startElement(String uri, String localName, String qName, Attributes atts) throws SAXException {
        depth++;
        if (depth == HEADER_DEPTH && "Header".equals(localName) && isSoapNamespace(uri)) {
            inHeader = true;
            return;
        }
        if (inHeader && depth == BLOCK_DEPTH) {
            inBlock = blocksSeen++ == index;
            if (inBlock)
                super.startElement(uri, localName, qName, withoutSoapAttributes(atts));
            return;
        }
        if (inBlock)
            super.startElement(uri, localName, qName, atts);
    }

    @Override
    public void endElement(String uri, String localName, String qName) throws SAXException {
        if (inBlock)
            super.endElement(uri, localName, qName);
        if (depth == BLOCK_DEPTH)
            inBlock = false;
        if (depth == HEADER_DEPTH)
            inHeader = false;
        depth--;
    }

    @Override
    public void characters(char[] ch, int start, int length) throws SAXException {
        if (!inBlock)
            return; // envelope text (whitespace) must not leak into the prolog
        super.characters(ch, start, length);
    }

    @Override
    public void ignorableWhitespace(char[] ch, int start, int length) throws SAXException {
        if (!inBlock)
            return;
        super.ignorableWhitespace(ch, start, length);
    }

    private static Attributes withoutSoapAttributes(Attributes atts) {
        var result = new AttributesImpl(atts);
        for (int i = result.getLength() - 1; i >= 0; i--) {
            if (isSoapNamespace(result.getURI(i)))
                result.removeAttribute(i);
        }
        return result;
    }
}
