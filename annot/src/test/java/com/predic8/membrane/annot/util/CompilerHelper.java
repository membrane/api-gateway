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
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public class CompilerHelper {
    public static CompilerResult compile(Iterable<? extends JavaFileObject> sources) {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) {
            throw new IllegalStateException("No system Java compiler found. Run tests with a JDK, not a JRE.");
        }
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

    public static List<JavaFileObject> splitSources(String sources) {
        return Stream.of(sources.split("---"))
                .map(CompilerHelper::toInMemoryJavaFile)
                .toList();
    }

    private static JavaFileObject toInMemoryJavaFile(String source) {
        String pkg = extractPackage(source);
        String cls = extractName(source);
        System.out.println("PACKAGE: " + pkg);
        System.out.println("CLASS: " + cls);
        System.out.println("SOURCE: " + source);
        return new InMemoryJavaFile(pkg + "." + cls, source);
    }

    private static String extractName(String source) {
        Matcher m = Pattern.compile("class\\s+([^\\s]+)\\s").matcher(source);
        if (!m.find())
            throw new RuntimeException("No class name found in source:\n" + source);
        return m.group(1);
    }

    private static String extractPackage(String source) {
        Matcher m = Pattern.compile("package\\s+([^;]+)\\s*;").matcher(source);

        if (!m.find())
            throw new RuntimeException("No package found in source:\n" + source);
        return m.group(1);
    }

}
