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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.predic8.membrane.annot.model.*;
import com.predic8.membrane.annot.model.doc.Doc;
import org.jetbrains.annotations.NotNull;

import javax.annotation.processing.FilerException;
import javax.annotation.processing.ProcessingEnvironment;
import javax.tools.FileObject;
import java.io.BufferedWriter;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_EMPTY;
import static com.fasterxml.jackson.annotation.JsonInclude.Value.construct;
import static com.fasterxml.jackson.core.JsonGenerator.Feature.AUTO_CLOSE_TARGET;
import static com.fasterxml.jackson.dataformat.yaml.YAMLGenerator.Feature.LITERAL_BLOCK_STYLE;
import static com.fasterxml.jackson.dataformat.yaml.YAMLGenerator.Feature.WRITE_DOC_START_MARKER;
import static com.predic8.membrane.annot.Constants.VERSION;
import static javax.tools.StandardLocation.CLASS_OUTPUT;

public class YamlDocsGenerator {

    private static final int DOC_FORMAT_VERSION = 1;
    private static final String OUTPUT_FILE = "docs.yaml";

    private static final ObjectMapper YAML_MAPPER = new ObjectMapper(
            new YAMLFactory().disable(WRITE_DOC_START_MARKER).enable(LITERAL_BLOCK_STYLE).disable(AUTO_CLOSE_TARGET)
    ).setDefaultPropertyInclusion(construct(NON_EMPTY, NON_EMPTY));

    private final ProcessingEnvironment processingEnv;

    public YamlDocsGenerator(ProcessingEnvironment processingEnv) {
        this.processingEnv = processingEnv;
    }

    public void write(Model m) throws IOException {
        Map<String, Map<String, ElementDoc>> byNamespace = collectMains(m);
        if (byNamespace.isEmpty()) return;

        try {
            FileObject o = processingEnv.getFiler().createResource(CLASS_OUTPUT, "docs", OUTPUT_FILE);
            try (BufferedWriter w = new BufferedWriter(o.openWriter())) {
                YAML_MAPPER.writeValue(w, buildRoot(byNamespace));
                w.write("\n");
            }
        } catch (FilerException e) {
            if (e.getMessage() != null && e.getMessage().contains("Source file already created")) return;
            throw e;
        }
    }

    private Object buildRoot(Map<String, Map<String, ElementDoc>> byNamespace) {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("docFormatVersion", DOC_FORMAT_VERSION);
        root.put("membraneVersion", VERSION);

        Map<String, Object> schemas = new LinkedHashMap<>();

        for (var nsEntry : byNamespace.entrySet()) {
            Map<String, Object> ns = new LinkedHashMap<>();

            Map<String, Object> elements = new LinkedHashMap<>();
            for (var elEntry : nsEntry.getValue().entrySet()) {
                ElementDoc ed = elEntry.getValue();

                Map<String, Object> el = new LinkedHashMap<>();

                if (ed.name != null && !ed.name.isBlank()) {
                    el.put("name", ed.name);
                }
                if (ed.doc != null && !ed.doc.isEmpty()) {
                    el.put("doc", ed.doc);
                }
                if (ed.attributes != null && !ed.attributes.isEmpty()) {
                    el.put("attributes", wrapDocMaps(ed.attributes));
                }
                if (ed.children != null && !ed.children.isEmpty()) {
                    el.put("children", wrapDocMaps(ed.children));
                }
                if (ed.otherAttributes != null && !ed.otherAttributes.isEmpty()) {
                    Map<String, Object> oa = new LinkedHashMap<>();
                    oa.put("doc", ed.otherAttributes);
                    el.put("otherAttributes", oa);
                }

                elements.put(elEntry.getKey(), el);
            }

            ns.put("elements", elements);
            schemas.put(nsEntry.getKey(), ns);
        }

        root.put("schemas", schemas);
        return root;
    }

    private Map<String, Object> wrapDocMaps(Map<String, Map<String, String>> in) {
        Map<String, Object> out = new LinkedHashMap<>();
        for (var e : in.entrySet()) {
            Map<String, Object> v = new LinkedHashMap<>();
            v.put("doc", e.getValue());
            out.put(e.getKey(), v);
        }
        return out;
    }

    private Map<String, Map<String, ElementDoc>> collectMains(Model m) {
        Map<String, Map<String, ElementDoc>> out = new TreeMap<>();

        for (MainInfo main : m.getMains()) {
            collect(main, out);
        }

        out.entrySet().removeIf(e -> e.getValue().isEmpty());
        return out;
    }

    private void collect(MainInfo main, Map<String, Map<String, ElementDoc>> out) {
        Map<String, ElementDoc> elements = out.computeIfAbsent(main.getAnnotation().targetNamespace(), __ -> new TreeMap<>());

        for (ElementInfo ei : main.getElements().values()) {
            Map<String, String> elementDoc = docMap(ei.getDoc(processingEnv));
            Map<String, Map<String, String>> attrs = attributeDocs(ei);
            Map<String, Map<String, String>> children = childDocs(ei);
            Map<String, String> otherAttrs = otherAttributesDoc(ei);

            if (!isHasAnyDoc(elementDoc, attrs, children, otherAttrs)) continue;

            elements.put(ei.getId(), getElementDoc(ei, elementDoc, attrs, children, otherAttrs));
        }
    }

    private static boolean isHasAnyDoc(Map<String, String> elementDoc, Map<String, Map<String, String>> attrs, Map<String, Map<String, String>> children, Map<String, String> otherAttrs) {
        return !elementDoc.isEmpty() || !attrs.isEmpty() || !children.isEmpty() || !otherAttrs.isEmpty();
    }

    private static @NotNull ElementDoc getElementDoc(ElementInfo ei, Map<String, String> elementDoc, Map<String, Map<String, String>> attrs, Map<String, Map<String, String>> children, Map<String, String> otherAttrs) {
        ElementDoc ed = new ElementDoc();
        ed.name = ei.getAnnotation() != null ? ei.getAnnotation().name() : null;
        ed.doc = elementDoc;
        ed.attributes = attrs;
        ed.children = children;
        ed.otherAttributes = otherAttrs;
        return ed;
    }

    private Map<String, Map<String, String>> attributeDocs(ElementInfo ei) {
        Map<String, Map<String, String>> out = new TreeMap<>();
        for (AttributeInfo ai : ei.getAis()) {
            Map<String, String> doc = docMap(ai.getDoc(processingEnv));
            if (!doc.isEmpty()) out.put(ai.getXMLName(), doc);
        }
        return out;
    }

    private Map<String, Map<String, String>> childDocs(ElementInfo ei) {
        Map<String, Map<String, String>> out = new TreeMap<>();
        for (ChildElementInfo cei : ei.getChildElementSpecs()) {
            Map<String, String> doc = docMap(cei.getDoc(processingEnv));
            if (!doc.isEmpty()) out.put(cei.getPropertyName(), doc);
        }
        return out;
    }

    private Map<String, String> otherAttributesDoc(ElementInfo ei) {
        if (ei.getOai() == null) return Map.of();
        return docMap(ei.getOai().getDoc(processingEnv));
    }

    private Map<String, String> docMap(Doc doc) {
        if (doc == null) return Map.of();

        Map<String, String> out = new LinkedHashMap<>();
        for (Doc.Entry e : doc.getEntries()) {
            String k = e.getKey();
            String v = e.getValueAsXMLSnippet(false);
            if (v == null) continue;
            v = v.trim();
            if (v.isEmpty()) continue;
            out.put(k, v);
        }
        return out;
    }

    private static final class ElementDoc {
        String name;
        Map<String, String> doc = Map.of();
        Map<String, Map<String, String>> attributes = Map.of();
        Map<String, Map<String, String>> children = Map.of();
        Map<String, String> otherAttributes = Map.of();
    }
}
