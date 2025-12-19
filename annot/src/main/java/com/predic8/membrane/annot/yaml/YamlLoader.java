/* Copyright 2025 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */

package com.predic8.membrane.annot.yaml;

import org.jetbrains.annotations.NotNull;
import org.yaml.snakeyaml.events.*;

import java.util.*;

public class YamlLoader {

    public static String readString(Iterator<Event> events) {
        Event event = events.next();
        if (event instanceof ScalarEvent se)
            return getValue(se);
        throw new IllegalStateException("Expected string in line " + event.getStartMark().getLine() + " column " + event.getStartMark().getColumn());
    }

    private static String getValue(ScalarEvent se) {
        String value = se.getValue();
        // remove the last newline (if present)
        if (value.endsWith("\n")) {
            value = value.substring(0, value.length() - 1);
        }
        return value;
    }

    public static Object readObj(Iterator<Event> events) {
        Event event = events.next();
        if (event instanceof ScalarEvent se)
            return se.getValue();
        if (event instanceof MappingStartEvent)
            return readMap(events);
        if (event instanceof SequenceStartEvent)
            return readSequence(events);

        throw new IllegalStateException(parsingErrorMessage("Expected scalar, map or sequence in line ", event));
    }

    public static List readSequence(Iterator<Event> events) {
        List res = new ArrayList();
        while (true) {
            Event event = events.next();
            if (event instanceof ScalarEvent se) {
                res.add(se.getValue());
            } else if (event instanceof MappingStartEvent) {
                res.add(readMap(events));
            } else if (event instanceof SequenceStartEvent) {
                res.add(readSequence(events));
            } else if (event instanceof SequenceEndEvent) {
                break;
            } else {
                throw new IllegalStateException(parsingErrorMessage("Expected scalar or end-of-map in line ", event));
            }
        }
        return res;
    }

    public static Map<String,Object> readMap(Iterator<Event> events) {
        Map<String,Object> res = new TreeMap<>();
        while (true) {
            Event event = events.next();
            if (event instanceof ScalarEvent se) {
                res.put(se.getValue(), readObj(events));
            } else if (event instanceof MappingEndEvent) {
                break;
            } else {
                throw new IllegalStateException(parsingErrorMessage("Expected scalar or end-of-map in line ", event));
            }
        }
        return res;
    }

    private static @NotNull String parsingErrorMessage(String x, Event event) {
        return x + event.getStartMark().getLine() + " column " + event.getStartMark().getColumn();
    }
}
