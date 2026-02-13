package com.predic8.membrane.annot.generator;

import com.predic8.membrane.annot.model.*;
import com.predic8.membrane.annot.model.doc.Doc;

import javax.annotation.processing.FilerException;
import javax.annotation.processing.ProcessingEnvironment;
import javax.tools.FileObject;
import java.io.BufferedWriter;
import java.io.IOException;
import java.util.*;
import java.util.regex.Pattern;

import static com.predic8.membrane.annot.Constants.VERSION;
import static java.lang.Integer.MAX_VALUE;
import static javax.tools.StandardLocation.CLASS_OUTPUT;

public class YamlDocsGenerator {

    private static final int DOC_FORMAT_VERSION = 1;
    private static final String OUTPUT_FILE = "docs.yaml";
    private static final Pattern SIMPLE_KEY = Pattern.compile("[A-Za-z0-9_]+");
    private static final Set<String> YAML_11_RESERVED = Set.of("y", "yes", "n", "no", "true", "false", "on", "off", "null", "~");

    private final ProcessingEnvironment processingEnv;

    public YamlDocsGenerator(ProcessingEnvironment processingEnv) {
        this.processingEnv = processingEnv;
    }

    public void write(Model m) throws IOException {

        Map<String, Map<String, ElementDoc>> byNamespace = collect(m);

        if (byNamespace.isEmpty()) return;

        try {
            FileObject o = processingEnv.getFiler().createResource(CLASS_OUTPUT, "docs", OUTPUT_FILE);
            try (BufferedWriter w = new BufferedWriter(o.openWriter())) {
                writeRoot(w, byNamespace);
            }
        } catch (FilerException e) {
            if (e.getMessage() != null && e.getMessage().contains("Source file already created")) return;
            throw e;
        }
    }

    private Map<String, Map<String, ElementDoc>> collect(Model m) {
        Map<String, Map<String, ElementDoc>> out = new TreeMap<>();

        for (MainInfo main : m.getMains()) {
            Map<String, ElementDoc> elements = out.computeIfAbsent(main.getAnnotation().targetNamespace(), __ -> new TreeMap<>());

            for (ElementInfo ei : main.getElements().values()) {
                String id = ei.getId();

                Map<String, String> elementDoc = docMap(ei.getDoc(processingEnv));
                Map<String, Map<String, String>> attrs = attributeDocs(ei);
                Map<String, Map<String, String>> children = childDocs(ei);
                Map<String, String> otherAttrs = otherAttributesDoc(ei);

                boolean hasAny =
                        !elementDoc.isEmpty()
                                || !attrs.isEmpty()
                                || !children.isEmpty()
                                || !otherAttrs.isEmpty();

                if (!hasAny) continue;

                ElementDoc ed = new ElementDoc();
                ed.name = ei.getAnnotation() != null ? ei.getAnnotation().name() : null;
                ed.doc = elementDoc;
                ed.attributes = attrs;
                ed.children = children;
                ed.otherAttributes = otherAttrs;

                elements.put(id, ed);
            }
        }

        out.entrySet().removeIf(e -> e.getValue().isEmpty());
        return out;
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

        LinkedHashMap<String, String> out = new LinkedHashMap<>();
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

    private void writeRoot(BufferedWriter w, Map<String, Map<String, ElementDoc>> byNamespace) throws IOException {
        w.write("docFormatVersion: " + DOC_FORMAT_VERSION + "\n");
        w.write("membraneVersion: ");
        writeScalar(w, VERSION, 0);
        w.write("\n");
        w.write("schemas:\n");
        for (var nsEntry : byNamespace.entrySet()) {
            w.write(indent(2) + yamlKey(nsEntry.getKey()) + ":\n");
            w.write(indent(4) + "elements:\n");

            for (var elEntry : nsEntry.getValue().entrySet()) {
                ElementDoc ed = elEntry.getValue();

                w.write(indent(6) + yamlKey(elEntry.getKey()) + ":\n");

                if (ed.name != null && !ed.name.isBlank()) {
                    w.write(indent(8) + "name: ");
                    writeScalar(w, ed.name, 8);
                    w.write("\n");
                }

                writeDocBlock(w, "doc", ed.doc, 8);

                if (!ed.attributes.isEmpty()) {
                    w.write(indent(8) + "attributes:\n");
                    for (var a : ed.attributes.entrySet()) {
                        w.write(indent(10) + yamlKey(a.getKey()) + ":\n");
                        writeDocBlock(w, "doc", a.getValue(), 12);
                    }
                }

                if (!ed.children.isEmpty()) {
                    w.write(indent(8) + "children:\n");
                    for (var c : ed.children.entrySet()) {
                        w.write(indent(10) + yamlKey(c.getKey()) + ":\n");
                        writeDocBlock(w, "doc", c.getValue(), 12);
                    }
                }

                if (!ed.otherAttributes.isEmpty()) {
                    w.write(indent(8) + "otherAttributes:\n");
                    writeDocBlock(w, "doc", ed.otherAttributes, 10);
                }
            }
        }
    }

    private void writeDocBlock(BufferedWriter w, String key, Map<String, String> doc, int indent) throws IOException {
        if (doc == null || doc.isEmpty()) return;

        w.write(indent(indent) + key + ":\n");
        for (var e : doc.entrySet()) {
            w.write(indent(indent + 2) + yamlKey(e.getKey()) + ": ");
            writeScalar(w, e.getValue(), indent + 2);
            w.write("\n");
        }
    }

    private void writeScalar(BufferedWriter w, String value, int currentIndent) throws IOException {
        if (value == null) {
            w.write("\"\"");
            return;
        }

        String v = value.replace("\r\n", "\n").replace("\r", "\n");

        if (v.contains("\n")) {
            w.write("|\n");
            int indent = currentIndent + 2;

            List<String> lines = Arrays.asList(v.split("\n", -1));

            int firstNonBlank = -1;
            for (int i = 0; i < lines.size(); i++) {
                if (!lines.get(i).isBlank()) {
                    firstNonBlank = i;
                    break;
                }
            }

            int minAll = MAX_VALUE;
            for (String line : lines) {
                if (line.isBlank()) continue;
                minAll = Math.min(minAll, leadingSpaces(line));
            }
            if (minAll == MAX_VALUE) minAll = 0;

            int strip = minAll;
            boolean skipFirst = false;

            if (strip == 0 && firstNonBlank != -1 && leadingSpaces(lines.get(firstNonBlank)) == 0) {
                int minRest = MAX_VALUE;
                for (int i = 0; i < lines.size(); i++) {
                    if (i == firstNonBlank) continue;
                    String line = lines.get(i);
                    if (line.isBlank()) continue;
                    int lead = leadingSpaces(line);
                    if (lead > 0) minRest = Math.min(minRest, lead);
                }
                if (minRest != MAX_VALUE) {
                    strip = minRest;
                    skipFirst = true;
                }
            }

            for (int i = 0; i < lines.size(); i++) {
                String out = lines.get(i);

                if (!out.isBlank()) {
                    int lead = leadingSpaces(out);
                    boolean shouldStrip = strip > 0 && lead >= strip && !(skipFirst && i == firstNonBlank);
                    if (shouldStrip) out = out.substring(strip);
                }

                w.write(indent(indent));
                w.write(out);
                w.write("\n");
            }
            return;
        }

        if (v.isEmpty()) {
            w.write("\"\"");
            return;
        }

        w.write("'");
        w.write(v.replace("'", "''"));
        w.write("'");
    }

    private int leadingSpaces(String s) {
        int i = 0;
        while (i < s.length() && s.charAt(i) == ' ') i++;
        return i;
    }

    private String yamlKey(String k) {
        if (k == null) return "\"\"";
        String s = k.trim();
        if (s.isEmpty()) return "\"\"";

        if (!SIMPLE_KEY.matcher(s).matches()) {
            return "'" + s.replace("'", "''") + "'";
        }

        if (YAML_11_RESERVED.contains(s.toLowerCase(Locale.ROOT))) {
            return "'" + s.replace("'", "''") + "'";
        }

        if (s.chars().allMatch(Character::isDigit)) {
            return "'" + s + "'";
        }

        return s;
    }

    private String indent(int n) {
        return " ".repeat(Math.max(0, n));
    }

    private static final class ElementDoc {
        String name;
        Map<String, String> doc = Map.of();
        Map<String, Map<String, String>> attributes = Map.of();
        Map<String, Map<String, String>> children = Map.of();
        Map<String, String> otherAttributes = Map.of();
    }
}
