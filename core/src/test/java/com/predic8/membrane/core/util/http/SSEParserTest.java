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

import java.io.ByteArrayOutputStream;
import java.util.Arrays;
import java.util.Set;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.*;

class SSEParserTest {

    @Test
    void parsesSingleEvent() {
        var parser = new SSEParser(Set.of("done"));

        assertFalse(parser.parse(chunk("""
                event: message
                data: hello
                
                """)));

        var events = parser.drainEvents();

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

        assertEquals("first\nsecond", parser.drainEvents().getFirst().data());
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

        var event = parser.drainEvents().getFirst();

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

        var events = parser.drainEvents();

        assertEquals(1, events.size());
        assertEquals("done", events.getFirst().name());
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

        var event = parser.drainEvents().getFirst();

        assertEquals("message", event.name());
        assertEquals("hello", event.data());
    }

    @Test
    void supportsCrLfLineEndings() {
        var parser = new SSEParser(Set.of("done"));

        parser.parse(chunk("event: message\r\ndata: hello\r\n\r\n"));

        var event = parser.drainEvents().getFirst();

        assertEquals("message", event.name());
        assertEquals("hello", event.data());
    }

    /**
     * A CR at the end of one chunk and the LF at the start of the next are one line end, not two:
     * the LF must not end the event before its second data line.
     */
    @Test
    void crLfSplitAcrossChunksIsOneLineEnd() {
        var parser = new SSEParser(Set.of("done"));

        parser.parse(chunk("data: one\r"));
        parser.parse(chunk("\ndata: two\r\n\r\n"));

        var events = parser.drainEvents();
        assertEquals(1, events.size());
        assertEquals("one\ntwo", events.getFirst().data());
    }

    /**
     * A lone CR is a line end of its own, so a line starting the next chunk is kept.
     */
    @Test
    void crAtTheEndOfAChunkIsALineEnd() {
        var parser = new SSEParser(Set.of("done"));

        parser.parse(chunk("data: one\r"));
        parser.parse(chunk("data: two\r\r"));

        assertEquals("one\ntwo", parser.drainEvents().getFirst().data());
    }

    /**
     * A character whose UTF-8 bytes are split over two chunks is decoded as a whole.
     */
    @Test
    void multiByteCharacterSplitAcrossChunks() {
        var parser = new SSEParser(Set.of("done"));
        var bytes = "data: Grüße €\n\n".getBytes(UTF_8);
        var euro = bytes.length - 5; // the first of the three bytes of €, followed by "\n\n"

        parser.parse(new Chunk(Arrays.copyOfRange(bytes, 0, euro + 1)));
        parser.parse(new Chunk(Arrays.copyOfRange(bytes, euro + 1, euro + 2)));
        parser.parse(new Chunk(Arrays.copyOfRange(bytes, euro + 2, bytes.length)));

        assertEquals("Grüße €", parser.drainEvents().getFirst().data());
    }

    /**
     * A malformed byte is replaced by U+FFFD and does not drop the event.
     */
    @Test
    void malformedByteIsReplacedAndEventIsKept() {
        var parser = new SSEParser(Set.of("done"));
        var out = new ByteArrayOutputStream();
        out.writeBytes("data: a".getBytes(UTF_8));
        out.write(0xFF); // never valid in UTF-8
        out.writeBytes(" b\n\n".getBytes(UTF_8));

        parser.parse(new Chunk(out.toByteArray()));

        assertEquals("a\uFFFD b", parser.drainEvents().getFirst().data());
    }

    @Test
    void returnsUnmodifiableEventsList() {
        var parser = new SSEParser(Set.of("done"));

        parser.parse(chunk("""
                event: message
                data: hello
                
                """));

        assertThrows(UnsupportedOperationException.class,
                () -> parser.drainEvents().add(new SSEParser.SSEEvent("x", "y")));
    }

    /**
     * A stream is consumed as it arrives, so what was handed out is not kept around.
     */
    @Test
    void drainedEventsAreNotHandedOutTwice() {
        var parser = new SSEParser(Set.of("done"));

        parser.parse(chunk("""
                event: message
                data: hello

                """));

        assertEquals(1, parser.drainEvents().size());
        assertTrue(parser.drainEvents().isEmpty());
    }

    @Test
    void terminalEventIsStillAvailableAfterDraining() {
        var parser = new SSEParser(Set.of("done"));

        parser.parse(chunk("""
                event: done
                data: final

                """));
        parser.drainEvents();

        assertEquals("done", parser.getTerminalEvent().orElseThrow().name());
    }

    private static Chunk chunk(String content) {
        return new Chunk(content.getBytes());
    }
}