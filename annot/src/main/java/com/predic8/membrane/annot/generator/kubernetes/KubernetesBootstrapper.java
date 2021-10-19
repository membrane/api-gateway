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
