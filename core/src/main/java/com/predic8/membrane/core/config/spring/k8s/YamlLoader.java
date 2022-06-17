/* Copyright 2022 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */
package com.predic8.membrane.core.config.spring.k8s;

import com.google.common.io.Resources;
import com.predic8.membrane.core.kubernetes.BeanRegistry;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.events.*;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class YamlLoader {
    public Envelope loadResource(String resource, BeanRegistry registry) throws IOException {
        BufferedReader br = Resources.asCharSource(Resources.getResource(resource), StandardCharsets.UTF_8).openBufferedStream();
        return load(br, registry);
    }

    public Envelope load(Reader reader, BeanRegistry registry) throws IOException {
        Yaml yaml = new Yaml();
        Iterable<Event> iterable = yaml.parse(reader);
        Iterator<Event> i = iterable.iterator();

        Envelope e = new Envelope();
        e.parse(i, registry);
        return e;
    }

    public static String readString(Iterator<Event> events) {
        Event event = events.next();
        if (event instanceof ScalarEvent)
            return ((ScalarEvent)event).getValue();
        throw new IllegalStateException("Expected string in line " + event.getStartMark().getLine() + " column " + event.getStartMark().getColumn());
    }

    public static Object readObj(Iterator<Event> events) {
        while (true) {
            Event event = events.next();
            if (event instanceof ScalarEvent)
                return ((ScalarEvent)event).getValue();
            else if (event instanceof MappingStartEvent)
                return readMap(events);
            else if (event instanceof SequenceStartEvent)
                return readSequence(events);
            else
                throw new IllegalStateException("Expected scalar, map or sequence in line " + event.getStartMark().getLine() + " column " + event.getStartMark().getColumn());
        }
    }

    public static List readSequence(Iterator<Event> events) {
        List res = new ArrayList();
        while (true) {
            Event event = events.next();
            if (event instanceof ScalarEvent) {
                String value = ((ScalarEvent) event).getValue();
                res.add(value);
            } else if (event instanceof MappingStartEvent) {
                res.add(readMap(events));
            } else if (event instanceof SequenceStartEvent) {
                res.add(readSequence(events));
            } else if (event instanceof SequenceEndEvent) {
                break;
            } else {
                throw new IllegalStateException("Expected scalar or end-of-map in line " + event.getStartMark().getLine() + " column " + event.getStartMark().getColumn());
            }
        }
        return res;
    }

    public static Map readMap(Iterator<Event> events) {
        Map res = new HashMap();
        while (true) {
            Event event = events.next();
            if (event instanceof ScalarEvent) {
                String key = ((ScalarEvent) event).getValue();
                Object value = readObj(events);
                res.put(key, value);
            } else if (event instanceof MappingEndEvent) {
                break;
            } else {
                throw new IllegalStateException("Expected scalar or end-of-map in line " + event.getStartMark().getLine() + " column " + event.getStartMark().getColumn());
            }
        }
        return res;
    }
}
