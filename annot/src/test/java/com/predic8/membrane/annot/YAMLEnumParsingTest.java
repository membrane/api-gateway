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
import com.predic8.membrane.annot.util.CompilerResult;
import org.junit.jupiter.api.Test;

import static com.predic8.membrane.annot.SpringConfigurationXSDGeneratingAnnotationProcessorTest.MC_MAIN_DEMO;
import static com.predic8.membrane.annot.util.CompilerHelper.*;
import static com.predic8.membrane.annot.util.StructureAssertionUtil.*;
import static com.predic8.membrane.annot.util.StructureAssertionUtil.convertedToString;

public class YAMLEnumParsingTest {

    private static final String TRIVIAL_ENUM_EXAMPLE = """
        package com.predic8.membrane.demo;
        import com.predic8.membrane.annot.*;
        import java.util.List;
        @MCElement(name="root", topLevel=true, component=false)
        public class DemoElement {
            MyEnum value;
        
            public MyEnum getValue() { return value; }
            @MCAttribute
            public void setValue(MyEnum value) { this.value = value; }
        }
        ---
        package com.predic8.membrane.demo;
        public enum MyEnum {
            VALUE1, VALUE2;
        }
        """;

    @Test
    public void checkEnumParsing() {
        var sources = splitSources(MC_MAIN_DEMO + TRIVIAL_ENUM_EXAMPLE);
        var result = CompilerHelper.compile(sources, false);
        assertCompilerResult(true, result);

        assertThatXMLValueWasTranslatedTo(result, "value1", "VALUE1");
        assertThatXMLValueWasTranslatedTo(result, "VALUE1", "VALUE1");
    }

    private static void assertThatXMLValueWasTranslatedTo(CompilerResult result, String xmlValue, String expectedToStringValue) {
        BeanRegistry br = parseYAML(result, """
                root:
                  value: %s""".formatted(xmlValue));

        assertStructure(
                br,
                clazz("DemoElement",
                        property("value", convertedToString(expectedToStringValue))));
    }

}
