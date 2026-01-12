package com.predic8.membrane.annot.generator;

import com.predic8.membrane.annot.model.MainInfo;
import com.predic8.membrane.annot.model.Model;
import org.jetbrains.annotations.NotNull;

import javax.annotation.processing.FilerException;
import javax.annotation.processing.ProcessingEnvironment;
import java.io.IOException;
import java.io.Writer;

public abstract class ClassGenerator {

    public static final String COPYRIGHT = """
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
            
            """;

    private final ProcessingEnvironment processingEnv;

    public ClassGenerator(ProcessingEnvironment processingEnv) {
        this.processingEnv = processingEnv;
    }

    /**
     * @return true if the file was written, false if it already existed
     */
    public boolean writeJava(Model m) throws IOException {
        for (MainInfo main : m.getMains()) {
            try {
                try (Writer w = processingEnv.getFiler().createSourceFile(getFileName(main)).openWriter()) {
                    w.write(COPYRIGHT);
                    w.write(getPackage(main));
                    w.write(getClassImpl());
                }
                return true;
            } catch (FilerException e) {
                String msg = e.getMessage();
                if (msg != null && (msg.contains("Source file already created")
                        || msg.contains("Attempt to recreate a file for"))) {
                    return false;
                }
                throw e;
            }
        }
        return false;
    }

    private @NotNull String getFileName(MainInfo main) {
        return main.getAnnotation().outputPackage() + "." + getClassName();
    }

    private static @NotNull String getPackage(MainInfo main) {
        return "package " + main.getAnnotation().outputPackage() + ";\r\n";
    }

    protected abstract String getClassName();

    protected abstract String getClassImpl();
}
