package com.predic8.membrane.core.interceptor.headerfilter;

import com.predic8.membrane.core.http.*;
import org.junit.jupiter.api.*;

import java.util.*;

import static com.predic8.membrane.core.interceptor.headerfilter.HeaderFilterRule.*;
import static org.junit.jupiter.api.Assertions.*;

class HeaderFilterTest {

    @Test
    void removeAllUnknown() {

        var header = new Header();
        header.add("X-Foo", "foo");
        header.add("X-Bar", "bar");
        header.add("X-Baz", "baz");

        var hf = new HeaderFilter();

        hf.setRules(List.of(
                keep("X-Foo"),
                keep("X-Bar"),
                remove(".*")));

        hf.filter(header);

        var fields = header.getFields();
        assertEquals(2, fields.size());
        assertEquals(List.of("X-Foo", "X-Bar"), headerNames(header));
    }

    @Test
    void repeatedFields() {

        var header = new Header();
        header.add("X-Foo", "foo");
        header.add("X-Bar", "bar");
        header.add("X-Bar", "bar");
        header.add("X-Bar", "bar");
        header.add("X-Baz", "baz");

        var hf = new HeaderFilter();

        hf.setRules(List.of(
                keep("X-Foo"),
                keep("X-Bar"),
                remove(".*")));

        hf.filter(header);

        var fields = header.getFields();
        assertEquals(4, fields.size());
        assertEquals(List.of("X-Foo", "X-Bar","X-Bar","X-Bar"), headerNames(header));
    }

    private List<String> headerNames(Header header) {
        return header.getFields().stream().map(hf -> hf.getHeaderName().getName()).toList();
    }

}