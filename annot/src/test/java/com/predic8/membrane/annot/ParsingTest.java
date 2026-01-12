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
import com.predic8.membrane.annot.beanregistry.SpringContextAdapter;
import com.predic8.membrane.annot.util.CompilerHelper;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.context.ConfigurableApplicationContext;

import java.util.List;

import static com.predic8.membrane.annot.SpringConfigurationXSDGeneratingAnnotationProcessorTest.MC_MAIN_DEMO;
import static com.predic8.membrane.annot.util.CompilerHelper.*;
import static com.predic8.membrane.annot.util.StructureAssertionUtil.*;
import static com.predic8.membrane.annot.util.StructureAssertionUtil.clazz;

public class ParsingTest {

    private String wrapSpring(String content) {
        return """
                <spring:beans xmlns="http://membrane-soa.org/demo/1/"
                              xmlns:spring="http://www.springframework.org/schema/beans"
                              xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                              xsi:schemaLocation="http://www.springframework.org/schema/beans
                                        http://www.springframework.org/schema/beans/spring-beans-4.2.xsd
                                        http://membrane-soa.org/demo/1/ http://membrane-soa.org/schemas/demo-1.xsd">
                %s
                </spring:beans>
                """.formatted(content);
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

        parseXML(result, wrapSpring("""
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

        parseXML(result, wrapSpring("""
                <root>
                    <child1 />
                </root>
                """));
    }

    @Test
    public void afterPropertiesSetAndDestroy() {
        var sources = splitSources(MC_MAIN_DEMO + """
        package com.predic8.membrane.demo;
        import com.predic8.membrane.annot.*;
        import java.util.List;
        @MCElement(name="root")
        public class DemoElement {
            ChildElement child;
        
            public ChildElement getChild() { return child; }
            @MCChildElement
            public void setChild(ChildElement child) { this.child = child; }
        }
        ---
        package com.predic8.membrane.demo;
        import com.predic8.membrane.annot.*;
        import org.springframework.beans.factory.*;
        import static org.junit.jupiter.api.Assertions.assertEquals;
        @MCElement(name="child")
        public class ChildElement implements InitializingBean, DisposableBean {
            int value = 0;
            public int getValue() { return value; }
        
            public void afterPropertiesSet() throws Exception {
                assertEquals(0, value);
                value = 1;
            }
            public void destroy() throws Exception {
                assertEquals(1, value);
                value = 2;
            }
        }
        """);
        var result = CompilerHelper.compile(sources, false);
        assertCompilerResult(true, result);

        BeanRegistry br = parseXML(result, wrapSpring("""
                <root>
                    <child />
                </root>
                """));

        assertStructure(
                br,
                clazz("DemoElement",
                        property("child", clazz("ChildElement",
                                property("value", value(1))))));

        List<Object> beans = br.getBeans(); // 'list of beans' must be retrieved before closing the context

        ((ConfigurableApplicationContext) ((SpringContextAdapter)br).getApplicationContext()).close();

        assertStructure(
                beans,
                clazz("DemoElement",
                        property("child", clazz("ChildElement",
                                property("value", value(2))))));

    }

    @Test
    public void afterPropertiesSetAndDestroyAndPostConstructAndPreDestroy() {
        var sources = splitSources(MC_MAIN_DEMO + """
        package com.predic8.membrane.demo;
        import com.predic8.membrane.annot.*;
        import java.util.List;
        @MCElement(name="root")
        public class DemoElement {
            ChildElement child;
        
            public ChildElement getChild() { return child; }
            @MCChildElement
            public void setChild(ChildElement child) { this.child = child; }
        }
        ---
        package com.predic8.membrane.demo;
        import com.predic8.membrane.annot.*;
        import org.springframework.beans.factory.*;
        import jakarta.annotation.*;
        import static org.junit.jupiter.api.Assertions.assertEquals;
        @MCElement(name="child")
        public class ChildElement implements InitializingBean, DisposableBean {
            int value = 0;
            public int getValue() { return value; }
        
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

        BeanRegistry br = parseXML(result, wrapSpring("""
                <spring:bean class="org.springframework.context.annotation.CommonAnnotationBeanPostProcessor" />
                
                <root>
                    <child />
                </root>
                """));

        assertStructure(
                br,
                clazz("CommonAnnotationBeanPostProcessor"),
                clazz("DemoElement",
                        property("child", clazz("ChildElement",
                                property("value", value(1))))));

        List<Object> beans = br.getBeans(); // 'list of beans' must be retrieved before closing the context

        ((ConfigurableApplicationContext) ((SpringContextAdapter)br).getApplicationContext()).close();

        assertStructure(
                beans,
                clazz("CommonAnnotationBeanPostProcessor"),
                clazz("DemoElement",
                        property("child", clazz("ChildElement",
                                property("value", value(2))))));

    }

    @Test
    public void postConstructAndPreDestroy() {
        var sources = splitSources(MC_MAIN_DEMO + """
        package com.predic8.membrane.demo;
        import com.predic8.membrane.annot.*;
        import java.util.List;
        @MCElement(name="root")
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

        BeanRegistry br = parseXML(result, wrapSpring("""
                <spring:bean class="org.springframework.context.annotation.CommonAnnotationBeanPostProcessor" />
                
                <root>
                    <child />
                </root>
                """));

        assertStructure(
                br,
                clazz("CommonAnnotationBeanPostProcessor"),
                clazz("DemoElement",
                        property("child", clazz("ChildElement",
                                property("value", value(1))))));

        List<Object> beans = br.getBeans(); // 'list of beans' must be retrieved before closing the context

        ((ConfigurableApplicationContext) ((SpringContextAdapter)br).getApplicationContext()).close();

        assertStructure(
                beans,
                clazz("CommonAnnotationBeanPostProcessor"),
                clazz("DemoElement",
                        property("child", clazz("ChildElement",
                                property("value", value(2))))));

    }

}
