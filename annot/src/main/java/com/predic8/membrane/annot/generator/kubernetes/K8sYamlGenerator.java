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
        try {
            for (MainInfo main : m.getMains()) {
                FileObject fo = createFileObject(main, fileName());
                try (BufferedWriter w = new BufferedWriter(fo.openWriter())) {
                    assemble(w, main);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void assemble(Writer w, MainInfo main) throws IOException {

        for (ElementInfo element : getRules(main)) {
            writeCRD(w, element);
            appendLine(w, "---");
        }

        assembleRBAC(w);
        appendLine(w, "---");
        assembleVAW(w);
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

    private void assembleVAW(Writer w) throws IOException {
        String name = "membrane-validator";
        String hookName = name + ".default.svc.cluster.local";

        appendLine(w,
                "apiVersion: admissionregistration.k8s.io/v1",
                "kind: ValidatingWebhookConfiguration",
                "metadata:",
                "  name: " + hookName,
                "webhooks:",
                "  - name: " + hookName,
                "    rules:",
                "      - apiGroups: [\"membrane-soa.org\"]",
                "        apiVersions: [\"v1beta1\"]",
                "        operations: [\"CREATE\", \"UPDATE\"]",
                "        resources: [" + allResources() + "]",//
                "        scope: \"*\"",
                "    clientConfig:",
                "      service:",
                "        namespace: membrane-soa",
                "        name: " + name,
                "      caBundle: ${CA_BUNDLE}",
                "    admissionReviewVersions: [\"v1\", \"v1beta1\"]",
                "    sideEffects: None",
                "    timeoutSeconds: 5"
        );
    }

    private String allResources() {
        return crdPlurals.stream()
                .map(crd -> "\"" + crd + "\"")
                .collect(Collectors.joining(","));
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
                "    kind: " + names.name,
                "    listKind: " + names.name + "List",
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
