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

import org.hamcrest.BaseMatcher;
import org.hamcrest.Description;
import org.hamcrest.collection.IsIterableContainingInAnyOrder;

import javax.tools.*;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static java.util.List.of;
import static java.util.stream.StreamSupport.stream;
import static javax.tools.StandardLocation.CLASS_OUTPUT;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class CompilerHelper {
    /**
     * Compile the given source files.
     *
     * @param sourceFiles the source files to compile
     * @param logCompilerOutput if true, print the compiler output to stderr
     */
    public static CompilerResult compile(Iterable<? extends FileObject> sourceFiles, boolean logCompilerOutput) {
        var javaSources = getJavaSources(sourceFiles);
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) {
            throw new IllegalStateException("No system Java compiler found. Run tests with a JDK, not a JRE.");
        }
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        JavaFileManager fileManager = new LoggingInMemoryJavaFileManager(compiler.getStandardFileManager(diagnostics, null, null));

        copyResourcesToOutput(getResources(sourceFiles), fileManager);

        JavaCompiler.CompilationTask task = compiler.getTask(
                null,
                fileManager,
                diagnostics,
                of("-processor", "com.predic8.membrane.annot.SpringConfigurationXSDGeneratingAnnotationProcessor"),
                null,
                javaSources
        );

        boolean success = task.call();

        if (logCompilerOutput)
            diagnostics.getDiagnostics().forEach(System.err::println);

        return new CompilerResult(success, diagnostics, fileManager.getClassLoader(CLASS_OUTPUT));
    }

    public static Object parseYAML(CompilerResult cr, String yamlConfig) {
        ClassLoader originalClassloader = Thread.currentThread().getContextClassLoader();
        try {
            InMemoryClassLoader loaderA = (InMemoryClassLoader) cr.classLoader();
            loaderA.defineOverlay(new OverlayInMemoryFile("/demo.yaml", yamlConfig));
            CompositeClassLoader cl = new CompositeClassLoader(loaderA, CompilerHelper.class.getClassLoader());
            Thread.currentThread().setContextClassLoader(cl);
            Class<?> c = cl.loadClass("com.predic8.membrane.annot.util.YamlParser");
            Object parser = c.getConstructor(String.class).newInstance("/demo.yaml");
            return c.getMethod("getResult").invoke(parser);
        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            Thread.currentThread().setContextClassLoader(originalClassloader);
        }
    }

    /**
     * Parse the given XML Spring config.
     */
    public static void parse(CompilerResult cr, String xmlSpringConfig) {
        ClassLoader originalClassloader = Thread.currentThread().getContextClassLoader();
        try {
            InMemoryClassLoader loaderA = (InMemoryClassLoader) cr.classLoader();
            loaderA.defineOverlay(new OverlayInMemoryFile("/demo.xml", xmlSpringConfig));
            CompositeClassLoader cl = new CompositeClassLoader(loaderA, CompilerHelper.class.getClassLoader());
            Thread.currentThread().setContextClassLoader(cl);
            Class<?> c = cl.loadClass("org.springframework.context.support.ClassPathXmlApplicationContext");
            c.getConstructor(String.class).newInstance("/demo.xml");
        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            Thread.currentThread().setContextClassLoader(originalClassloader);
        }
    }

    private static List<JavaFileObject> getJavaSources(Iterable<? extends FileObject> sources) {
        return stream(sources.spliterator(), false)
                .filter(i -> i instanceof JavaFileObject)
                .map(i -> (JavaFileObject) i)
                .toList();
    }

    private static List<OverlayInMemoryFile> getResources(Iterable<? extends FileObject> sources) {
        return stream(sources.spliterator(), false)
                .filter(i -> i instanceof OverlayInMemoryFile)
                .map(i -> (OverlayInMemoryFile) i)
                .toList();
    }

    private static void copyResourcesToOutput(List<? extends OverlayInMemoryFile> sources, JavaFileManager fileManager) {
        sources.forEach(i -> {
                    PrintWriter pw = null;
                    try {
                        pw = new PrintWriter(fileManager.getFileForOutput(CLASS_OUTPUT, "", i.getName(), null)
                                .openWriter());
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                    pw.write(i.getCharContent(true).toString());
                    pw.close();
                });
    }

    public static List<FileObject> splitSources(String sources) {
        return Stream.of(sources.split("---"))
                .filter(s -> !s.isBlank())
                .map(CompilerHelper::toFile)
                .toList();
    }

    private static FileObject toFile( String content) {
        if (!content.trim().startsWith("resource"))
            return toInMemoryJavaFile(content);

        String[] parts;
        while(true) {
            parts = content.split("\n", 2);
            if (parts.length != 2)
                throw new RuntimeException("Invalid resource file: " + content + ". The resource is expected to have the format 'resource <path>\n<content>'.");
            if (!parts[0].isEmpty())
                break;
            content = parts[1];
        };

        String name = parts[0].substring(9).trim();
        return new OverlayInMemoryFile(name, parts[1]);
    }

    private static JavaFileObject toInMemoryJavaFile(String source) {
        String pkg = extractPackage(source);
        String cls = extractName(source);
        return new OverlayInMemoryJavaFile(pkg + "." + cls, source);
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

    public static void assertCompilerResult(boolean success, CompilerResult result) {
        assertCompilerResult(success, new ArrayList<>(), result);
    }

    public static void assertCompilerResult(boolean success,
                                            List<org.hamcrest.Matcher<? super Diagnostic<?>>> expectedDiagnostics,
                                            CompilerResult result) {
        assertThat("expected errors and warnings match.",
                result.diagnostics().getDiagnostics(),
                new IsIterableContainingInAnyOrder<Diagnostic<?>>(expectedDiagnostics));
        assertEquals(success, result.compilationSuccess());
    }

    public static org.hamcrest.Matcher<Diagnostic<?>> warning(String text) {
        return compilerResult(Diagnostic.Kind.WARNING, text);
    }

    public static org.hamcrest.Matcher<Diagnostic<?>> error(String text) {
        return compilerResult(Diagnostic.Kind.ERROR, text);
    }

    public static org.hamcrest.Matcher<Diagnostic<?>> compilerResult(Diagnostic.Kind kind, String text) {
        return new BaseMatcher<Diagnostic<?>>() {

            @Override
            public void describeTo(Description description) {
                description.appendText("is '" + text + "'");
            }

            @Override
            public boolean matches(Object o) {
                if (o instanceof Diagnostic<?> d) {
                    return d.getKind() == kind && d.getMessage(null).equals(text);
                }
                return false;
            }

        };
    }

}
