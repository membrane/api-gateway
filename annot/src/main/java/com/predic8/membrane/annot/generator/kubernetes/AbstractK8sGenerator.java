package com.predic8.membrane.annot.generator.kubernetes;

import com.predic8.membrane.annot.model.ElementInfo;
import com.predic8.membrane.annot.model.MainInfo;
import com.predic8.membrane.annot.model.Model;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.Element;
import javax.tools.FileObject;
import javax.tools.StandardLocation;
import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Bundles functionality for kubernetes file generation
 */
public abstract class AbstractK8sGenerator {

    protected final ProcessingEnvironment processingEnv;

    protected class WritableNames {
        String className;
        String name;
        String pluralName;

        public WritableNames(ElementInfo ei) {
            className = ei.getElement().getSimpleName().toString();
            name = ei.getAnnotation().name().toLowerCase();
            pluralName = pluralize(name);
        }
    }

    public AbstractK8sGenerator(final ProcessingEnvironment processingEnv) {
        this.processingEnv = processingEnv;
    }

    protected abstract String fileName();
    protected abstract void write(Model m);

    protected WritableNames getElementNames(ElementInfo ei) {
        return new WritableNames(ei);
    }

    protected Stream<ElementInfo> getRulesStream(MainInfo main) {
        return main.getElements().values().stream()
                .filter(ei -> {
                    String[] packages = ei.getElement().getQualifiedName().toString().split("\\.");
                    return packages[packages.length - 2].equals("rules") && !packages[packages.length - 1].startsWith("Abstract");
                });
    }

    protected List<ElementInfo> getRules(MainInfo main) {
        return getRulesStream(main)
                .collect(Collectors.toList());
    }

    protected FileObject createFileObject(MainInfo main, String fileName) throws IOException {
        List<Element> sources = new ArrayList<>(main.getInterceptorElements());
        sources.add(main.getElement());

        return processingEnv.getFiler()
                .createResource(
                        StandardLocation.CLASS_OUTPUT,
                        "com.predic8.membrane.core.config.kubernetes",
                        fileName,
                        sources.toArray(new Element[0])
                );
    }

    public String pluralize(String singular) {
        if (singular.endsWith("s")) {
            return singular;
        }

        if (singular.endsWith("y")) {
            singular = singular.substring(0, singular.length() - 1) + "ie";
        }

        return singular + "s";
    }

    protected void appendLine(Writer w, String... lines) throws IOException {
        String[] passed = Arrays.copyOf(lines, lines.length + 1);
        passed[lines.length] = "";
        w.append(String.join(System.lineSeparator(), passed));
    }
}
