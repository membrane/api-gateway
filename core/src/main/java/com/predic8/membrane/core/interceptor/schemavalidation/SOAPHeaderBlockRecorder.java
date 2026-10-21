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

import com.predic8.membrane.core.util.xml.parser.HardenedSaxParser;
import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.Locator;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.AttributesImpl;
import org.xml.sax.helpers.DefaultHandler;
import org.xml.sax.helpers.LocatorImpl;
import org.xml.sax.helpers.NamespaceSupport;

import javax.xml.namespace.QName;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Predicate;
import java.util.regex.Pattern;

import static com.predic8.membrane.annot.Constants.SOAP11_NS;
import static com.predic8.membrane.annot.Constants.SOAP12_NS;

/**
 * Records the selected SOAP header blocks - direct children of {@code soap:Header} - in a single
 * parse of the message, so that each can be schema-validated on its own, against several schemas,
 * without parsing the message again. The header usually holds several blocks, which together are
 * no well-formed document, and each is described by its own schema. Each block is handed on as
 * soon as it is complete, so only one is held at a time.
 * <p>
 * Parsing stops at the end of the header, or at the first other child of the envelope if there is
 * no header: SOAP puts the header first, and the body is not needed. A header after the body is
 * rejected when the body is validated, see {@link SOAPXMLFilter}.
 * <p>
 * The attributes SOAP defines for every header block ({@code mustUnderstand}, {@code actor}/
 * {@code role}, {@code relay}, {@code encodingStyle}) are removed from the block's root element:
 * they belong to the envelope, and the block's own schema does not declare them.
 */
public class SOAPHeaderBlockRecorder extends DefaultHandler {

    private static final int HEADER_DEPTH = 2;
    private static final int BLOCK_DEPTH = 3;

    private static final Pattern WHITESPACE = Pattern.compile("\\s+");

    private final Set<QName> selected;

    /**
     * Called with each selected block; returns {@code false} to stop.
     */
    private final Predicate<RecordedElement> action;

    private boolean stopped;

    private final NamespaceSupport namespaces = new NamespaceSupport();

    /**
     * Whether the context of the element about to start is already pushed. SAX reports the
     * prefixes an element declares before the element.
     */
    private boolean contextPushed;

    private Locator locator = new LocatorImpl();

    /**
     * Depth of the current element: {@code 1} for the envelope, {@code 2} for the header,
     * {@code 3} for the header blocks.
     */
    private int depth;

    /**
     * The block being recorded; null outside the selected blocks.
     */
    private RecordedElement block;

    /**
     * Prefixes of the QName-like values in the block being recorded.
     */
    private final Set<String> usedPrefixes = new HashSet<>();

    /**
     * Text of the block being recorded since the last tag. SAX may report it in several chunks,
     * split anywhere, also inside a QName.
     */
    private final StringBuilder text = new StringBuilder();

    private SOAPHeaderBlockRecorder(Set<QName> selected, Predicate<RecordedElement> action) {
        this.selected = selected;
        this.action = action;
    }

    /**
     * @param selected names of the header blocks to record
     * @param action   called with each selected header block in document order; returns
     *                 {@code false} to stop
     * @return {@code false} if {@code action} stopped, {@code true} otherwise
     * @throws SAXException if the message is not well-formed up to the end of its header
     */
    public static boolean forEach(InputStream message, Set<QName> selected, Predicate<RecordedElement> action) throws IOException, SAXException {
        final var recorder = new SOAPHeaderBlockRecorder(selected, action);
        final var reader = HardenedSaxParser.newSAXParser().getXMLReader();
        reader.setContentHandler(recorder);
        try {
            reader.parse(new InputSource(message));
        } catch (StopParsing ignored) {
            // the rest of the message is not needed
        }
        return !recorder.stopped;
    }

    private static boolean isSoapNamespace(String uri) {
        return SOAP11_NS.equals(uri) || SOAP12_NS.equals(uri);
    }

    @Override
    public void setDocumentLocator(Locator locator) {
        this.locator = locator;
    }

    @Override
    public void startPrefixMapping(String prefix, String uri) {
        if (!contextPushed) {
            namespaces.pushContext();
            contextPushed = true;
        }
        namespaces.declarePrefix(prefix, uri);
        if (block != null)
            block.add(locator, handler -> handler.startPrefixMapping(prefix, uri));
    }

    @Override
    public void endPrefixMapping(String prefix) {
        if (block != null)
            block.add(locator, handler -> handler.endPrefixMapping(prefix));
    }

    @Override
    public void startElement(String uri, String localName, String qName, Attributes atts) throws SAXException {
        if (!contextPushed)
            namespaces.pushContext();
        contextPushed = false;
        depth++;
        if (depth == HEADER_DEPTH && !("Header".equals(localName) && isSoapNamespace(uri)))
            throw new StopParsing(); // the header comes first, so there is none
        if (depth == BLOCK_DEPTH && selected.contains(new QName(uri, localName))) {
            final var attributes = withoutSoapAttributes(atts);
            block = new RecordedElement(locator, handler -> handler.startElement(uri, localName, qName, attributes));
            collectPrefixes(attributes);
            return;
        }
        if (block == null)
            return;
        flushText();
        final var attributes = new AttributesImpl(atts);
        block.add(locator, handler -> handler.startElement(uri, localName, qName, attributes));
        collectPrefixes(attributes);
    }

    @Override
    public void endElement(String uri, String localName, String qName) throws SAXException {
        if (block != null) {
            flushText();
            block.add(locator, handler -> handler.endElement(uri, localName, qName));
            if (depth == BLOCK_DEPTH)
                finishBlock();
        }
        namespaces.popContext();
        if (depth == HEADER_DEPTH)
            throw new StopParsing(); // the rest of the message holds no header blocks
        depth--;
    }

    @Override
    public void characters(char[] ch, int start, int length) {
        if (block == null)
            return; // envelope text (whitespace) must not leak into the prolog
        final var chars = Arrays.copyOfRange(ch, start, start + length);
        block.add(locator, handler -> handler.characters(chars, 0, chars.length));
        text.append(chars);
    }

    @Override
    public void ignorableWhitespace(char[] ch, int start, int length) {
        if (block == null)
            return;
        final var chars = Arrays.copyOfRange(ch, start, start + length);
        block.add(locator, handler -> handler.ignorableWhitespace(chars, 0, chars.length));
    }

    /**
     * Declares in front of the block the prefixes it uses but does not declare itself, resolved
     * against the namespaces in scope at its root, which still are here. Element and attribute
     * names come with their namespace, so only the default namespace and the prefixes of QName
     * values, e.g. of {@code xsi:type}, are needed. Declaring every prefix in scope would cost
     * each block as much as the envelope declares.
     */
    private void finishBlock() throws StopParsing {
        usedPrefixes.add("");
        for (var prefix : usedPrefixes) {
            final var uri = namespaces.getURI(prefix);
            if (uri != null)
                block.declareOuterNamespace(prefix, uri);
        }
        usedPrefixes.clear();
        final var complete = block;
        block = null;
        if (!action.test(complete)) {
            stopped = true;
            throw new StopParsing();
        }
    }

    private void flushText() {
        collectPrefixes(text);
        text.setLength(0);
    }

    private void collectPrefixes(Attributes attributes) {
        for (int i = 0; i < attributes.getLength(); i++)
            collectPrefixes(attributes.getValue(i));
    }

    private void collectPrefixes(CharSequence value) {
        for (var token : WHITESPACE.split(value)) {
            final var colon = token.indexOf(':');
            if (colon > 0)
                usedPrefixes.add(token.substring(0, colon));
        }
    }

    private static Attributes withoutSoapAttributes(Attributes atts) {
        var result = new AttributesImpl(atts);
        for (int i = result.getLength() - 1; i >= 0; i--) {
            if (isSoapNamespace(result.getURI(i)))
                result.removeAttribute(i);
        }
        return result;
    }

    /**
     * Stops parsing once the header is read or the action asks to stop.
     */
    private static final class StopParsing extends SAXException {
    }
}
