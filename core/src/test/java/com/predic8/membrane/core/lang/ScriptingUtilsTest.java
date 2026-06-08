/* Copyright 2026 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */

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
            assertEquals(
                    7,
                    assertInstanceOf(
                            Integer.class,
                            createBinding("7").get("json")
                    )
            );
        }

        private static @NotNull Map<String, Object> createBinding(String json) throws URISyntaxException {
            var bindings = ScriptingUtils.createParameterBindings(new DefaultRouter(), get("/foo").json(json).buildExchange(), REQUEST, true, null);
            assertNotNull(bindings);
            return bindings;
        }
    }

}
