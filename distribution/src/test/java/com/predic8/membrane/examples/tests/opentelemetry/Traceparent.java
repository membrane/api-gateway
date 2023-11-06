package com.predic8.membrane.examples.tests.opentelemetry;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Traceparent {

    private static final Pattern pattern = Pattern.compile("traceparent: (.*)-(.*)-(.*)-(.*)");
    public String version;
    public String traceId;
    public String parentId;
    public String flags;

    public Traceparent(String version, String traceId, String parentId, String flags) {
        this.version = version;
        this.traceId = traceId;
        this.parentId = parentId;
        this.flags = flags;
    }

    public static List<Traceparent> parse(String s) {

        Matcher m = pattern.matcher(s);

        List<Traceparent> l = new ArrayList<>();
        while (m.find()) {
            Traceparent traceparent = new Traceparent(m.group(1), m.group(2), m.group(3), m.group(4));
            l.add(traceparent);
        }

        return l;
    }

    public boolean sameTraceId(Traceparent t) {
        return traceId.equals(t.traceId);
    }
}
