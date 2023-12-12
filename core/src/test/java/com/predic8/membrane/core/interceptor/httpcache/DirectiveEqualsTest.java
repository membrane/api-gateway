package com.predic8.membrane.core.interceptor.httpcache;

import org.junit.jupiter.api.Test;
import static com.predic8.membrane.core.interceptor.httpcache.Directives.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

public class DirectiveEqualsTest {

        @Test
        void testEqualsSelf() {
            Directive dir = new Directive(MAX_AGE);

            assertEquals(dir, dir);
        }

        @Test
        void testEqualsNoArg() {
            Directive dir = new Directive(PRIVATE);
            Directive dir2 = new Directive(PRIVATE);

            assertEquals(dir, dir2);
        }

        @Test
        void testEqualsArg() {
            Directive dir = new Directive(PRIVATE, new DirectiveArgument<>(1));
            Directive dir2 = new Directive(PRIVATE, new DirectiveArgument<>(1));

            assertEquals(dir, dir2);
        }

        @Test
        void testNotEqualsNoArg() {
            Directive dir = new Directive(PUBLIC);
            Directive dir2 = new Directive(PRIVATE);

            assertNotEquals(dir, dir2);
        }

        @Test
        void testNotEqualsArg() {
            Directive dir = new Directive(PUBLIC, new DirectiveArgument<>(1));
            Directive dir2 = new Directive(PUBLIC, new DirectiveArgument<>(2));

            assertNotEquals(dir, dir2);
        }

        @Test
        void testNotEqualsNull() {
            Directive dir = new Directive(NO_CACHE);

            assertNotEquals(dir, null);
        }

        @Test
        void testNotEqualsWrongClass() {
            Directive dir = new Directive(STALE_IF_ERROR);
            String dir2 = "";

            assertNotEquals(dir, dir2);
        }
    }