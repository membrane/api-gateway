package com.predic8.membrane.annot;

import com.predic8.membrane.annot.util.CompilerHelper;
import org.junit.jupiter.api.Test;

import static com.predic8.membrane.annot.SpringConfigurationXSDGeneratingAnnotationProcessorTest.MC_MAIN_DEMO;
import static com.predic8.membrane.annot.util.CompilerHelper.*;
import static java.util.List.of;

public class YamlSetterConflictTest {

    @Test
    public void sameConcreteChildOnTwoSetters() {
        var sources = splitSources(MC_MAIN_DEMO + """
        package com.predic8.membrane.demo;
        import com.predic8.membrane.annot.*;
        import java.util.List;

        @MCElement(name="demo")
        public class DemoElement {
            @MCChildElement(order = 1)
            public void setB(List<B> s) {}

            @MCChildElement(order = 2)
            public void setE(List<B> s) {}
        }
        ---
        package com.predic8.membrane.demo;
        import com.predic8.membrane.annot.*;

        @MCElement(name="b", topLevel = false, id = "b")
        public class B {
        }
        """);
        var result = CompilerHelper.compile(sources, false);

        assertCompilerResult(true, result);
    }

    @Test
    public void sameChildNameFromDifferentAbstractHierarchies() {
        var sources = splitSources(MC_MAIN_DEMO + """
        package com.predic8.membrane.demo;
        import com.predic8.membrane.annot.*;
        import java.util.List;

        @MCElement(name="a")
        public class A {
            @MCChildElement(order = 1)
            public void setB(List<AbstractC> s) {}

            @MCChildElement(order = 2)
            public void setE(List<AbstractF> s) {}
        }
        ---
        package com.predic8.membrane.demo;

        public abstract class AbstractC {
        }
        ---
        package com.predic8.membrane.demo;

        public abstract class AbstractF {
        }
        ---
        package com.predic8.membrane.demo.a;
        import com.predic8.membrane.annot.*;
        import com.predic8.membrane.demo.AbstractC;

        @MCElement(name="d", topLevel = false, id = "d1")
        public class D extends AbstractC {
        }
        ---
        package com.predic8.membrane.demo.b;
        import com.predic8.membrane.annot.*;
        import com.predic8.membrane.demo.AbstractF;

        @MCElement(name="d", topLevel = false, id = "d2")
        public class D extends AbstractF {
        }
        """);
        var result = CompilerHelper.compile(sources, false);

        assertCompilerResult(true, result);
    }

    @Test
    public void sameChildNameViaBaseAndConcreteSetter() {
        var sources = splitSources(MC_MAIN_DEMO + """
        package com.predic8.membrane.demo;
        import com.predic8.membrane.annot.*;
        import java.util.List;

        @MCElement(name="demo")
        public class DemoElement {
            @MCChildElement(order = 1)
            public void setAbstract(List<AbstractChild> s) {}

            @MCChildElement(order = 2)
            public void setConcrete(List<ConcreteChild> s) {}
        }
        ---
        package com.predic8.membrane.demo;

        public abstract class AbstractChild {
        }
        ---
        package com.predic8.membrane.demo;
        import com.predic8.membrane.annot.*;

        @MCElement(name="child", topLevel = false, id = "child")
        public class ConcreteChild extends AbstractChild {
        }
        """);
        var result = CompilerHelper.compile(sources, false);

        assertCompilerResult(true, result);
    }

    @Test
    public void childNameNotUniqueAcrossPackages() {
        var sources = splitSources(MC_MAIN_DEMO + """
        package com.predic8.membrane.demo;
        import com.predic8.membrane.annot.*;

        @MCElement(name="demo")
        public class DemoElement {
            @MCChildElement
            public void setChild(AbstractChildElement s) {}
        }
        ---
        package com.predic8.membrane.demo;

        public abstract class AbstractChildElement {
        }
        ---
        package com.predic8.membrane.demo.a;
        import com.predic8.membrane.annot.*;
        import com.predic8.membrane.demo.AbstractChildElement;

        @MCElement(name="child", topLevel = false, id = "child1")
        public class ChildA extends AbstractChildElement {
        }
        ---
        package com.predic8.membrane.demo.b;
        import com.predic8.membrane.annot.*;
        import com.predic8.membrane.demo.AbstractChildElement;

        @MCElement(name="child", topLevel = false, id = "child2")
        public class ChildB extends AbstractChildElement {
        }
        """);
        var result = CompilerHelper.compile(sources, false);

        assertCompilerResult(false, of(error("Duplicate childElement 'child': child")), result);
    }

    @Test
    public void sameConcreteChildOnTwoSetters_noList() {
        var sources = splitSources(MC_MAIN_DEMO + """
        package com.predic8.membrane.demo;
        import com.predic8.membrane.annot.*;

        @MCElement(name="demo")
        public class DemoElement {
            @MCChildElement(order = 1)
            public void setB(B b) {}

            @MCChildElement(order = 2)
            public void setE(B b) {}
        }
        ---
        package com.predic8.membrane.demo;
        import com.predic8.membrane.annot.*;

        @MCElement(name="b", topLevel = false, id = "b")
        public class B {
        }
        """);
        var result = CompilerHelper.compile(sources, false);

        assertCompilerResult(false, of(error("Name clash: 'b' used by childElement 'b' & childElement 'e'")), result);
    }

    @Test
    public void sameChildNameFromDifferentAbstractHierarchies_noList() {
        var sources = splitSources(MC_MAIN_DEMO + """
        package com.predic8.membrane.demo;
        import com.predic8.membrane.annot.*;

        @MCElement(name="a")
        public class A {
            @MCChildElement(order = 1)
            public void setB(AbstractC c) {}

            @MCChildElement(order = 2)
            public void setE(AbstractF f) {}
        }
        ---
        package com.predic8.membrane.demo;

        public abstract class AbstractC {
        }
        ---
        package com.predic8.membrane.demo;

        public abstract class AbstractF {
        }
        ---
        package com.predic8.membrane.demo.a;
        import com.predic8.membrane.annot.*;
        import com.predic8.membrane.demo.AbstractC;

        @MCElement(name="d", topLevel = false, id = "d1")
        public class DFromC extends AbstractC {
        }
        ---
        package com.predic8.membrane.demo.b;
        import com.predic8.membrane.annot.*;
        import com.predic8.membrane.demo.AbstractF;

        @MCElement(name="d", topLevel = false, id = "d2")
        public class DFromF extends AbstractF {
        }
        """);
        var result = CompilerHelper.compile(sources, false);

        assertCompilerResult(false, of(error("Name clash: 'd' used by childElement 'b' & childElement 'e'")), result);
    }

    @Test
    public void sameChildNameViaBaseAndConcreteSetter_noList() {
        var sources = splitSources(MC_MAIN_DEMO + """
        package com.predic8.membrane.demo;
        import com.predic8.membrane.annot.*;

        @MCElement(name="demo")
        public class DemoElement {
            @MCChildElement(order = 1)
            public void setAbstract(AbstractChild c) {}

            @MCChildElement(order = 2)
            public void setConcrete(ConcreteChild c) {}
        }
        ---
        package com.predic8.membrane.demo;

        public abstract class AbstractChild {
        }
        ---
        package com.predic8.membrane.demo;
        import com.predic8.membrane.annot.*;

        @MCElement(name="child", topLevel = false, id = "child")
        public class ConcreteChild extends AbstractChild {
        }
        """);
        var result = CompilerHelper.compile(sources, false);

        assertCompilerResult(false, of(error("Name clash: 'child' used by childElement 'abstract' & childElement 'concrete'")), result);
    }

    @Test
    public void childNameNotUniqueAcrossPackages_noList() {
        var sources = splitSources(MC_MAIN_DEMO + """
        package com.predic8.membrane.demo;
        import com.predic8.membrane.annot.*;

        @MCElement(name="demo")
        public class DemoElement {
            @MCChildElement
            public void setChild(AbstractChildElement c) {}
        }
        ---
        package com.predic8.membrane.demo;

        public abstract class AbstractChildElement {
        }
        ---
        package com.predic8.membrane.demo.a;
        import com.predic8.membrane.annot.*;
        import com.predic8.membrane.demo.AbstractChildElement;

        @MCElement(name="child", topLevel = false, id = "child1")
        public class ChildA extends AbstractChildElement {
        }
        ---
        package com.predic8.membrane.demo.b;
        import com.predic8.membrane.annot.*;
        import com.predic8.membrane.demo.AbstractChildElement;

        @MCElement(name="child", topLevel = false, id = "child2")
        public class ChildB extends AbstractChildElement {
        }
        """);
        var result = CompilerHelper.compile(sources, false);

        assertCompilerResult(false, of(error("Duplicate childElement 'child': child")), result);
    }

    @Test
    public void sameChildNameAsSetter() {
        var sources = splitSources(MC_MAIN_DEMO + """
        package com.predic8.membrane.demo;
        import com.predic8.membrane.annot.*;
        import java.util.List;

        @MCElement(name="demo")
        public class DemoElement {
            @MCChildElement(order = 1)
            public void setA(List<Child> c) {}

            @MCChildElement(order = 2)
            public void setB(Child c) {}

        }
        ---
        package com.predic8.membrane.demo;
        import com.predic8.membrane.annot.*;

        @MCElement(name="a", topLevel = false, id = "child")
        public class Child {
        }
        """);
        var result = CompilerHelper.compile(sources, false);

        assertCompilerResult(false, of(error("Name clash: 'a' used by childElement 'a' & childElement 'b'")), result);
    }

}