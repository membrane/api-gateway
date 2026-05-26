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

package com.predic8.membrane.core.util.http;

import com.predic8.membrane.core.http.Chunk;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class SSEParserTest {

    @Test
    void parsesSingleEvent() {
        var parser = new SSEParser(Set.of("done"));

        assertFalse(parser.parse(chunk("""
                event: message
                data: hello
                
                """)));

        var events = parser.getEvents();

        assertEquals(1, events.size());
        assertEquals("message", events.getFirst().name());
        assertEquals("hello", events.getFirst().data());
        assertTrue(parser.getTerminalEvent().isEmpty());
    }

    @Test
    void parsesMultilineData() {
        var parser = new SSEParser(Set.of("done"));

        parser.parse(chunk("""
                event: message
                data: first
                data: second
                
                """));

        assertEquals("first\nsecond", parser.getEvents().getFirst().data());
    }

    @Test
    void parsesEventSplitAcrossChunks() {
        var parser = new SSEParser(Set.of("done"));

        assertFalse(parser.parse(chunk("""
                event: mes""")));

        assertFalse(parser.parse(chunk("""
                sage
                data: hel""")));

        assertFalse(parser.parse(chunk("""
                lo
                
                """)));

        var event = parser.getEvents().getFirst();

        assertEquals("message", event.name());
        assertEquals("hello", event.data());
    }

    @Test
    void returnsTrueWhenTerminalEventIsFound() {
        var parser = new SSEParser(Set.of("done"));

        assertTrue(parser.parse(chunk("""
                event: done
                data: {"usage":{"total_tokens":42}}
                
                """)));

        var terminal = parser.getTerminalEvent();

        assertTrue(terminal.isPresent());
        assertEquals("done", terminal.get().name());
        assertEquals("{\"usage\":{\"total_tokens\":42}}", terminal.get().data());
    }

    @Test
    void ignoresChunksAfterTerminalEvent() {
        var parser = new SSEParser(Set.of("done"));

        assertTrue(parser.parse(chunk("""
                event: done
                data: final
                
                """)));

        assertTrue(parser.parse(chunk("""
                event: message
                data: ignored
                
                """)));

        assertEquals(1, parser.getEvents().size());
        assertEquals("done", parser.getEvents().getFirst().name());
    }

    @Test
    void ignoresCommentsAndUnknownFields() {
        var parser = new SSEParser(Set.of("done"));

        parser.parse(chunk("""
                : comment
                id: 123
                retry: 1000
                event: message
                data: hello
                
                """));

        var event = parser.getEvents().getFirst();

        assertEquals("message", event.name());
        assertEquals("hello", event.data());
    }

    @Test
    void supportsCrLfLineEndings() {
        var parser = new SSEParser(Set.of("done"));

        parser.parse(chunk("event: message\r\ndata: hello\r\n\r\n"));

        var event = parser.getEvents().getFirst();

        assertEquals("message", event.name());
        assertEquals("hello", event.data());
    }

    @Test
    void returnsUnmodifiableEventsList() {
        var parser = new SSEParser(Set.of("done"));

        parser.parse(chunk("""
                event: message
                data: hello
                
                """));

        assertThrows(UnsupportedOperationException.class,
                () -> parser.getEvents().add(new SSEParser.SSEEvent("x", "y")));
    }

    private static Chunk chunk(String content) {
        return new Chunk(content.getBytes());
    }
}