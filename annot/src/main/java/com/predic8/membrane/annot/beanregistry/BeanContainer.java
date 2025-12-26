/* Copyright 2025 predic8 GmbH, www.predic8.com

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

import java.util.concurrent.atomic.*;

public class BeanContainer {
    private final BeanDefinition definition;
    /**
     * Constructed bean after initialization.
     */
    private final AtomicReference<Object> singleton = new AtomicReference<>();

    public BeanContainer(BeanDefinition definition) {
        this.definition = definition;
    }

    public Object getSingleton() {
        return singleton.get();
    }

    public void setSingleton(Object singleton) {
        this.singleton.set(singleton);
    }

    /**
     * Sets the singleton if not already set.
     *
     * @return true if this call published the singleton
     */
    public boolean setIfAbsent(Object instance) {
        return singleton.compareAndSet(null, instance);
    }

    public BeanDefinition getDefinition() {
        return definition;
    }

    @Override
    public String toString() {
        return "BeanContainer: %s of %s".formatted( definition.getName(),definition.getKind());
    }
}
