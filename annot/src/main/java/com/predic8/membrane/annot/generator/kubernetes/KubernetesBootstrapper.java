package com.predic8.membrane.annot.generator.kubernetes;

import com.predic8.membrane.annot.model.Model;

import javax.annotation.processing.ProcessingEnvironment;

/**
 * Collection to bootstrap all needed config files for kubernetes integration
 */
public class KubernetesBootstrapper {

    private final ProcessingEnvironment processingEnv;

    public KubernetesBootstrapper(final ProcessingEnvironment processingEnv) {
        this.processingEnv = processingEnv;
    }

    public void boot(final Model model) {
        new K8sYamlGenerator(processingEnv).write(model);
        new JsonSchemaGenerator(processingEnv).write(model);
        new K8sHelperGenerator(processingEnv).write(model);
    }
}
