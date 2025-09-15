package com.predic8.membrane.core.kubernetes;

import com.predic8.membrane.core.openapi.serviceproxy.APIProxy;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.events.Event;

import java.io.StringReader;
import java.util.Iterator;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GenericYamlParserTest {
    @Test
    void parseSimpleApi() {
        String yaml = """
          port: 2000
          target:
            url: https://api.predic8.de
        """;

        APIProxy r = GenericYamlParser.parse("api", APIProxy.class, events(yaml), null);

        assertEquals(2000, r.getPort());
        assertEquals("https://api.predic8.de", r.getTarget().getUrl());
    }




    private static Iterator<Event> events(String yaml) {
        Iterable<Event> iterable = new Yaml().parse(new StringReader(yaml));
        java.util.List<Event> filtered = new java.util.ArrayList<>();
        for (Event e : iterable) {
            if (e instanceof org.yaml.snakeyaml.events.StreamStartEvent
                    || e instanceof org.yaml.snakeyaml.events.DocumentStartEvent) {
                continue;
            }
            filtered.add(e);
        }
        return filtered.iterator();
    }

}
