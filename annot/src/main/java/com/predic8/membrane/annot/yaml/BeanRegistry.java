/* Copyright 2022 predic8 GmbH, www.predic8.com

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

import com.predic8.membrane.annot.*;

import java.util.*;

public interface BeanRegistry {

    Object resolveReference(String url);

    List<Object> getBeans();

    /**
     * Registers a set of {@link BeanDefinition}s originating from static configuration
     * (for example YAML files on startup).
     *
     * <p>Thread–safety model:</p>
     * <ul>
     *   <li>This method is expected to be called from a single thread.</li>
     *   <li>It publishes initialization events into the internal change queue but does not
     *       perform any activation work itself.</li>
     *   <li>Callers must ensure that no other thread invokes {@link #registerBeanDefinitions(List)}
     *       concurrently.</li>
     * </ul>
     *
     * <p>Lifecycle expectations:</p>
     * <ul>
     *   <li>After publishing all static configuration events, this method signals that
     *       the configuration stream has finished by inserting a {@code StaticConfigurationLoaded}
     *       marker event.</li>
     *   <li>The caller must invoke {@link #start()} exactly once after registration to process all
     *       pending events and activate the beans.</li>
     * </ul>
     *
     * @param beanDefinitions the list of static {@link BeanDefinition}s to register
     */
    void registerBeanDefinitions(List<BeanDefinition> beanDefinitions);

    /**
     * Processes all pending change events and activates the corresponding beans.
     *
     * <p>Thread–safety model:</p>
     * <ul>
     *   <li>This method must be called by exactly one thread.</li>
     *   <li>No other thread may call {@link #start()} concurrently.</li>
     *   <li>All mutating work on the registry (creation, modification, deletion,
     *       activation of beans) is performed exclusively inside this method.</li>
     * </ul>
     *
     * <p>Execution model:</p>
     * <ul>
     *   <li>This method acts as the single consumer of the internal change-event queue.</li>
     *   <li>It blocks until the queue is empty and processes all events in order.</li>
     *   <li>The activation logic uses the insertion order of queued events to guarantee a
     *       deterministic activation sequence.</li>
     *   <li>Callers are responsible for scheduling this method in a dedicated thread when used
     *       with asynchronous producers (such as Kubernetes watchers).</li>
     * </ul>
     *
     * <p>Usage constraints:</p>
     * <ul>
     *   <li>Call this exactly once during startup for static configuration.</li>
     *   <li>In Kubernetes mode, run this method in a dedicated long-running thread to consume
     *       update events as they arrive.</li>
     * </ul>
     */
    void start();

    Grammar getGrammar();

}
