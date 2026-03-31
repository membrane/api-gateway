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

package com.predic8.membrane.annot.beanregistry;

import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Wraps parsed bean definitions and provides simple validation helpers for multi-file configs.
 */
public record BeanDefinitions(List<BeanDefinition> definitions) {

    public BeanDefinitions {
        definitions = List.copyOf(definitions);
    }

    public @NotNull Optional<BeanDefinition> getConfigDefinition() {
        List<BeanDefinition> configurationDefinitions = definitions.stream()
                .filter(bd -> "configuration".equals(bd.getKind()))
                .toList();

        if (configurationDefinitions.size() > 1) {
            throw new IllegalStateException("Found multiple 'configuration' definitions (%d). Only one is allowed. Found at: %s"
                    .formatted(configurationDefinitions.size(), configurationDefinitions.stream()
                            .map(BeanDefinition::formatConfigLocation)
                            .collect(Collectors.joining(", "))));
        }

        return configurationDefinitions.stream().findFirst();
    }

    public void ensureSingleGlobalDefinition() {
        List<BeanDefinition> globalDefinitions = definitions.stream()
                .filter(bd -> "global".equals(bd.getKind()))
                .toList();

        if (globalDefinitions.size() > 1) {
            throw new IllegalStateException("Found multiple 'global' definitions (%d). Only one is allowed. Found at: %s"
                    .formatted(globalDefinitions.size(), globalDefinitions.stream()
                            .map(BeanDefinition::formatConfigLocation)
                            .collect(Collectors.joining(", "))));
        }
    }
}
