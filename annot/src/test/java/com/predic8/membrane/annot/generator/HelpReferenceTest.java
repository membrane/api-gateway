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
package com.predic8.membrane.annot.generator;

import com.predic8.membrane.annot.MCAttribute;
import com.predic8.membrane.annot.MCElement;
import com.predic8.membrane.annot.MCMain;
import com.predic8.membrane.annot.model.AttributeInfo;
import com.predic8.membrane.annot.model.ElementInfo;
import com.predic8.membrane.annot.model.MainInfo;
import com.predic8.membrane.annot.model.Model;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.Element;
import javax.lang.model.util.Elements;
import java.util.List;

import static java.util.Collections.emptyList;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

/**
 * {@link HelpReference} only reports on the {@link Model}; the generators running after it
 * (NamespaceInfo, YamlDocsGenerator, BlueprintParsers) read the same instance, so it must not
 * reorder or remove anything.
 */
class HelpReferenceTest {

    private ProcessingEnvironment processingEnv;

    @BeforeEach
    void setUp() {
        Elements elements = mock(Elements.class);
        when(elements.getDocComment(any())).thenReturn(null);
        processingEnv = mock(ProcessingEnvironment.class);
        when(processingEnv.getElementUtils()).thenReturn(elements);
    }

    @Test
    void writeHelpDoesNotMutateTheModel() {
        ElementInfo ei = elementInfo("target");
        // "id" first, then an attribute sorting before it, so both the sort and the removal would show up
        ei.getAis().add(attributeInfo("id"));
        ei.getAis().add(attributeInfo("async"));

        Model m = model(ei);

        new HelpReference(processingEnv).writeHelp(m);

        assertEquals(List.of("id", "async"), ei.getAis().stream().map(AttributeInfo::getXMLName).toList());
    }

    @Test
    void writeHelpDoesNotReorderElements() {
        ElementInfo second = elementInfo("second");
        ElementInfo first = elementInfo("first");

        Model m = model(second, first);

        new HelpReference(processingEnv).writeHelp(m);

        assertEquals(List.of("second", "first"),
                m.getMains().getFirst().getIis().stream().map(e -> e.getAnnotation().name()).toList());
    }

    private Model model(ElementInfo... eis) {
        MCMain mcMain = mock(MCMain.class);
        when(mcMain.outputPackage()).thenReturn("com.example");
        when(mcMain.targetNamespace()).thenReturn("http://example.com/ns");

        MainInfo main = new MainInfo();
        main.setAnnotation(mcMain);
        main.getIis().addAll(List.of(eis));

        Model m = new Model();
        m.getMains().add(main);
        return m;
    }

    private ElementInfo elementInfo(String name) {
        MCElement mcElement = mock(MCElement.class);
        when(mcElement.name()).thenReturn(name);
        when(mcElement.id()).thenReturn("");
        when(mcElement.mixed()).thenReturn(false);
        when(mcElement.component()).thenReturn(true); // skips getPrimaryParentId()

        ElementInfo ei = new ElementInfo();
        ei.setAnnotation(mcElement);
        ei.setDocedE(undocumentedElement());
        return ei;
    }

    private AttributeInfo attributeInfo(String xmlName) {
        MCAttribute mcAttribute = mock(MCAttribute.class);
        when(mcAttribute.attributeName()).thenReturn(xmlName);
        when(mcAttribute.excludeFromJson()).thenReturn(false);

        AttributeInfo ai = new AttributeInfo();
        ai.setAnnotation(mcAttribute);
        ai.setDocedE(undocumentedElement());
        return ai;
    }

    private Element undocumentedElement() {
        Element e = mock(Element.class);
        doReturn(emptyList()).when(e).getAnnotationMirrors();
        return e;
    }
}
