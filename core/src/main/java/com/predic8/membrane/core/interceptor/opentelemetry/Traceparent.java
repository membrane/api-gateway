package com.predic8.membrane.core.interceptor.opentelemetry;

import java.util.*;
import java.util.regex.*;

public class Traceparent {

    public String version;
    public String traceId;
    public   String parentId;
    public String flags;

    public Traceparent(String version, String traceId, String parentId, String flags) {
        this.version = version;
        this.traceId = traceId;
        this.parentId = parentId;
        this.flags = flags;
    }

    private static final Pattern pattern = Pattern.compile("traceparent: (.*)-(.*)-(.*)-(.*)");

    public static List<Traceparent> parse(String s) {

        Matcher m = pattern.matcher(s);

        List<Traceparent> l = new ArrayList<>();
        while (m.find()) {
            Traceparent traceparent = new Traceparent(m.group(1), m.group(2), m.group(3), m.group(4));
            l.add(traceparent);
        }

        return l;
    }
}
