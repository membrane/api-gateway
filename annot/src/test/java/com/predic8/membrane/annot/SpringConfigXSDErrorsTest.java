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

}
