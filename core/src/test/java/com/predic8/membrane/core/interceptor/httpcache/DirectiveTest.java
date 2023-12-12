package com.predic8.membrane.core.interceptor.httpcache;

import org.junit.jupiter.api.Test;

import java.util.LinkedList;

import static com.predic8.membrane.core.interceptor.httpcache.Directives.*;
import static org.junit.jupiter.api.Assertions.*;

class DirectiveTest {

    @Test
    void parseWithoutValue() {
        Directive directive = new Directive(NO_STORE, null);
        Directive parsed = Directive.parse("no-store");

        assertEquals(directive, parsed);
    }

    @Test
    void parseWithIntValue() {
        Directive directive = new Directive(MAX_AGE, new DirectiveArgument<>(80000));
        Directive parsed = Directive.parse("max-age=80000");

        assertEquals(directive, parsed);
    }

    @Test
    void parseWithListValue() {
        LinkedList<String> args = new LinkedList<>();
        args.add("Content-Type");
        args.add("Length");

        Directive directive = new Directive(PRIVATE, new DirectiveArgument<>(args));
        Directive parsed = Directive.parse("private= \"Content-Type, Length\"");

        assertEquals(directive, parsed);
    }
}