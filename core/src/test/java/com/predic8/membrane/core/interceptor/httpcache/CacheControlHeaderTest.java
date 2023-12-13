package com.predic8.membrane.core.interceptor.httpcache;

import com.predic8.membrane.core.http.Header;
import com.predic8.membrane.core.util.EndOfStreamException;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.LinkedList;

import static com.predic8.membrane.core.interceptor.httpcache.Directives.*;
import static org.junit.jupiter.api.Assertions.*;

class CacheControlHeaderTest {

    private final Header header = new Header("Cache-Control: max-age=80000, no-store");
    private final CacheControlHeader cch = CacheControlHeader.parseHeader(header);

    CacheControlHeaderTest() throws EndOfStreamException, IOException {}

    @Test
    void getDirectiveArgument() {
        assertEquals(80000, cch.getDirectiveArgument(MAX_AGE).get().getValue());
    }

    @Test
    void directiveHasArgument() {
        assertTrue(cch.directiveHasArgument(MAX_AGE));
        assertFalse(cch.directiveHasArgument(NO_STORE));
    }

    @Test
    void hasDirective() {
        assertTrue(cch.hasDirective(MAX_AGE));
        assertFalse(cch.hasDirective(S_MAXAGE));
    }

    @Test
    void parseDirectives() {
        final String rawDirectives = "max-age=80000, no-store";
        LinkedList<Directive> directives = CacheControlHeader.parseDirectives(rawDirectives);
        LinkedList<Directive> testDirectives = new LinkedList<>();
        testDirectives.add(new Directive(MAX_AGE, new DirectiveArgument<>(80000)));
        testDirectives.add(new Directive(NO_STORE));

        assertEquals(testDirectives, directives);
    }

    @Test
    void httpElementToList() {
        String element = "test, Test = \"DEMO, Demo, demo\", TEST";
        LinkedList<String> list = new LinkedList<>();
        list.add("test");
        list.add("Test = \"DEMO, Demo, demo\"");
        list.add("TEST");

        assertEquals(list, CacheControlHeader.httpElementToList(element));
    }
}