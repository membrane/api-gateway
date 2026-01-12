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

package com.predic8.membrane.annot;

import com.predic8.membrane.annot.beanregistry.BeanRegistry;
import com.predic8.membrane.annot.util.CompilerHelper;
import com.predic8.membrane.annot.yaml.YamlSchemaValidationException;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationTargetException;
import java.util.List;

import static com.predic8.membrane.annot.SpringConfigurationXSDGeneratingAnnotationProcessorTest.MC_MAIN_DEMO;
import static com.predic8.membrane.annot.util.CompilerHelper.*;
import static com.predic8.membrane.annot.util.StructureAssertionUtil.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class YAMLParsingTest {
    @Test
    public void simple() {
        var sources = splitSources(MC_MAIN_DEMO + """
        package com.predic8.membrane.demo;
        import com.predic8.membrane.annot.*;
        import java.util.List;
        @MCElement(name="demo", topLevel=true, component=false)
        public class DemoElement {
        }
        """);
        var result = CompilerHelper.compile(sources, false);
        assertCompilerResult(true, result);

        assertStructure(
                parseYAML(result, """
                demo: {}
                """),
                clazz("DemoElement")
        );
    }

    @Test
    public void attribute() {
        var sources = splitSources(MC_MAIN_DEMO + """
        package com.predic8.membrane.demo;
        import com.predic8.membrane.annot.*;
        import java.util.List;
        @MCElement(name="demo", topLevel=true, component=false)
        public class DemoElement {
            public String attr;
        
            public String getAttr() {
                return attr;
            }
        
            @MCAttribute
            public void setAttr(String attr) {
                this.attr = attr;
            }
        }
        """);
        var result = CompilerHelper.compile(sources, false);
        assertCompilerResult(true, result);

        assertStructure(
                parseYAML(result, """
                demo:
                    attr: here
                """),
                clazz("DemoElement",
                        property("attr", value("here")))
        );
    }

    @Test
    public void singleChild() {
        var sources = splitSources(MC_MAIN_DEMO + """
        package com.predic8.membrane.demo;
        import com.predic8.membrane.annot.*;
        import java.util.List;
        @MCElement(name="demo", topLevel=true, component=false)
        public class DemoElement {
            Child1Element child;
        
            public Child1Element getChild() {
                return child;
            }
        
            @MCChildElement
            public void setChild(Child1Element child) {
                this.child = child;
            }
        }
        ---
        package com.predic8.membrane.demo;
        import com.predic8.membrane.annot.*;
        @MCElement(name="child1", component=false)
        public class Child1Element {
        }
        """);
        var result = CompilerHelper.compile(sources, false);
        assertCompilerResult(true, result);

        assertStructure(
                parseYAML(result, """
                demo:
                    child1: {}
                """),
                clazz("DemoElement",
                        property("child", clazz("Child1Element")))
        );
    }

    @Test
    public void twoObjects() {
        var sources = splitSources(MC_MAIN_DEMO + """
        package com.predic8.membrane.demo;
        import com.predic8.membrane.annot.*;
        import java.util.List;
        @MCElement(name="demo", topLevel=true, component=false)
        public class DemoElement {
        }
        """);
        var result = CompilerHelper.compile(sources, false);
        assertCompilerResult(true, result);

        assertStructure(
                parseYAML(result, """
                demo: {}
                ---
                demo: {}
                """),
                clazz("DemoElement"),
                clazz("DemoElement")
        );

    }

    @Test
    public void nestedChildren() {
        var sources = splitSources(MC_MAIN_DEMO + """
        package com.predic8.membrane.demo;
        import com.predic8.membrane.annot.*;
        import java.util.List;
        @MCElement(name="demo", topLevel=true, component=false)
        public class DemoElement {
            Child1Element child;
        
            public Child1Element getChild() {
                return child;
            }
        
            @MCChildElement
            public void setChild(Child1Element child) {
                this.child = child;
            }
        }
        ---
        package com.predic8.membrane.demo;
        import com.predic8.membrane.annot.*;
        @MCElement(name="child1", component=false)
        public class Child1Element {
            Child2Element child;
        
            public Child2Element getChild() {
                return child;
            }
        
            @MCChildElement
            public void setChild(Child2Element child) {
                this.child = child;
            }
        }
        ---
        package com.predic8.membrane.demo;
        import com.predic8.membrane.annot.*;
        @MCElement(name="child2", component=false)
        public class Child2Element {
        }
        """);
        var result = CompilerHelper.compile(sources, false);
        assertCompilerResult(true, result);

        assertStructure(
                parseYAML(result, """
                demo:
                    child1:
                        child2: {}
                """),
                clazz("DemoElement",
                        property("child", clazz("Child1Element",
                                property("child", clazz("Child2Element"))))));
    }

    @Test
    public void nestedListOfChildsWithAttr() {
        var sources = splitSources(MC_MAIN_DEMO + """
        package com.predic8.membrane.demo;
        import com.predic8.membrane.annot.*;
        import java.util.List;
        @MCElement(name="demo", topLevel=true, component=false)
        public class DemoElement {
            Child1Element child;
        
            public Child1Element getChild() {
                return child;
            }
        
            @MCChildElement
            public void setChild(Child1Element child) {
                this.child = child;
            }
        }
        ---
        package com.predic8.membrane.demo;
        import com.predic8.membrane.annot.*;
        import java.util.List;
        @MCElement(name="child1")
        public class Child1Element {
            List<Child2Element> child;
        
            public List<Child2Element> getChild() {
                return child;
            }
        
            @MCChildElement
            public void setChild(List<Child2Element> child) {
                this.child = child;
            }
        
            @MCElement(name="child2", component=false)
            public static class Child2Element {
                public String attr;
        
                public String getAttr() {
                    return attr;
                }
        
                @MCAttribute
                public void setAttr(String attr) {
                    this.attr = attr;
                }
            }

        }
        """);
        var result = CompilerHelper.compile(sources, false);
        assertCompilerResult(true, result);

        assertStructure(
                parseYAML(result, """
                demo:
                    child1:
                        child:
                        - child2:
                            attr: here
                """),
                clazz("DemoElement",
                        property("child", clazz("Child1Element",
                                property("child", list( clazz("Child2Element",
                                        property("attr", value("here")))))))));
    }

    @Test
    public void noEnvelope() {
        var sources = splitSources(MC_MAIN_DEMO + """
        package com.predic8.membrane.demo;
        import com.predic8.membrane.annot.*;
        import java.util.List;
        @MCElement(name="demo", noEnvelope=true, topLevel=true, component=false)
        public class DemoElement {
            List<Child1Element> children;
        
            public List<Child1Element> getChildren() {
                return children;
            }
        
            @MCChildElement
            public void setChildren(List<Child1Element> children) {
                this.children = children;
            }
        }
        ---
        package com.predic8.membrane.demo;
        import com.predic8.membrane.annot.*;
        import java.util.List;
        @MCElement(name="child1")
        public class Child1Element {
            public String attr;
        
            public String getAttr() {
                return attr;
            }
        
            @MCAttribute
            public void setAttr(String attr) {
                this.attr = attr;
            }
        }
        """);
        var result = CompilerHelper.compile(sources, false);
        assertCompilerResult(true, result);

        assertStructure(
                parseYAML(result, """
                demo:
                  - child1:
                        attr: here
                  - child1:
                        attr: here2
                """),
                clazz("DemoElement",
                        property("children", list(
                                clazz("Child1Element",
                                        property("attr", value("here"))),
                                clazz("Child1Element",
                                        property("attr", value("here2")))))));
    }

    @Test
    public void nestedListOfChildsWithAttr2() {
        var sources = splitSources(MC_MAIN_DEMO + """
        package com.predic8.membrane.demo;
        import com.predic8.membrane.annot.*;
        import java.util.List;
        @MCElement(name="outer", topLevel=true, component=false)
        public class OuterElement {
            List<DemoElement> child;
        
            public List<DemoElement> getFlow() {
                return child;
            }
        
            @MCChildElement
            public void setFlow(List<DemoElement> child) {
                this.child = child;
            }
        }
        ---
        package com.predic8.membrane.demo;
        import com.predic8.membrane.annot.*;
        import java.util.List;
        @MCElement(name="demo")
        public class DemoElement {
            Child1Element child;
        
            public Child1Element getChild() {
                return child;
            }
        
            @MCChildElement
            public void setChild(Child1Element child) {
                this.child = child;
            }
        }
        ---
        package com.predic8.membrane.demo;
        import com.predic8.membrane.annot.*;
        import java.util.List;
        @MCElement(name="child")
        public class Child1Element {
            List<Child2Element> child;
        
            public List<Child2Element> getChild() {
                return child;
            }
        
            @MCChildElement
            public void setChild(List<Child2Element> child) {
                this.child = child;
            }
        
            @MCElement(name="child2", mixed=true, component=false)
            public static class Child2Element {
                public String attr;
                public String content;
        
                public String getAttr() {
                    return attr;
                }
        
                @MCAttribute
                public void setAttr(String attr) {
                    this.attr = attr;
                }
                public String getContent() {
                    return content;
                }
                @MCTextContent
                public void setContent(String content) {
                    this.content = content;
                }
            }

        }
        """);
        var result = CompilerHelper.compile(sources, false);
        assertCompilerResult(true, result);

        assertStructure(
                parseYAML(result, """
                outer:
                  flow:
                  - demo:
                      child:
                        child:
                        - child2:
                            attr: here
                            content: here2
                """),
                clazz("OuterElement",
                        property("flow", list(
                            clazz("DemoElement",
                                    property("child", clazz("Child1Element",
                                            property("child", list( clazz("Child2Element",
                                                    property("attr", value("here")),
                                                    property("content", value("here2"))
                                            ))))))))));
    }

    @Test
    public void errorInSecondLevelWord() {
        var sources = splitSources(MC_MAIN_DEMO + """
        package com.predic8.membrane.demo;
        import com.predic8.membrane.annot.*;
        import java.util.List;
        @MCElement(name="demo", topLevel=true, component=false)
        public class DemoElement {
            Child1Element child;
        
            public Child1Element getChild() {
                return child;
            }
        
            @MCChildElement
            public void setChild(Child1Element child) {
                this.child = child;
            }
        }
        ---
        package com.predic8.membrane.demo;
        import com.predic8.membrane.annot.*;
        @MCElement(name="child1", component=false)
        public class Child1Element {
        }
        ---
        package com.predic8.membrane.demo;
        import com.predic8.membrane.annot.*;
        @MCElement(name="demo2", topLevel=true, component=false)
        public class Demo2Element {
        }
        ---
        """);
        var result = CompilerHelper.compile(sources, false);
        assertCompilerResult(true, result);

        try {
            parseYAML(result, """
                    demo:
                        errorHere: {}
                    """);
            throw new AssertionError("Parsing did not throw a nested YamlSchemaValidationException.");
        } catch (RuntimeException e) {
            YamlSchemaValidationException e2 = (YamlSchemaValidationException) getCause(e);
            assertEquals(1, e2.getErrors().size());
            assertEquals("/demo: property 'errorHere' is not defined in the schema and the schema does not allow additional properties",
                    e2.getErrors().getFirst().toString());
        }
    }

    @Test
    public void requiredChild() {
        var sources = splitSources(MC_MAIN_DEMO + """
        package com.predic8.membrane.demo;
        import com.predic8.membrane.annot.*;
        import java.util.List;
        @MCElement(name="demo", topLevel=true, component=false)
        public class DemoElement {
            ChildElement child;
        
            public ChildElement getChild() {
                return child;
            }
        
            @Required
            @MCChildElement
            public void setChild(ChildElement child) {
                this.child = child;
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

        parseYAML(result, """
                    demo:
                        a: {}
                    """);

        try {
            parseYAML(result, """
                    demo: {}
                    """);
            throw new AssertionError("Parsing did not throw an Exception.");
        } catch (RuntimeException e) {
        }
    }

    @Test
    public void errorInListItemUniqueness() {
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
        ---
        package com.predic8.membrane.demo;
        import com.predic8.membrane.annot.*;
        @MCElement(name="demo2")
        public class Demo2Element {
        }
        ---
        """);
        var result = CompilerHelper.compile(sources, false);
        assertCompilerResult(true, result);

        try {
            parseYAML(result, """
                    demo:
                        children:
                        - a: {}
                          b: {}
                    """);
            throw new AssertionError("Parsing did not throw a nested YamlSchemaValidationException.");
        } catch (RuntimeException e) {
            YamlSchemaValidationException e2 = (YamlSchemaValidationException) getCause(e);
            assertEquals(1, e2.getErrors().size());
            assertEquals(": property 'demo' is not defined in the schema and the schema does not allow additional properties",
                    e2.getErrors().getFirst().toString());
        }
    }

    @Test
    public void postConstructAndPreDestroy() {
        var sources = splitSources(MC_MAIN_DEMO + """
        package com.predic8.membrane.demo;
        import com.predic8.membrane.annot.*;
        import java.util.List;
        @MCElement(name="root", topLevel=true, component=false)
        public class DemoElement {
            ChildElement child;
        
            public ChildElement getChild() { return child; }
            @MCChildElement
            public void setChild(ChildElement child) { this.child = child; }
        }
        ---
        package com.predic8.membrane.demo;
        import com.predic8.membrane.annot.*;
        import jakarta.annotation.*;
        import static org.junit.jupiter.api.Assertions.assertEquals;
        @MCElement(name="child")
        public class ChildElement {
            int value = 0;
            public int getValue() {
                return value; 
            }
        
            @PostConstruct
            public void afterPropertiesSet() throws Exception {
                assertEquals(0, value);
                value = 1;
            }
            @PreDestroy
            public void destroy() throws Exception {
                assertEquals(1, value);
                value = 2;
            }
        }
        """);
        var result = CompilerHelper.compile(sources, false);
        assertCompilerResult(true, result);

        BeanRegistry br = parseYAML(result, """
                root:
                    child: {}
                """);

        assertStructure(
                br,
                clazz("DemoElement",
                        property("child", clazz("ChildElement",
                                property("value", value(1))))));

        List<Object> beans = br.getBeans(); // 'list of beans' must be retrieved before closing the context

        br.close();

        assertStructure(
                beans,
                clazz("DemoElement",
                        property("child", clazz("ChildElement",
                                property("value", value(2))))));

    }

    private Throwable getCause(Throwable e) {
        if (e.getCause() != null)
            return getCause(e.getCause());
        if (e instanceof InvocationTargetException ite)
            return getCause(ite.getTargetException());
        return e;
    }

}
