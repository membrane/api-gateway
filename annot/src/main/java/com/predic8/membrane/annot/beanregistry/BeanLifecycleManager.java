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

import java.lang.reflect.Method;

/**
 * A BeanLifecycleManager supports the init-destroy lifecycle of beans.
 *
 * Beans are inited (@PostConstruct method called) when they are being defined, that is before their instance is
 * published via the registry.
 *
 * Beans are destroyed (@PreDestroy method called) when close() is called on the registry.
 *
 * The registry implements this interface.
 */
public interface BeanLifecycleManager {
    /**
     * Tells the registry that the <code>method</code> should be called on the <code>bean</code> when the registry is
     * closed.
     *
     * The registry should call all pre-destroy-callbacks in <b>reverse</b> order in which they were registered.
     */
    void addPreDestroyCallback(Object bean, Method method);
}
