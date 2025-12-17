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

import com.predic8.membrane.annot.yaml.BeanRegistry;
import org.hamcrest.BaseMatcher;
import org.hamcrest.Description;
import org.hamcrest.collection.IsIterableContainingInAnyOrder;
import org.jetbrains.annotations.NotNull;

import javax.tools.*;
import java.io.IOException;
import java.io.PrintWriter;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static java.util.List.of;
import static java.util.stream.StreamSupport.stream;
import static javax.tools.Diagnostic.Kind.ERROR;
import static javax.tools.Diagnostic.Kind.WARNING;
import static javax.tools.StandardLocation.CLASS_OUTPUT;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class CompilerHelper {

    public static final String YAML_PARSER_CLASS_NAME = "com.predic8.membrane.annot.util.YamlParser";
    private static final Pattern PACKAGE_PATTERN = Pattern.compile("package\\s+([^;]+)\\s*;");
    private static final Pattern CLASS_PATTERN = Pattern.compile("class\\s+([^\\s]+)\\s");
    public static final String ANNOTATION_PROCESSOR_CLASSNAME = "com.predic8.membrane.annot.SpringConfigurationXSDGeneratingAnnotationProcessor";
    public static final String APPLICATION_CONTEXT_CLASSNAME = "org.springframework.context.support.ClassPathXmlApplicationContext";

    /**
     * Compile the given source files.
     *
     * @param sourceFiles       the source files to compile
     * @param logCompilerOutput if true, print the compiler output to stderr
     */
    public static CompilerResult compile(Iterable<? extends FileObject> sourceFiles, boolean logCompilerOutput) {
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
                of("-processor", ANNOTATION_PROCESSOR_CLASSNAME),
                null,
                getJavaSources(sourceFiles)
        );

        boolean success = task.call();

        if (logCompilerOutput)
            diagnostics.getDiagnostics().forEach(System.err::println);

        return new CompilerResult(success, diagnostics, fileManager.getClassLoader(CLASS_OUTPUT));
    }

    public static BeanRegistry parseYAML(CompilerResult cr, String yamlConfig) {
        CompositeClassLoader cl = getCompositeClassLoader(cr, yamlConfig);
        return withContextClassLoader(cl, () -> {
            Class<?> parserClass = cl.loadClass(YAML_PARSER_CLASS_NAME);
            return getBeanRegistry(parserClass, getParser(parserClass));
        });
    }

    public static void parseXML(CompilerResult cr, String xmlSpringConfig) {
        CompositeClassLoader cl = xmlClassLoader(cr, xmlSpringConfig);
        withContextClassLoader(cl, () -> {
            Class<?> ctx = cl.loadClass(APPLICATION_CONTEXT_CLASSNAME);
            ctx.getConstructor(String.class).newInstance("demo.xml");
            return null;
        });
    }

    @FunctionalInterface
    private interface ThrowingSupplier<T> {
        T get() throws Exception;
    }

    private static <T> T withContextClassLoader(ClassLoader cl, ThrowingSupplier<T> action) {
        ClassLoader original = Thread.currentThread().getContextClassLoader();
        try {
            Thread.currentThread().setContextClassLoader(cl);
            return action.get();
        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            Thread.currentThread().setContextClassLoader(original);
        }
    }

    private static CompositeClassLoader xmlClassLoader(CompilerResult cr, String xmlSpringConfig) {
        InMemoryClassLoader inMemory = (InMemoryClassLoader) cr.classLoader();
        inMemory.defineOverlay(new OverlayInMemoryFile("/demo.xml", xmlSpringConfig));
        return new CompositeClassLoader(CompilerHelper.class.getClassLoader(), inMemory);
    }

    private static BeanRegistry getBeanRegistry(Class<?> parserClass, Object instance) throws Exception {
        return (BeanRegistry) parserClass
                .getMethod("getBeanRegistry")
                .invoke(instance);
    }

    private static @NotNull CompositeClassLoader getCompositeClassLoader(CompilerResult cr, String yamlConfig) {
        InMemoryClassLoader loaderA = (InMemoryClassLoader) cr.classLoader();
        loaderA.defineOverlay(new OverlayInMemoryFile("/demo.yaml", yamlConfig));
        return new CompositeClassLoader(CompilerHelper.class.getClassLoader(), loaderA);
    }

    private static @NotNull Object getParser(Class<?> c) throws InstantiationException, IllegalAccessException, InvocationTargetException, NoSuchMethodException {
        return c.getConstructor(String.class).newInstance("demo.yaml");
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

    private static void copyResourcesToOutput(List<? extends OverlayInMemoryFile> sources,
                                              JavaFileManager fileManager) {
        sources.forEach(i -> {
            try (PrintWriter pw = new PrintWriter(
                    fileManager.getFileForOutput(CLASS_OUTPUT, "", i.getName(), null)
                            .openWriter())) {
                pw.write(i.getCharContent(true).toString());
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
    }

    public static List<FileObject> splitSources(String sources) {
        return Stream.of(sources.split("---"))
                .filter(s -> !s.isBlank())
                .map(CompilerHelper::toFile)
                .toList();
    }

    private static FileObject toFile(String content) {
        if (!content.trim().startsWith("resource"))
            return toInMemoryJavaFile(content);

        String[] parts = stripFirstLine(content);

        return new OverlayInMemoryFile(parts[0].substring("resource".length()).trim(), parts[1]);
    }


    static String @NotNull [] stripFirstLine(String content) {
        String[] parts;
        while (true) {
            parts = content.split("\n", 2);
            if (parts.length != 2)
                throw new RuntimeException("Invalid resource file: %s. The resource is expected to have the format 'resource <path>\n<content>'.".formatted(content));
            if (!parts[0].isEmpty())
                break;
            content = parts[1];
        }
        return parts;
    }

    private static JavaFileObject toInMemoryJavaFile(String source) {
        return new OverlayInMemoryJavaFile(extractPackage(source) + "." + extractName(source), source);
    }

    private static String extractName(String source) {
        Matcher m = CLASS_PATTERN.matcher(source);
        if (!m.find())
            throw new RuntimeException("No class name found in source:\n" + source);
        return m.group(1);
    }

    private static String extractPackage(String source) {
        Matcher m = PACKAGE_PATTERN.matcher(source);

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
                new IsIterableContainingInAnyOrder<>(expectedDiagnostics));
        assertEquals(success, result.compilationSuccess());
    }

    public static org.hamcrest.Matcher<Diagnostic<?>> warning(String text) {
        return compilerResult(WARNING, text);
    }

    public static org.hamcrest.Matcher<Diagnostic<?>> error(String text) {
        return compilerResult(ERROR, text);
    }

    public static org.hamcrest.Matcher<Diagnostic<?>> compilerResult(Diagnostic.Kind kind, String text) {
        return new BaseMatcher<>() {

            @Override
            public void describeTo(Description description) {
                description.appendText("is '%s'".formatted(text));
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
