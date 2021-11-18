/* Copyright 2009, 2021 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */
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
                .filter(ei -> ei.getAnnotation().topLevel());
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
        if (singular.endsWith("s") || singular.endsWith("sh") || singular.endsWith("ch") || singular.endsWith("x") || singular.endsWith("z") ) {
            return singular + "es";
        }

        if (singular.endsWith("ao") || singular.endsWith("oo") || singular.endsWith("uo") || singular.endsWith("eo") || singular.endsWith("io"))
            return singular + "s";

        if (singular.endsWith("o"))
            return singular + "es"; // could also just be "s" - but who knows

        if (singular.endsWith("ay") || singular.endsWith("oy") || singular.endsWith("uy") || singular.endsWith("ey") || singular.endsWith("iy"))
            return singular + "s";

        if (singular.endsWith("y"))
            return singular.substring(0, singular.length() - 1) + "ies";

        return singular + "s";
    }

    protected void appendLine(Writer w, String... lines) throws IOException {
        String[] passed = Arrays.copyOf(lines, lines.length + 1);
        passed[lines.length] = "";
        w.append(String.join(System.lineSeparator(), passed));
    }
}
