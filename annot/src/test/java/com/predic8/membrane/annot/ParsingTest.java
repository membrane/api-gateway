package com.predic8.membrane.annot;

import com.predic8.membrane.annot.util.CompilerHelper;
import org.junit.jupiter.api.Test;

import static com.predic8.membrane.annot.SpringConfigurationXSDGeneratingAnnotationProcessorTest.MC_MAIN_DEMO;
import static com.predic8.membrane.annot.util.CompilerHelper.*;

public class ParsingTest {

    private String wrapSpring(String content) {
        return """
                <spring:beans xmlns="http://membrane-soa.org/demo/1/"
                              xmlns:spring="http://www.springframework.org/schema/beans"
                              xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                              xsi:schemaLocation="http://www.springframework.org/schema/beans
                                        http://www.springframework.org/schema/beans/spring-beans-4.2.xsd
                                        http://membrane-soa.org/demo/1/ http://membrane-soa.org/schemas/demo-1.xsd">
                """ + content + """
                </spring:beans>
                """;
    }

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

        parse(result, wrapSpring("""
                <demo />
                """));
    }

    @Test
    public void childElements() {
        var sources = splitSources(MC_MAIN_DEMO + """
        package com.predic8.membrane.demo;
        import com.predic8.membrane.annot.*;
        import java.util.List;
        @MCElement(name="root")
        public class DemoElement {
            @MCChildElement
            public void setChild(AbstractDemoChildElement s) {}
        }
        ---
        package com.predic8.membrane.demo;
        public abstract class AbstractDemoChildElement {
        }
        ---
        package com.predic8.membrane.demo;
        import com.predic8.membrane.annot.*;
        @MCElement(name="child1")
        public class Child1 extends AbstractDemoChildElement {
        }
        ---
        package com.predic8.membrane.demo;
        import com.predic8.membrane.annot.*;
        @MCElement(name="child2")
        public class Child2 extends AbstractDemoChildElement {
        }
        """);
        var result = CompilerHelper.compile(sources, false);
        assertCompilerResult(true, result);

        parse(result, wrapSpring("""
                <root>
                    <child1 />
                </root>
                """));
    }
}
