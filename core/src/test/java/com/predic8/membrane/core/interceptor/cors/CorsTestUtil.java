package com.predic8.membrane.core.interceptor.cors;

import com.predic8.membrane.core.http.*;
import org.junit.jupiter.api.*;

import java.util.*;

import static java.util.Arrays.*;
import static java.util.stream.Collectors.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.http.HttpHeaders.*;

public class CorsTestUtil {

    private static final String ACCESS_CONTROL_ALLOW_PREFIX = "access-control-allow";

    public static Set<String> getAccessControlAllowHeaderNames(Header header) {
        return stream(header.getAllHeaderFields())
                .map(hf -> hf.getHeaderName().getName().toLowerCase())
                .filter(n -> n.startsWith(ACCESS_CONTROL_ALLOW_PREFIX))
                .collect(toSet());
    }

    @Test
    void testGetAccessControlAllowHeaderNames() {
        Header h = new Header();
        h.add(ACCESS_CONTROL_ALLOW_ORIGIN, "http://example.com");
        h.add(ACCESS_CONTROL_ALLOW_METHODS, "POST");
        h.add("Foo", "Bar");

        Set<String> names = getAccessControlAllowHeaderNames(h);
        assertEquals(2,names.size());
        assertEquals(Set.of(ACCESS_CONTROL_ALLOW_METHODS.toLowerCase(), ACCESS_CONTROL_ALLOW_ORIGIN.toLowerCase()),
                names);
    }
}
