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
import org.junit.jupiter.api.Test;

import static com.predic8.membrane.annot.SpringConfigurationXSDGeneratingAnnotationProcessorTest.MC_MAIN_DEMO;
import static com.predic8.membrane.annot.util.CompilerHelper.*;
import static com.predic8.membrane.annot.util.StructureAssertionUtil.*;

public class YAMLParsingTest {
    @Test
    public void simple() {
        var sources = splitSources(MC_MAIN_DEMO + """
        package com.predic8.membrane.demo;
        import com.predic8.membrane.annot.*;
        import java.util.List;
        @MCElement(name="demo")
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
        @MCElement(name="demo")
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
        @MCElement(name="child1", topLevel=false)
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
        @MCElement(name="demo")
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
        @MCElement(name="child1", topLevel=false)
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
        @MCElement(name="child2", topLevel=false)
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
        
            @MCElement(name="child2", topLevel=false)
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
    public void nestedListOfChildsWithAttr2() {
        var sources = splitSources(MC_MAIN_DEMO + """
        package com.predic8.membrane.demo;
        import com.predic8.membrane.annot.*;
        import java.util.List;
        @MCElement(name="outer")
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
        
            @MCElement(name="child2", mixed=true, topLevel=false)
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

}
