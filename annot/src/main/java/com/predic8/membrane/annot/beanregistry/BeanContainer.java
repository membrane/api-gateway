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

import com.predic8.membrane.annot.Grammar;
import com.predic8.membrane.annot.bean.BeanFactory;
import com.predic8.membrane.annot.yaml.GenericYamlParser;
import org.jetbrains.annotations.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.*;

public class BeanContainer {
    private static final Logger log = LoggerFactory.getLogger(BeanContainer.class);

    private final BeanDefinition definition;
    /**
     * The class of the bean.
     */
    private final Class clazz;
    /**
     * Constructed bean after initialization.
     */
    private final AtomicReference<Object> singleton = new AtomicReference<>();

    /**
     * Creates a BeanDefinition where the bean has not yet been instantiated and initialized.
     */
    public BeanContainer(BeanDefinition definition, Grammar grammar) {
        this.definition = definition;
        this.clazz = GenericYamlParser.decideClazz(definition.getKind(), grammar, definition.getNode());
    }

    /**
     * Creates a BeanDefinition where the bean has already been instantiated and initialized.
     */
    public BeanContainer(BeanDefinition definition, @NotNull Object singleton) {
        this.definition = definition;
        this.singleton.set(singleton);
        this.clazz = singleton.getClass();
    }

    /**
     * Only to be used within this class.
     * Use {@link #getOrCreate(BeanRegistryImplementation, Grammar)} instead.
     */
    private Object getSingleton() {
        return singleton.get();
    }

    /**
     * Only to be used within this class.
     * Use {@link #getOrCreate(BeanRegistryImplementation, Grammar)} instead.
     */
    private void setSingleton(Object singleton) {
        this.singleton.set(singleton);
    }

    public BeanDefinition getDefinition() {
        return definition;
    }

    @Override
    public String toString() {
        return "BeanContainer: %s of %s singleton: %s".formatted( definition.getName(),definition.getKind(),singleton.get());
    }

    private synchronized @NotNull Object define(BeanRegistryImplementation registry, Grammar grammar) {
        log.debug("defining bean: {}", definition.getNode());
        try {
            if ("bean".equals(definition.getKind())) {
                return new BeanFactory(registry).create(definition.getNode().path("bean"));
            }
            return GenericYamlParser.readMembraneObject(definition.getKind(),
                    grammar,
                    definition.getNode(),
                    registry);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public @NotNull Object getOrCreate(BeanRegistryImplementation registry, Grammar grammar) {
        boolean prototype = isPrototypeScope(getDefinition());

        // Prototypes are created anew every time.
        if (prototype) {
            return define(registry, grammar);
        }


        // Singleton: ensure define() runs at most once per BeanContainer.
        synchronized (this) {
            Object existing = getSingleton();
            if (existing != null) {
                return existing;
            }

            Object created = define(registry, grammar);
            setSingleton(created);
            return created;
        }
    }

    private static boolean isPrototypeScope(BeanDefinition bd) {
        if (!bd.isBean())
            return bd.isPrototype();

        return "PROTOTYPE".equalsIgnoreCase(
                bd.getNode().path("bean").path("scope").asText("SINGLETON")
        );
    }

    /**
     * Checks whether this bean definition will produce a bean which can be assigned to the clazz.
     */
    public <T> boolean produces(Class<T> clazz) {
        return clazz.isAssignableFrom(this.clazz);
    }
}
