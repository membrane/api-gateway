package com.predic8.membrane.core.util.http;

import com.predic8.membrane.core.http.Chunk;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public final class SSEParser {

    private final Set<String> terminalEventNames;
    private final StringBuilder buffer = new StringBuilder();

    private final List<SSEEvent> events = new ArrayList<>();

    private String eventName;
    private final StringBuilder data = new StringBuilder();

    private boolean terminalFound;

    public SSEParser(String... terminalEventNames) {
        this.terminalEventNames = Set.of(terminalEventNames);
    }

    public boolean parse(Chunk chunk) {
        if (terminalFound) {
            return true;
        }

        buffer.append(chunk.toString());

        int lineEnd;
        while ((lineEnd = findLineEnd(buffer)) >= 0) {
            String line = readLine(buffer, lineEnd);

            if (line.isEmpty()) {
                var event = buildEvent();
                resetEvent();

                if (event != null) {
                    events.add(event);

                    if (terminalEventNames.contains(event.name())) {
                        terminalFound = true;
                        return true;
                    }
                }

                continue;
            }

            parseLine(line);
        }

        return false;
    }

    public List<SSEEvent> getEvents() {
        return List.copyOf(events);
    }

    public Optional<SSEEvent> getTerminalEvent() {
        if (!terminalFound || events.isEmpty()) {
            return Optional.empty();
        }

        return Optional.of(events.getLast());
    }

    private SSEEvent buildEvent() {
        if (eventName == null && data.isEmpty()) {
            return null;
        }

        return new SSEEvent(eventName, data.isEmpty() ? null : data.toString());
    }

    private void resetEvent() {
        eventName = null;
        data.setLength(0);
    }

    private void parseLine(String line) {
        if (line.startsWith(":")) {
            return;
        }

        int colon = line.indexOf(':');

        String field = colon >= 0 ? line.substring(0, colon) : line;
        String value = colon >= 0 ? line.substring(colon + 1) : "";

        if (value.startsWith(" ")) {
            value = value.substring(1);
        }

        switch (field) {
            case "event" -> eventName = value;

            case "data" -> {
                if (!data.isEmpty()) {
                    data.append('\n');
                }
                data.append(value);
            }

            default -> {
                // ignore id, retry, unknown fields
            }
        }
    }

    private static int findLineEnd(StringBuilder buffer) {
        for (int i = 0; i < buffer.length(); i++) {
            char c = buffer.charAt(i);
            if (c == '\n' || c == '\r') {
                return i;
            }
        }
        return -1;
    }

    private static String readLine(StringBuilder buffer, int lineEnd) {
        String line = buffer.substring(0, lineEnd);

        int removeUntil = lineEnd + 1;

        if (lineEnd + 1 < buffer.length()
                && buffer.charAt(lineEnd) == '\r'
                && buffer.charAt(lineEnd + 1) == '\n') {
            removeUntil++;
        }

        buffer.delete(0, removeUntil);
        return line;
    }

    public record SSEEvent(String name, String data) {}
}