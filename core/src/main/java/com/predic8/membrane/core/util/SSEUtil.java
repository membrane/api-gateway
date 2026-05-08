package com.predic8.membrane.core.util;

import com.predic8.membrane.core.http.Chunk;

/**
 * Util for Server Sent Events.
 */
public class SSEUtil {

    private SSEUtil() {}

    public record SSEvent(String name, String data) {}

    public static SSEvent parseSSEvent(Chunk chunk) {
        var content = chunk.toString();
        String event = null;
        String data = null;

        for (var line : content.split("\n")) {
            line = line.trim();
            if (line.startsWith("name:")) {
                event = line.substring("name:".length()).trim();
            } else if (line.startsWith("data:")) {
                data = line.substring("data:".length()).trim();
            }
        }

        if (event == null && data == null) return null;
        return new SSEvent(event, data);
    }
}
