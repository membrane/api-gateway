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

    private static @NotNull ConfigurationParsingException getCause(RuntimeException e) {
        return (ConfigurationParsingException) ExceptionUtil.getRootCause(e);
    }
}
