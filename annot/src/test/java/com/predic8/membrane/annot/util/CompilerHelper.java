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
package com.predic8.membrane.annot.util;

import javax.tools.*;
import java.util.List;

public class CompilerHelper {
    public static CompilerResult run(Iterable<? extends JavaFileObject> sources) {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        JavaFileManager fileManager = new CustomJavaFileManager(compiler.getStandardFileManager(diagnostics, null, null));

        JavaCompiler.CompilationTask task = compiler.getTask(
                null,
                fileManager,
                diagnostics,
                List.of("-processor", "com.predic8.membrane.annot.SpringConfigurationXSDGeneratingAnnotationProcessor"),
                null,
                sources
        );

        boolean success = task.call();

        diagnostics.getDiagnostics().forEach(System.err::println);

        return new CompilerResult(success, diagnostics);
    }
}
