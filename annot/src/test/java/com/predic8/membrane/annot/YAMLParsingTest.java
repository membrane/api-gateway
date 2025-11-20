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
    public void singleChild() {
        var sources = splitSources(MC_MAIN_DEMO + """
        package com.predic8.membrane.demo;
        import com.predic8.membrane.annot.*;
        import java.util.List;
        @MCElement(name="demo")
        public class DemoElement {
            ChildElement child;
        
            public ChildElement getChild() {
                return child;
            }
        
            @MCChildElement
            public void setChild(ChildElement child) {
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
                    child:
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

}
