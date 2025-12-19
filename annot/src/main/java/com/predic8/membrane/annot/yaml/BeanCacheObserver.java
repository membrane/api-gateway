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

package com.predic8.membrane.annot.yaml;

import java.io.IOException;

/**
 * Observer for {@link BeanRegistryImplementation} events.
 * <p>
 * Implementations are notified when the cache has finished its asynchronous
 * initial load and whenever a bean is added, modified, or deleted.
 */
public interface BeanCacheObserver {
    /**
     * Called when the cache finished its asynchronous initial load.
     *
     * @param empty {@code true} if no activatable beans are present afterwards,
     *              {@code false} otherwise
     */
    void handleAsynchronousInitializationResult(boolean empty);

    /**
     * Called for an add/modify/delete event of a bean.
     *
     * @param bd      the bean definition
     * @param bean    the current instance (on ADD/MODIFY) or {@code null} (on DELETE)
     * @param oldBean the previous instance (on MODIFY) or {@code null}
     * @throws IOException if handling the event performs I/O and it fails
     *
     *
     * TODO: Make event visible: enum and add to signature?
     *
     */
    void handleBeanEvent(BeanDefinition bd, Object bean, Object oldBean) throws IOException;

    /**
     * Whether beans of the given definition should be considered activatable/usable
     * by the runtime.
     *
     * @param bd the bean definition
     * @return {@code true} if activatable, {@code false} otherwise
     */
    boolean isActivatable(BeanDefinition bd);
}
