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
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import javax.tools.JavaFileObject;
import java.util.List;

import static com.predic8.membrane.annot.SpringConfigurationXSDGeneratingAnnotationProcessorTest.MC_MAIN_DEMO;
import static com.predic8.membrane.annot.util.CompilerHelper.*;
import static java.util.List.of;

public class SpringConfigXSDErrorsTest {
    @Test
    public void mcMainMissing() {
        List<JavaFileObject> sources = splitSources("""
            package com.predic8.membrane.demo;
            public class Demo {
            }
            ---
            package com.predic8.membrane.demo;
            import com.predic8.membrane.annot.MCElement;
            @MCElement(name="demo")
            public class DemoElement {
            }
            """);
        var result = CompilerHelper.compile(sources, false);

        assertCompilerResult(true, of(
                        warning("@MCMain was nowhere found.")
                ), result);
    }

    @Test
    public void mcElementNameMissing() {
        List<JavaFileObject> sources = splitSources("""
            package com.predic8.membrane.demo;
            import com.predic8.membrane.annot.MCElement;
            @MCElement
            public class DemoElement {
            }
            """);
        var result = CompilerHelper.compile(sources, false);

        assertCompilerResult(false, of(
                error("annotation @com.predic8.membrane.annot.MCElement is missing a default value for the element 'name'")
        ), result);
    }

    @Test
    public void mcElementMissing() {
        List<JavaFileObject> sources = splitSources(MC_MAIN_DEMO);
        var result = CompilerHelper.compile(sources, false);

        assertCompilerResult(false, of(
                error("@MCMain but no @MCElement found.")
        ), result);
    }


    @Test
    public void duplicateMcElementId() {
        List<JavaFileObject> sources = splitSources(MC_MAIN_DEMO + """
            package com.predic8.membrane.demo;
            import com.predic8.membrane.annot.MCElement;
            @MCElement(name="demo")
            public class DemoElement {
            }
            ---
            package com.predic8.membrane.demo;
            import com.predic8.membrane.annot.MCElement;
            @MCElement(name="demo")
            public class DemoElement2 {
            }
            """);
        var result = CompilerHelper.compile(sources, false);

        assertCompilerResult(false, of(
                error("Duplicate element id \"demo\". Please assign one using @MCElement(id=\"...\")."),
                error("also here")
        ), result);
    }

    @Test
    public void duplicateMcElementName() {
        List<JavaFileObject> sources = splitSources(MC_MAIN_DEMO + """
            package com.predic8.membrane.demo;
            import com.predic8.membrane.annot.MCElement;
            @MCElement(name="demo", id="demo1")
            public class DemoElement {
            }
            ---
            package com.predic8.membrane.demo;
            import com.predic8.membrane.annot.MCElement;
            @MCElement(name="demo", id="demo2")
            public class DemoElement2 {
            }
            """);
        var result = CompilerHelper.compile(sources, false);

        assertCompilerResult(false, of(
                error("Duplicate top-level @MCElement name. Make at least one @MCElement(topLevel=false,...) ."),
                error("also here")
        ), result);
    }

    @Nested
    class NoEnvelope {

        @Test
        public void topLevel() {
            List<JavaFileObject> sources = splitSources(MC_MAIN_DEMO + """
            package com.predic8.membrane.demo;
            import com.predic8.membrane.annot.MCElement;
            @MCElement(name="demo", noEnvelope=true)
            public class DemoElement {
            }
            """);
            var result = CompilerHelper.compile(sources, false);

            assertCompilerResult(false, of(
                    error("@MCElement(..., noEnvelope=true, topLevel=true) is invalid.")
            ), result);
        }

        @Test
        public void mixed() {
            List<JavaFileObject> sources = splitSources(MC_MAIN_DEMO + """
            package com.predic8.membrane.demo;
            import com.predic8.membrane.annot.MCElement;
            @MCElement(name="demo", noEnvelope=true, topLevel=false, mixed=true)
            public class DemoElement {
            }
            """);
            var result = CompilerHelper.compile(sources, false);

            assertCompilerResult(false, of(
                    error("@MCElement(..., noEnvelope=true, mixed=true) is invalid.")
            ), result);
        }

        @Test
        public void noChildElements() {
            List<JavaFileObject> sources = splitSources(MC_MAIN_DEMO + """
            package com.predic8.membrane.demo;
            import com.predic8.membrane.annot.MCElement;
            @MCElement(name="demo", noEnvelope=true, topLevel=false)
            public class DemoElement {
            }
            """);
            var result = CompilerHelper.compile(sources, false);

            assertCompilerResult(false, of(
                    error("@MCElement(noEnvelope=true) requires exactly one @MCChildElement.")
            ), result);
        }

        @Test
        public void twoChildElements() {
            List<JavaFileObject> sources = splitSources(MC_MAIN_DEMO + """
            package com.predic8.membrane.demo;
            import com.predic8.membrane.annot.MCElement;
            import com.predic8.membrane.annot.MCChildElement;
            import java.util.List;
            @MCElement(name="demo", noEnvelope=true, topLevel=false)
            public class DemoElement {
                @MCChildElement(order=1)
                public void setChild1(List<DemoElement> s) {}
                @MCChildElement(order=2)
                public void setChild2(List<DemoElement> s) {}
            }
            """);
            var result = CompilerHelper.compile(sources, false);

            assertCompilerResult(false, of(
                    error("@MCElement(noEnvelope=true) requires exactly one @MCChildElement.")
            ), result);
        }

        @Test
        public void childIsNotAList() {
            List<JavaFileObject> sources = splitSources(MC_MAIN_DEMO + """
            package com.predic8.membrane.demo;
            import com.predic8.membrane.annot.MCElement;
            import com.predic8.membrane.annot.MCChildElement;
            @MCElement(name="demo", noEnvelope=true, topLevel=false)
            public class DemoElement {
                @MCChildElement
                public void setChild1(DemoElement s) {}
            }
            """);
            var result = CompilerHelper.compile(sources, false);

            assertCompilerResult(false, of(
                    error("@MCElement(noEnvelope=true) requires its @MCChildElement() to be a List or Collection.")
            ), result);
        }

        @Test
        public void hasAttributes() {
            List<JavaFileObject> sources = splitSources(MC_MAIN_DEMO + """
            package com.predic8.membrane.demo;
            import com.predic8.membrane.annot.*;
            import java.util.List;
            @MCElement(name="demo", noEnvelope=true, topLevel=false)
            public class DemoElement {
                @MCChildElement
                public void setChild1(List<DemoElement> s) {}
                @MCAttribute
                public void setAttribute1(String s) {}
            }
            """);
            var result = CompilerHelper.compile(sources, false);

            assertCompilerResult(false, of(
                    error("@MCElement(noEnvelope=true) requires @MCAttribute to be not present.")
            ), result);
        }

        @Test
        public void otherAttributes() {
            List<JavaFileObject> sources = splitSources(MC_MAIN_DEMO + """
            package com.predic8.membrane.demo;
            import com.predic8.membrane.annot.*;
            import java.util.List;
            import java.util.Map;
            @MCElement(name="demo", noEnvelope=true, topLevel=false)
            public class DemoElement {
                @MCChildElement
                public void setChild1(List<DemoElement> s) {}
                @MCOtherAttributes
                public void setAttributes(Map<String, String> attributes) {}
            }
            """);
            var result = CompilerHelper.compile(sources, false);

            assertCompilerResult(false, of(
                    error("@MCElement(noEnvelope=true) requires @MCOtherAttributes to be not present.")
            ), result);
        }

        @Test
        public void textContent() {
            List<JavaFileObject> sources = splitSources(MC_MAIN_DEMO + """
            package com.predic8.membrane.demo;
            import com.predic8.membrane.annot.*;
            import java.util.List;
            import java.util.Map;
            @MCElement(name="demo", noEnvelope=true, topLevel=false)
            public class DemoElement {
                @MCChildElement
                public void setChild1(List<DemoElement> s) {}
                @MCTextContent
                public void setContent(String content) {}
            }
            """);
            var result = CompilerHelper.compile(sources, false);

            assertCompilerResult(false, of(
                    error("@MCElement(noEnvelope=true) requires @MCTextContent to be not present.")
            ), result);
        }
    }

    @Nested
    class TextContent {
        @Test
        public void mcTextContentMissing() {
            List<JavaFileObject> sources = splitSources(MC_MAIN_DEMO + """
            package com.predic8.membrane.demo;
            import com.predic8.membrane.annot.*;
            @MCElement(name="demo", mixed=true)
            public class DemoElement {
            }
            """);
            var result = CompilerHelper.compile(sources, false);

            assertCompilerResult(false, of(
                    error("@MCElement(..., mixed=true) requires @MCTextContent on a property.")
            ), result);
        }
        @Test
        public void mixedMissing() {
            List<JavaFileObject> sources = splitSources(MC_MAIN_DEMO + """
            package com.predic8.membrane.demo;
            import com.predic8.membrane.annot.*;
            @MCElement(name="demo")
            public class DemoElement {
                @MCTextContent
                public void setContent(String content) {}
            }
            """);
            var result = CompilerHelper.compile(sources, false);

            assertCompilerResult(false, of(
                    error("@MCTextContent requires @MCElement(..., mixed=true) on the class.")
            ), result);
        }

    }
}
