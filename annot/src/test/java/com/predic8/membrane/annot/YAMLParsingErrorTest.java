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

import com.predic8.membrane.annot.util.*;
import com.predic8.membrane.annot.yaml.*;
import com.predic8.membrane.common.*;
import org.jetbrains.annotations.*;
import org.junit.jupiter.api.*;

import java.util.Map;

import static com.predic8.membrane.annot.SpringConfigurationXSDGeneratingAnnotationProcessorTest.*;
import static com.predic8.membrane.annot.util.CompilerHelper.*;
import static org.junit.jupiter.api.Assertions.*;

public class YAMLParsingErrorTest {

    @Test
    void topLevelWrong() {
        var sources = splitSources(MC_MAIN_DEMO + """
                package com.predic8.membrane.demo;
                import com.predic8.membrane.annot.*;
                @MCElement(name="demo", topLevel=true, component=false)
                public class DemoElement {
                }
                """);
        var result = compile(sources, false);
        assertCompilerResult(true, result);

        try {
            parseYAML(result, """
                    wrong: {}
                    """);
            fail();
        } catch (RuntimeException e) {
            var pc = getCause(e).getParsingContext();
            assertEquals("$", pc.getPath());
            assertEquals("wrong", pc.getKey());
        }
    }

    @Test
    void secondLevelWrongField() {
        var sources = splitSources(MC_MAIN_DEMO + """
                package com.predic8.membrane.demo;
                import com.predic8.membrane.annot.*;
                @MCElement(name="demo", topLevel=true, component=false)
                public class DemoElement {
                }
                """);
        var result = compile(sources, false);
        assertCompilerResult(true, result);

        try {
            parseYAML(result, """
                    demo:
                        wrong: {}
                    """);
            fail();
        } catch (RuntimeException e) {
            var c = getCause(e);
            var pc = c.getParsingContext();
            assertEquals("$.demo", pc.getPath());
            assertEquals("wrong", pc.getKey());
        }
    }

    @Test
    void attribute() {
        var sources = splitSources(MC_MAIN_DEMO + """
                package com.predic8.membrane.demo;
                import com.predic8.membrane.annot.*;
                @MCElement(name="demo", topLevel=true, component=false)
                public class DemoElement {
                    @MCAttribute
                    public void setAttr(int attr) {
                    }
                }
                """);
        var result = compile(sources, false);
        assertCompilerResult(true, result);

        try {
            parseYAML(result, """
                    demo:
                        attr: "no a number"
                    """);
            fail();
        } catch (RuntimeException e) {
            var c = getCause(e);
            var pc = c.getParsingContext();
            assertEquals("$.demo", pc.getPath());
            assertEquals("attr", pc.getKey());
        }

        try {
            parseYAML(result, """
                    demo:
                        attr: 2
                        wrong: 1
                    """);
            fail();
        } catch (RuntimeException e) {
            var c = getCause(e);
            var pc = c.getParsingContext();
            assertEquals("$.demo", pc.getPath());
            assertEquals("wrong", pc.getKey());
        }
    }

    @Test
    void noEnvelope() {
        var sources = splitSources(MC_MAIN_DEMO + """
                package com.predic8.membrane.demo;
                import com.predic8.membrane.annot.*;
                import java.util.List;
                @MCElement(name="demo", noEnvelope=true, topLevel=true, component=false)
                public class DemoElement {
                    @MCChildElement
                    public void setChildren(List<Child1Element> children) {
                
                    }
                }
                ---
                package com.predic8.membrane.demo;
                import com.predic8.membrane.annot.*;
                @MCElement(name="child1")
                public class Child1Element {
                
                    @MCAttribute
                    public void setAttr(int attr) {
                    }
                }
                """);
        var result = compile(sources, false);
        assertCompilerResult(true, result);

        try {
            parseYAML(result, """
                    demo:
                        - attr: foo
                        - attr: 6
                    """);
            fail();
        } catch (RuntimeException e) {
            var c = getCause(e);
            var pc = c.getParsingContext();
            assertEquals("$.demo[0]", pc.getPath());
            assertEquals("attr", pc.getKey());
        }

        try {
            parseYAML(result, """
                    demo:
                        foo: 2
                    """);
            fail();
        } catch (RuntimeException e) {
            var c = getCause(e);
            var pc = c.getParsingContext();
            assertEquals("$.demo", pc.getPath());
            assertNull(pc.getKey());
        }
    }

    @Test
    void requiredChild() {
        var sources = splitSources(MC_MAIN_DEMO + """
                package com.predic8.membrane.demo;
                import com.predic8.membrane.annot.*;
                @MCElement(name="demo", topLevel=true, component=false)
                public class DemoElement {
                    @Required
                    @MCChildElement
                    public void setChild(ChildElement child) {
                    }
                }
                ---
                package com.predic8.membrane.demo;
                public abstract class ChildElement {
                }
                ---
                package com.predic8.membrane.demo;
                import com.predic8.membrane.annot.*;
                @MCElement(name="a")
                public class Child1Element extends ChildElement {
                }
                ---
                package com.predic8.membrane.demo;
                import com.predic8.membrane.annot.*;
                @MCElement(name="b")
                public class Child2Element extends ChildElement {
                }
                """);
        var result = CompilerHelper.compile(sources, false);
        assertCompilerResult(true, result);

        try {
            parseYAML(result, """
                    demo: {}
                    """);
            fail();
        } catch (RuntimeException e) {
            var c = getCause(e);
            var pc = c.getParsingContext();
            assertEquals("$.demo", pc.getPath());
            assertNull(pc.getKey());
        }

        try {
            parseYAML(result, """
                    demo:
                        a: {}
                        b: {}
                        c: {}
                    """);
            fail();
        } catch (RuntimeException e) {
            var c = getCause(e);
            var pc = c.getParsingContext();
            assertEquals("$.demo", pc.getPath());
            assertEquals("c", pc.getKey());
        }
    }

    @Test
    void listItem() {
        var sources = splitSources(MC_MAIN_DEMO + """
                package com.predic8.membrane.demo;
                import com.predic8.membrane.annot.*;
                import java.util.List;
                @MCElement(name="demo")
                public class DemoElement {
                    List<ChildElement> children;
                
                    public List<ChildElement> getChildren() {
                        return children;
                    }
                
                    @MCChildElement
                    public void setChildren(List<ChildElement> children) {
                        this.children = children;
                    }
                }
                ---
                package com.predic8.membrane.demo;
                public abstract class ChildElement {
                }
                ---
                package com.predic8.membrane.demo;
                import com.predic8.membrane.annot.*;
                @MCElement(name="a")
                public class Child1Element extends ChildElement {
                }
                ---
                package com.predic8.membrane.demo;
                import com.predic8.membrane.annot.*;
                @MCElement(name="b")
                public class Child2Element extends ChildElement {
                }
                """);
        var result = CompilerHelper.compile(sources, false);
        assertCompilerResult(true, result);

        try {
            parseYAML(result, """
                    demo:
                        children:
                           - a: {}
                           - b: {}
                           - c: {}
                    """);
            fail();
        } catch (RuntimeException e) {
            var c = getCause(e);
            var pc = c.getParsingContext();
            assertEquals("$.demo.children[2].c", pc.getPath());
            assertNull(pc.getKey());
        }

        try {
            parseYAML(result, """
                    demo:
                        children:
                           wrong: 1
                    """);
            fail();
        } catch (RuntimeException e) {
            var c = getCause(e);
            var pc = c.getParsingContext();
            assertEquals("$.demo.children", pc.getPath());
            assertNull(pc.getKey());
        }
    }

    @Test
    void noEnvelopeListItemWithNonListChild() {
        var sources = splitSources(MC_MAIN_DEMO + """
                package com.predic8.membrane.demo;
                import com.predic8.membrane.annot.*;
                import java.util.List;
                
                @MCElement(name="demo", noEnvelope=true, topLevel=true, component=false)
                public class DemoElement {
                    List<Child1Element> children;
                
                    public List<Child1Element> getChildren() { return children; }
                
                    @MCChildElement
                    public void setChildren(List<Child1Element> children) { this.children = children; }
                }
                ---
                package com.predic8.membrane.demo;
                import com.predic8.membrane.annot.*;
                
                @MCElement(name="child1", component=false)
                public class Child1Element {
                    String attr;
                    ValidatorElement validator;
                
                    public String getAttr() { return attr; }
                    public ValidatorElement getValidator() { return validator; }
                
                    @MCAttribute
                    public void setAttr(String attr) { this.attr = attr; }
                
                    @MCChildElement
                    public void setValidator(ValidatorElement validator) { this.validator = validator; }
                }
                ---
                package com.predic8.membrane.demo;
                import com.predic8.membrane.annot.*;
                
                @MCElement(name="validator", component=false)
                public class ValidatorElement {
                    String type;
                
                    public String getType() { return type; }
                
                    @MCAttribute
                    public void setType(String type) { this.type = type; }
                }
                """);

        var result = CompilerHelper.compile(sources, false);
        assertCompilerResult(true, result);

        try {
            parseYAML(result, """
                    demo:
                      - attr: here
                        validator:
                          type: regex
                      - attr: here2
                        validator:
                          type: notNull
                          wrong: 1
                    """);
            fail();
        } catch (RuntimeException e) {
            var c = getCause(e);
            var pc = c.getParsingContext();
            assertEquals("$.demo[1].validator", pc.getPath());
            assertEquals("wrong", pc.getKey());
        }

        try {
            parseYAML(result, """
                    demo:
                      - attr: here
                        wrong: 1
                        validator:
                          type: regex
                    """);
            fail();
        } catch (RuntimeException e) {
            var c = getCause(e);
            var pc = c.getParsingContext();
            assertEquals("$.demo[0]", pc.getPath());
            assertEquals("wrong", pc.getKey());
        }

        try {
            parseYAML(result, """
                    demo:
                        wrong: 1
                    """);
            fail();
        } catch (RuntimeException e) {
            var c = getCause(e);
            var pc = c.getParsingContext();
            assertEquals("$.demo", pc.getPath());
            assertNull(pc.getKey());
        }
    }

    @Test
    void otherAttributesKeyWithDotWrongFieldRendersErrorReport() throws Exception {
        var result = compileMethodMapSources();

        try {
            parseYAML(result, """
                    api:
                      methods:
                        'rpc.echo':
                          wrong: 1
                    """);
            fail();
        } catch (RuntimeException e) {
            var c = getCause(e);
            var pc = c.getParsingContext();
            assertEquals("$.api.methods['rpc.echo']", pc.getPath());
            assertEquals("wrong", pc.getKey());

            String report = c.getFormattedReport();
            assertTrue(report.contains("rpc.echo"));
            assertTrue(report.contains("wrong"));
        }
    }

    @Test
    void otherAttributesKeyWithQuoteWrongFieldRendersErrorReport() throws Exception {
        var result = compileMethodMapSources();

        try {
            parseYAML(result, """
                    api:
                      methods:
                        "rpc'echo":
                          wrong: 1
                    """);
            fail();
        } catch (RuntimeException e) {
            var c = getCause(e);
            var pc = c.getParsingContext();
            assertEquals("$.api.methods['rpc\\'echo']", pc.getPath());
            assertEquals("wrong", pc.getKey());

            String report = c.getFormattedReport();
            assertTrue(report.contains("rpc'echo"));
            assertTrue(report.contains("wrong"));
        }
    }

    @Test
    void otherAttributesMapValueUsesLocalContextForChildren() throws Exception {
        var result = compileMethodMapSources();

        var registry = parseYAML(result, """
                api:
                  methods:
                    'rpc.echo':
                      params:
                        location: tmp.schema.json
                """);

        Object api = registry.getBeans().stream()
                .filter(bean -> bean.getClass().getSimpleName().equals("ApiElement"))
                .findFirst()
                .orElseThrow();

        Object methodDefinitions = api.getClass().getMethod("getMethods").invoke(api);
        @SuppressWarnings("unchecked")
        Map<String, Object> methods = (Map<String, Object>) methodDefinitions.getClass().getMethod("getMethods").invoke(methodDefinitions);

        Object method = methods.get("rpc.echo");
        assertNotNull(method);

        Object params = method.getClass().getMethod("getParams").invoke(method);
        assertNotNull(params);
        assertEquals("MethodParams", params.getClass().getSimpleName());
        assertEquals("tmp.schema.json", params.getClass().getMethod("getLocation").invoke(params));
    }

    private static CompilerResult compileMethodMapSources() {
        var sources = splitSources(MC_MAIN_DEMO + """
                package com.predic8.membrane.demo;
                import com.predic8.membrane.annot.*;
                @MCElement(name="api", topLevel=true, component=false)
                public class ApiElement {
                    private MethodDefinitions methods;
                
                    @MCChildElement
                    public void setMethods(MethodDefinitions methods) { this.methods = methods; }
                    public MethodDefinitions getMethods() { return methods; }
                }
                ---
                package com.predic8.membrane.demo;
                import com.predic8.membrane.annot.*;
                import java.util.LinkedHashMap;
                import java.util.Map;
                
                @MCElement(name="methods", component=false)
                public class MethodDefinitions {
                    private final Map<String, MethodElement> methods = new LinkedHashMap<>();
                
                    @MCOtherAttributes
                    public void setMethods(Map<String, MethodElement> methods) {
                        this.methods.putAll(methods);
                    }
                
                    public Map<String, MethodElement> getMethods() { return methods; }
                }
                ---
                package com.predic8.membrane.demo;
                import com.predic8.membrane.annot.*;
                
                @MCElement(name="method", component=false)
                public class MethodElement {
                    private MethodParams params;
                
                    @MCChildElement
                    public void setParams(MethodParams params) { this.params = params; }
                    public MethodParams getParams() { return params; }
                }
                ---
                package com.predic8.membrane.demo;
                import com.predic8.membrane.annot.*;
                
                @MCElement(name="params", component=false)
                public class GlobalParams {
                    @MCAttribute
                    public void setOther(String other) { }
                }
                ---
                package com.predic8.membrane.demo;
                import com.predic8.membrane.annot.*;
                
                @MCElement(name="params", component=false, id="method-params")
                public class MethodParams {
                    private String location;
                
                    @MCAttribute
                    public void setLocation(String location) { this.location = location; }
                    public String getLocation() { return location; }
                }
                """);
        var result = compile(sources, false);
        assertCompilerResult(true, result);
        return result;
    }

    private static @NotNull ConfigurationParsingException getCause(RuntimeException e) {
        return (ConfigurationParsingException) ExceptionUtil.getRootCause(e);
    }
}
