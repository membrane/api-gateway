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

import com.predic8.membrane.annot.yaml.*;
import org.hamcrest.*;
import org.hamcrest.collection.*;
import org.jetbrains.annotations.*;

import javax.tools.*;
import java.io.*;
import java.lang.reflect.*;
import java.util.*;
import java.util.regex.*;
import java.util.regex.Matcher;
import java.util.stream.*;

import static java.util.List.*;
import static java.util.stream.StreamSupport.*;
import static javax.tools.StandardLocation.*;
import static org.hamcrest.MatcherAssert.*;
import static org.junit.jupiter.api.Assertions.*;

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
                of("-processor", ANNOTATION_PROCESSOR_CLASSNAME),
                null,
                javaSources
        );

        boolean success = task.call();

        if (logCompilerOutput)
            diagnostics.getDiagnostics().forEach(System.err::println);

        return new CompilerResult(success, diagnostics, fileManager.getClassLoader(CLASS_OUTPUT));
    }

    public static BeanRegistry parseYAML(CompilerResult cr, String yamlConfig) {
        ClassLoader original = Thread.currentThread().getContextClassLoader();
        CompositeClassLoader cl = getCompositeClassLoader(cr, yamlConfig);
        try {
            Thread.currentThread().setContextClassLoader(cl);
            Class<?> parserClass = cl.loadClass(YAML_PARSER_CLASS_NAME);
            return getBeanRegistry(parserClass,getParser(parserClass));
        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            Thread.currentThread().setContextClassLoader(original);
        }
    }

    private static BeanRegistry getBeanRegistry(Class<?> parserClass, Object instance) throws Exception {
        return (BeanRegistry) parserClass
                .getMethod("getBeanRegistry")
                .invoke(instance);
    }

    private static Class<?> getParserClass(CompilerResult cr, String yamlConfig) throws ClassNotFoundException {
        return getCompositeClassLoader(cr, yamlConfig).loadClass(YAML_PARSER_CLASS_NAME);
    }

    private static @NotNull CompositeClassLoader getCompositeClassLoader(CompilerResult cr, String yamlConfig) {
        InMemoryClassLoader loaderA = (InMemoryClassLoader) cr.classLoader();
        loaderA.defineOverlay(new OverlayInMemoryFile("/demo.yaml", yamlConfig));
        return new CompositeClassLoader(CompilerHelper.class.getClassLoader(), loaderA);
    }

    private static @NotNull Object getYAMLParser(CompositeClassLoader cl) throws InstantiationException, IllegalAccessException, InvocationTargetException, NoSuchMethodException, ClassNotFoundException {
        return getParser(cl.loadClass(YAML_PARSER_CLASS_NAME));
    }

    private static @NotNull Object getParser(Class<?> c) throws InstantiationException, IllegalAccessException, InvocationTargetException, NoSuchMethodException {
        return c.getConstructor(String.class).newInstance("demo.yaml");
    }

    /**
     * Parse the given XML Spring config.
     * TODO Refactor: too much in common with parseYAML
     */
    public static void parse(CompilerResult cr, String xmlSpringConfig) {
        ClassLoader originalClassloader = Thread.currentThread().getContextClassLoader();
        try {
            InMemoryClassLoader loaderA = (InMemoryClassLoader) cr.classLoader();
            loaderA.defineOverlay(new OverlayInMemoryFile("/demo.xml", xmlSpringConfig));
            CompositeClassLoader cl = new CompositeClassLoader(CompilerHelper.class.getClassLoader(),loaderA);
            Thread.currentThread().setContextClassLoader(cl);
            Class<?> c = cl.loadClass(APPLICATION_CONTEXT_CLASSNAME);
            c.getConstructor(String.class).newInstance("demo.xml");
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

        // TODO extract method
        String[] parts;
        while (true) {
            parts = content.split("\n", 2);
            if (parts.length != 2)
                throw new RuntimeException("Invalid resource file: %s. The resource is expected to have the format 'resource <path>\n<content>'.".formatted(content));
            if (!parts[0].isEmpty())
                break;
            content = parts[1];
        }

        String name = parts[0].substring(9).trim(); // TODO Refactor and give meaningful name
        return new OverlayInMemoryFile(name, parts[1]);
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
        return compilerResult(Diagnostic.Kind.WARNING, text);
    }

    public static org.hamcrest.Matcher<Diagnostic<?>> error(String text) {
        return compilerResult(Diagnostic.Kind.ERROR, text);
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
