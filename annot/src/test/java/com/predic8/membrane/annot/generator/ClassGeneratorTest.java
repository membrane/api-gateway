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

import com.predic8.membrane.annot.MCMain;
import com.predic8.membrane.annot.model.MainInfo;
import com.predic8.membrane.annot.model.Model;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.annotation.processing.Filer;
import javax.annotation.processing.FilerException;
import javax.annotation.processing.ProcessingEnvironment;
import javax.tools.JavaFileObject;
import java.io.IOException;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * {@link ClassGenerator} writes one class per {@code @MCMain}. Its boolean result drives the
 * processor's round machinery: true means "a file was written, call me again once it exists".
 */
class ClassGeneratorTest {

    private static final String ONE = "com.predic8.membrane.one.config.spring";
    private static final String TWO = "com.predic8.membrane.two.config.spring";

    /** File name -> what was written to it. */
    private Map<String, StringWriter> written;
    /** File names the Filer should reject as already created. */
    private final List<String> alreadyCreated = new ArrayList<>();
    private ClassGenerator generator;

    @BeforeEach
    void setUp() throws IOException {
        written = new LinkedHashMap<>();

        Filer filer = mock(Filer.class);
        when(filer.createSourceFile(any(CharSequence.class))).thenAnswer(invocation -> {
            String name = invocation.getArgument(0).toString();
            if (alreadyCreated.contains(name))
                throw new FilerException("Attempt to recreate a file for type " + name);
            StringWriter sw = new StringWriter();
            written.put(name, sw);
            JavaFileObject file = mock(JavaFileObject.class);
            when(file.openWriter()).thenReturn(sw);
            return file;
        });

        ProcessingEnvironment processingEnv = mock(ProcessingEnvironment.class);
        when(processingEnv.getFiler()).thenReturn(filer);

        generator = new ClassGenerator(processingEnv) {
            @Override
            protected String getClassName() {
                return "Components";
            }

            @Override
            protected String getClassImpl() {
                return "public class Components {}";
            }
        };
    }

    private static Model modelWith(String... outputPackages) {
        Model m = new Model();
        for (String outputPackage : outputPackages) {
            MCMain annotation = mock(MCMain.class);
            when(annotation.outputPackage()).thenReturn(outputPackage);
            MainInfo main = new MainInfo();
            main.setAnnotation(annotation);
            m.getMains().add(main);
        }
        return m;
    }

    @Test
    void everyMainGetsItsOwnClass() throws IOException {
        assertTrue(generator.writeJava(modelWith(ONE, TWO)));

        assertEquals(List.of(ONE + ".Components", TWO + ".Components"), List.copyOf(written.keySet()));
        assertTrue(written.get(TWO + ".Components").toString().contains("package " + TWO + ";"));
    }

    @Test
    void aMainWhoseFileExistsDoesNotStopTheRemainingMains() throws IOException {
        alreadyCreated.add(ONE + ".Components");

        assertTrue(generator.writeJava(modelWith(ONE, TWO)));

        assertEquals(List.of(TWO + ".Components"), List.copyOf(written.keySet()));
    }

    @Test
    void nothingWrittenWhenEveryFileExists() throws IOException {
        alreadyCreated.add(ONE + ".Components");
        alreadyCreated.add(TWO + ".Components");

        assertFalse(generator.writeJava(modelWith(ONE, TWO)));
        assertTrue(written.isEmpty());
    }

    @Test
    void anUnrelatedFilerFailureIsNotSwallowed() {
        Model m = modelWith(ONE);
        alreadyCreated.clear();
        FilerException real = new FilerException("Illegal name " + ONE);
        assertThrows(FilerException.class, () -> {
            Filer filer = mock(Filer.class);
            when(filer.createSourceFile(any(CharSequence.class))).thenThrow(real);
            ProcessingEnvironment pe = mock(ProcessingEnvironment.class);
            when(pe.getFiler()).thenReturn(filer);
            new ClassGenerator(pe) {
                @Override
                protected String getClassName() {
                    return "Components";
                }

                @Override
                protected String getClassImpl() {
                    return "";
                }
            }.writeJava(m);
        });
    }
}
