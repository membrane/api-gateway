package com.predic8.membrane.annot.yaml;

import org.jetbrains.annotations.NotNull;
import org.yaml.snakeyaml.events.*;

import java.util.*;

public class YamlLoader {

    public static String readString(Iterator<Event> events) {
        Event event = events.next();
        if (event instanceof ScalarEvent se)
            return se.getValue();
        throw new IllegalStateException("Expected string in line " + event.getStartMark().getLine() + " column " + event.getStartMark().getColumn());
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
