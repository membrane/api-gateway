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

import org.xml.sax.ContentHandler;
import org.xml.sax.InputSource;
import org.xml.sax.Locator;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.LocatorImpl;
import org.xml.sax.helpers.XMLFilterImpl;

import javax.xml.transform.Source;
import javax.xml.transform.sax.SAXSource;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * An element recorded as SAX events, which can be replayed as a document of its own any number
 * of times, e.g. once for each schema it is validated against. Each event keeps its line and
 * column in the document it was recorded from, so errors reported while reading the replay point
 * into that document.
 */
public final class RecordedElement {

    /**
     * One recorded call of a {@link ContentHandler}.
     */
    @FunctionalInterface
    interface Event {
        void replay(ContentHandler handler) throws SAXException;
    }

    private record PositionedEvent(int line, int column, Event event) {
    }

    /**
     * Starts with the start of the element.
     */
    private final List<PositionedEvent> events = new ArrayList<>();

    /**
     * Prefixes declared outside the element but used inside it, declared in front of it on replay.
     */
    private final Map<String, String> outerNamespaces = new LinkedHashMap<>();

    /**
     * The start of the element as it was read, if the recorded start differs from it; null
     * otherwise.
     */
    private final Event originalStart;

    /**
     * @param start         the start of the element as recorded
     * @param originalStart the start of the element as it was read, if it differs from
     *                      {@code start}; null otherwise
     */
    RecordedElement(Locator locator, Event start, Event originalStart) {
        add(locator, start);
        this.originalStart = originalStart;
    }

    void add(Locator locator, Event event) {
        events.add(new PositionedEvent(locator.getLineNumber(), locator.getColumnNumber(), event));
    }

    void declareOuterNamespace(String prefix, String uri) {
        outerNamespaces.put(prefix, uri);
    }

    /**
     * A source that replays the element as a document.
     */
    public Source toSource() {
        return new SAXSource(new Player(false), new InputSource());
    }

    /**
     * Whether the recorded start of the element differs from the one that was read.
     */
    public boolean isStartChanged() {
        return originalStart != null;
    }

    /**
     * A source that replays the element as a document, starting it as it was read.
     */
    public Source toOriginalSource() {
        return new SAXSource(new Player(isStartChanged()), new InputSource());
    }

    private void replay(ContentHandler handler, boolean original) throws SAXException {
        final var locator = new LocatorImpl();
        handler.setDocumentLocator(locator);
        handler.startDocument();
        moveTo(locator, events.getFirst());
        for (var namespace : outerNamespaces.entrySet()) {
            handler.startPrefixMapping(namespace.getKey(), namespace.getValue());
        }
        for (var event : events) {
            moveTo(locator, event);
            (original && event == events.getFirst() ? originalStart : event.event()).replay(handler);
        }
        for (var prefix : outerNamespaces.keySet()) {
            handler.endPrefixMapping(prefix);
        }
        handler.endDocument();
    }

    private static void moveTo(LocatorImpl locator, PositionedEvent event) {
        locator.setLineNumber(event.line());
        locator.setColumnNumber(event.column());
    }

    /**
     * Replays the recording instead of parsing the input source it is given.
     */
    private class Player extends XMLFilterImpl {

        private final boolean original;

        Player(boolean original) {
            this.original = original;
        }

        @Override
        public void parse(InputSource input) throws SAXException {
            replay(getContentHandler(), original);
        }

        @Override
        public void parse(String systemId) throws SAXException {
            replay(getContentHandler(), original);
        }
    }
}
