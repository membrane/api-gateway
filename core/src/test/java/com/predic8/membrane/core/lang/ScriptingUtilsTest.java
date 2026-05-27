package com.predic8.membrane.core.lang;

import com.predic8.membrane.core.router.DefaultRouter;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.net.URISyntaxException;
import java.util.List;
import java.util.Map;

import static com.predic8.membrane.core.http.Request.get;
import static com.predic8.membrane.core.interceptor.Interceptor.Flow.REQUEST;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class ScriptingUtilsTest {

    @Nested
    class json {

        @Test
        void map() throws URISyntaxException {
            assertEquals(
                    Map.of("foo", 1),
                    assertInstanceOf(Map.class, createBinding("""
                            {"foo":1}""").get("json"))
            );
        }

        @Test
        void array() throws URISyntaxException {
            assertEquals(
                    List.of(1, 2, 3),
                    assertInstanceOf(List.class, createBinding("[1,2,3]").get("json"))
            );
        }

        @Test
        void string() throws URISyntaxException {
            assertEquals(
                    "foo",
                    assertInstanceOf(String.class, createBinding("""
                            "foo"
                            """).get("json"))
            );
        }

        @Test
        void number() throws URISyntaxException {
            assertInstanceOf(
                    Integer.class,
                    createBinding("7").get("json")
            );
        }

        private static @NotNull Map<String, Object> createBinding(String json) throws URISyntaxException {
            var bindings = ScriptingUtils.createParameterBindings(new DefaultRouter(), get("/foo").json(json).buildExchange(), REQUEST, true, null);
            assertNotNull(bindings);
            return bindings;
        }
    }

}
