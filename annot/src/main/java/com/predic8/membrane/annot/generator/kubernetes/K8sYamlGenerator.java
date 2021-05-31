package com.predic8.membrane.annot.generator.kubernetes;

import com.predic8.membrane.annot.ProcessingException;
import com.predic8.membrane.annot.model.ElementInfo;
import com.predic8.membrane.annot.model.MainInfo;
import com.predic8.membrane.annot.model.Model;

import javax.annotation.processing.ProcessingEnvironment;
import javax.tools.FileObject;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.Writer;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Generates ClusterRoles, ClusterRoleBindings and CustomResourceDefinitions for kubernetes integration.
 */
public class K8sYamlGenerator extends AbstractK8sGenerator {

    private final List<String> crdPlurals;

    public K8sYamlGenerator(ProcessingEnvironment processingEnv) {
        super(processingEnv);
        this.crdPlurals = new ArrayList<>();
    }

    @Override
    public String fileName() {
        return "kubernetes-config.yaml";
    }

    @Override
    protected void write(Model m) {
        m.getMains().forEach(main -> {
            try {
                FileObject fo = createFileObject(main);
                try (BufferedWriter bw = new BufferedWriter(fo.openWriter())) {
                    assemble(bw, main);
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
    }

    private void assemble(Writer w, MainInfo main) throws IOException {
        for (ElementInfo element : getRules(main)) {
            writeCRD(w, element);
            appendLine(w, "---");
        }

        assembleRBAC(w);
    }

    private void assembleRBAC(Writer writer) throws IOException {
        appendLine(writer,
                "apiVersion: rbac.authorization.k8s.io/v1",
                "kind: ClusterRole",
                "metadata:",
                "  name: membrane-soa",
                "rules:",
                "- apiGroups: [\"membrane-soa.org\"]",
                "  resources: [" + crdPlurals.stream()
                        .map(s -> "\"" + s + "\"")
                        .collect(Collectors.joining(",")) + "]",
                "  verbs: [\"list\", \"watch\"]",
                "---",
                "apiVersion: rbac.authorization.k8s.io/v1",
                "kind: ClusterRoleBinding",
                "metadata:",
                "  name: membrane-soa",
                "subjects:",
                "- kind: ServiceAccount",
                "  name: default",
                "  namespace: default",
                "roleRef:",
                "  kind: ClusterRole",
                "  name: membrane-soa",
                "  apiGroup: rbac.authorization.k8s.io"
        );
    }

    private void writeCRD(Writer w, ElementInfo ei) throws IOException {

        if (ei.getAnnotation().mixed() && ei.getCeis().size() > 0)
            throw new ProcessingException(
                    "@MCElement(..., mixed=true) and @MCTextContent is not compatible with @MCChildElement.",
                    ei.getElement()
            );

        WritableNames names = getElementNames(ei);

        appendLine(w,
                "apiVersion: apiextensions.k8s.io/v1",
                "kind: CustomResourceDefinition",
                "metadata:",
                "  name: " + names.pluralName + ".membrane-soa.org",
                "spec:",
                "  conversion:",
                "    strategy: None",
                "  group: membrane-soa.org",
                "  names:",
                "    kind: " + names.className,
                "    listKind: " + names.className + "List",
                "    plural: " + names.pluralName,
                "    shortNames:",
                "    - " + names.name,
                "    singular: " + names.name,
                "  scope: Namespaced",
                "  versions:",
                "  - name: v1beta1",
                "    served: true",
                "    storage: true",
                "    schema:",
                "      openAPIV3Schema:",
                "        x-kubernetes-preserve-unknown-fields: true"
        );

        crdPlurals.add(names.pluralName);
    }
}
