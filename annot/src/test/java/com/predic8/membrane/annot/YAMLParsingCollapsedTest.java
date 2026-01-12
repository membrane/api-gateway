package com.predic8.membrane.annot;

import com.predic8.membrane.annot.util.CompilerHelper;
import com.predic8.membrane.annot.yaml.YamlSchemaValidationException;
import org.junit.jupiter.api.Test;

import static com.predic8.membrane.annot.SpringConfigurationXSDGeneratingAnnotationProcessorTest.MC_MAIN_DEMO;
import static com.predic8.membrane.annot.util.CompilerHelper.*;
import static com.predic8.membrane.annot.util.StructureAssertionUtil.*;
import static org.junit.jupiter.api.Assertions.*;

public class YAMLParsingCollapsedTest {

    @Test
    void collapsedAttributeInline() {
        var sources = splitSources(MC_MAIN_DEMO + """
        package com.predic8.membrane.demo;
        import com.predic8.membrane.annot.*;
        @MCElement(name="demo", topLevel=true, component=false)
        public class DemoElement {
            ChildElement child;
            public ChildElement getChild() { return child; }
            @MCChildElement public void setChild(ChildElement child) { this.child = child; }
        }
        ---
        package com.predic8.membrane.demo;
        import com.predic8.membrane.annot.*;
        @MCElement(name="child", collapsed=true)
        public class ChildElement {
            int value = 0;
            @MCAttribute public void setValue(int value) { this.value = value; }
            public int getValue() { return value; }
        }
        """);

        var result = CompilerHelper.compile(sources, false);
        assertCompilerResult(true, result);

        assertStructure(
                parseYAML(result, """
                demo:
                  child: 12
                """),
                clazz("DemoElement",
                        property("child", clazz("ChildElement",
                                property("value", value(12))))))
        ;
    }

    @Test
    void collapsedAttributeInlineList_noEnvelope() {
        var sources = splitSources(MC_MAIN_DEMO + """
        package com.predic8.membrane.demo;
        import com.predic8.membrane.annot.*;
        import java.util.List;
        @MCElement(name="demo", topLevel=true, component=false, noEnvelope=true)
        public class DemoElement {
            List<ChildElement> children;
            public List<ChildElement> getChildren() { return children; }
            @MCChildElement public void setChildren(List<ChildElement> children) { this.children = children; }
        }
        ---
        package com.predic8.membrane.demo;
        import com.predic8.membrane.annot.*;
        @MCElement(name="child", collapsed=true)
        public class ChildElement {
            String value = "foo";
            @MCAttribute public void setValue(String value) { this.value = value; }
            public String getValue() { return value; }
        }
        """);

        var result = CompilerHelper.compile(sources, false);
        assertCompilerResult(true, result);

        assertStructure(
                parseYAML(result, """
                demo:
                  - child: bar
                  - child: baz
                """),
                clazz("DemoElement",
                        property("children", list(
                                clazz("ChildElement", property("value", value("bar"))),
                                clazz("ChildElement", property("value", value("baz")))
                        ))))
        ;
    }

    @Test
    void collapsedTextContentInline() {
        var sources = splitSources(MC_MAIN_DEMO + """
        package com.predic8.membrane.demo;
        import com.predic8.membrane.annot.*;
        @MCElement(name="demo", topLevel=true, component=false)
        public class DemoElement {
            ChildElement child;
            public ChildElement getChild() { return child; }
            @MCChildElement public void setChild(ChildElement child) { this.child = child; }
        }
        ---
        package com.predic8.membrane.demo;
        import com.predic8.membrane.annot.*;
        @MCElement(name="child", collapsed=true, mixed=true)
        public class ChildElement {
            String content;
            public String getContent() { return content; }
            @MCTextContent public void setContent(String content) { this.content = content; }
        }
        """);

        var result = CompilerHelper.compile(sources, false);
        assertCompilerResult(true, result);

        assertStructure(
                parseYAML(result, """
                demo:
                  child: hello
                """),
                clazz("DemoElement",
                        property("child", clazz("ChildElement",
                                property("content", value("hello"))))))
        ;
    }

    @Test
    void collapsedTextContentInlineList_noEnvelope() {
        var sources = splitSources(MC_MAIN_DEMO + """
        package com.predic8.membrane.demo;
        import com.predic8.membrane.annot.*;
        import java.util.List;
        @MCElement(name="demo", topLevel=true, component=false, noEnvelope=true)
        public class DemoElement {
            List<ChildElement> children;
            public List<ChildElement> getChildren() { return children; }
            @MCChildElement public void setChildren(List<ChildElement> children) { this.children = children; }
        }
        ---
        package com.predic8.membrane.demo;
        import com.predic8.membrane.annot.*;
        @MCElement(name="child", collapsed=true, mixed=true)
        public class ChildElement {
            String content;
            public String getContent() { return content; }
            @MCTextContent public void setContent(String content) { this.content = content; }
        }
        """);

        var result = CompilerHelper.compile(sources, false);
        assertCompilerResult(true, result);

        assertStructure(
                parseYAML(result, """
                demo:
                  - child: a
                  - child: b
                """),
                clazz("DemoElement",
                        property("children", list(
                                clazz("ChildElement", property("content", value("a"))),
                                clazz("ChildElement", property("content", value("b")))
                        ))))
        ;
    }

    @Test
    void collapsedRejectsObjectShape_schemaValidation() {
        var sources = splitSources(MC_MAIN_DEMO + """
        package com.predic8.membrane.demo;
        import com.predic8.membrane.annot.*;
        @MCElement(name="demo", topLevel=true, component=false)
        public class DemoElement {
            ChildElement child;
            @MCChildElement public void setChild(ChildElement child) { this.child = child; }
        }
        ---
        package com.predic8.membrane.demo;
        import com.predic8.membrane.annot.*;
        @MCElement(name="child", collapsed=true)
        public class ChildElement {
            int value;
            @MCAttribute public void setValue(int value) { this.value = value; }
        }
        """);

        var result = CompilerHelper.compile(sources, false);
        assertCompilerResult(true, result);

        RuntimeException ex = assertThrows(RuntimeException.class, () ->
                parseYAML(result, """
                demo:
                  child:
                    value: 12
                """)
        );

        assertFalse(((YamlSchemaValidationException) rootCause(ex)).getErrors().isEmpty());
    }

    private static Throwable rootCause(Throwable e) {
        Throwable cur = e;
        while (true) {
            if (cur instanceof java.lang.reflect.InvocationTargetException ite) {
                cur = ite.getTargetException();
                continue;
            }
            if (cur.getCause() == null) return cur;
            cur = cur.getCause();
        }
    }
}
