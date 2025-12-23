/* Copyright 2025 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */
package com.predic8.membrane.annot;

import com.predic8.membrane.annot.util.CompilerHelper;
import com.predic8.membrane.annot.beanregistry.BeanRegistry;
import com.predic8.membrane.annot.yaml.YamlSchemaValidationException;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.List;

import static com.predic8.membrane.annot.SpringConfigurationXSDGeneratingAnnotationProcessorTest.MC_MAIN_DEMO;
import static com.predic8.membrane.annot.util.CompilerHelper.*;
import static org.junit.jupiter.api.Assertions.*;

public class YAMLBeanParsingTest {

    @Test
    void beanComponentIsInstantiatedAndInjectedViaRefInList() {
        BeanRegistry r = parse("""
            components:
              dep:
                bean:
                  class: com.predic8.membrane.demo.Dep
              myBean:
                bean:
                  class: com.predic8.membrane.demo.MyBean
                  scope: singleton
                  constructorArgs:
                    - constructorArg: { value: "8080" }
                    - constructorArg: { ref: "#/components/dep" }
                  properties:
                    - property: { name: "name", value: "abc" }
                    - property: { name: "l", value: "7" }
                    - property: { name: "d", value: "1.5" }
            ---
            holder:
              items:
                - $ref: "#/components/myBean"
            """);

        Object holder = firstBeanBySimpleName(r, "HolderElement");
        @SuppressWarnings("unchecked")
        List<Object> items = (List<Object>) call(holder, "getItems");

        assertEquals(1, items.size());
        Object myBean = items.getFirst();

        assertEquals("MyBean", myBean.getClass().getSimpleName());
        assertEquals(8080, ((Number) call(myBean, "getPort")).intValue());
        assertEquals("abc", call(myBean, "getName"));
        assertEquals(7L, ((Number) call(myBean, "getL")).longValue());
        assertEquals(1.5d, ((Number) call(myBean, "getD")).doubleValue());
        assertNotNull(call(myBean, "getDep"));
        assertEquals("Dep", call(myBean, "getDep").getClass().getSimpleName());
    }

    @Test
    void singletonResolveReference() {
        BeanRegistry r = parse("""
            components:
              s:
                bean:
                  class: com.predic8.membrane.demo.Counting
                  scope: singleton
            """);

        Object s1 = r.resolveReference("#/components/s");
        Object s2 = r.resolveReference("#/components/s");
        assertSame(s1, s2, "singleton must return same instance");
        assertEquals(call(s1, "getId"), call(s2, "getId"));
    }

    @Test
    void prototypeResolveReference() {
        BeanRegistry r = parse("""
            components:
              p:
                bean:
                  class: com.predic8.membrane.demo.Counting
                  scope: prototype
            """);

        Object p1 = r.resolveReference("#/components/p");
        Object p2 = r.resolveReference("#/components/p");
        assertNotSame(p1, p2, "prototype must return new instance");
        assertNotEquals(call(p1, "getId"), call(p2, "getId"));
    }

    @Test
    void missingClassFailsFastOnResolve() {
        BeanRegistry r = parse("""
            components:
              x:
                bean:
                  scope: singleton
            """);

        var ex = assertThrows(RuntimeException.class, () -> r.resolveReference("#/components/x"));
        assertAnyErrorContains(ex, "Missing/blank 'class'");
    }

    @Test
    void unknownBeanPropertyIsSchemaError() {
        var ex = assertThrows(RuntimeException.class, () -> parse("""
            components:
              x:
                bean:
                  class: com.predic8.membrane.demo.Dep
                  doesNotExist: 1
            """));
        assertSchemaErrorContains(ex, "doesNotExist", "is not defined in the schema and the schema does not allow additional properties");
    }


    private BeanRegistry parse(String yaml) {
        var sources = splitSources(MC_MAIN_DEMO + BEAN_DEMO_SOURCES);
        var result = CompilerHelper.compile(sources, false);
        assertCompilerResult(true, result);
        return parseYAML(result, yaml);
    }

    private static Object firstBeanBySimpleName(BeanRegistry r, String simpleName) {
        return r.getBeans().stream()
                .filter(b -> b != null && b.getClass().getSimpleName().equals(simpleName))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Bean not found: " + simpleName));
    }

    private static Object call(Object target, String method) {
        try {
            Method m = target.getClass().getMethod(method);
            m.setAccessible(true);
            return m.invoke(target);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void assertSchemaErrorContains(RuntimeException ex, String... needles) {
        var root = getRootCause(ex);
        if (!(root instanceof YamlSchemaValidationException yse))
            throw new AssertionError("Expected YamlSchemaValidationException but got: " + root, root);

        assertFalse(yse.getErrors().isEmpty(), "Expected schema errors.");

        for (var n : needles) {
            boolean found = yse.getErrors().stream().anyMatch(err -> {
                String msg = err.getMessage();
                String s1 = msg != null ? msg : "";
                String s2 = err.toString();
                return s1.contains(n) || s2.contains(n);
            });
            assertTrue(found, () -> "Expected schema error to contain '" + n + "' but was: " + yse.getErrors());
        }
    }

    private void assertAnyErrorContains(RuntimeException ex, String... needles) {
        var root = getRootCause(ex);
        var msg = root.getMessage() != null ? root.getMessage() : root.toString();
        for (var n : needles)
            assertTrue(msg.toLowerCase().contains(n.toLowerCase()),
                    () -> "Expected error to contain '" + n + "' but was: " + msg);
    }

    private Throwable getRootCause(Throwable e) {
        return (e.getCause() == null) ? e : getRootCause(e.getCause());
    }

    private static final String BEAN_DEMO_SOURCES = """
        ---
        package com.predic8.membrane.demo;
        import com.predic8.membrane.annot.*;
        import java.util.List;

        @MCElement(name="holder", topLevel=true, component=false)
        public class HolderElement {
            private List<Object> items;

            public List<Object> getItems() { return items; }

            @MCChildElement
            public void setItems(List<Object> items) { this.items = items; }
        }
        ---
        package com.predic8.membrane.demo;
        public class Dep {}
        ---
        package com.predic8.membrane.demo;

        public class MyBean {
            private final int port;
            private final Dep dep;
            private String name;
            private long l;
            public double d;

            public MyBean(int port, Dep dep) {
                this.port = port;
                this.dep = dep;
            }

            public int getPort() { return port; }
            public Dep getDep() { return dep; }

            public String getName() { return name; }
            public void setName(String name) { this.name = name; }

            public long getL() { return l; }
            private void setL(long l) { this.l = l; }

            public double getD() { return d; }
        }
        ---
        package com.predic8.membrane.demo;

        import java.util.concurrent.atomic.AtomicInteger;

        public class Counting {
            private static final AtomicInteger C = new AtomicInteger();
            private final int id = C.incrementAndGet();
            public int getId() { return id; }
        }
        ---
        package com.predic8.membrane.demo;
        import com.predic8.membrane.annot.*;
        @MCElement(name="child")
        public class Child2Element {
        }
        """;
}
