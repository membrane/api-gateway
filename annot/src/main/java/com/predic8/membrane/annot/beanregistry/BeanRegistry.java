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
package com.predic8.membrane.annot.beanregistry;

import com.predic8.membrane.annot.*;

import java.util.*;
import java.util.function.*;

public interface BeanRegistry {

    Object resolve(String url);

    List<Object> getBeans();

    Grammar getGrammar();

    <T> List<T> getBeans(Class<T> clazz);

    /**
     * Retrieves a single bean of the specified type.
     *
     * @param clazz the class of the bean to retrieve
     * @param <T>   the bean type
     * @return Optional containing the bean if exactly one exists, empty otherwise
     * @throws RuntimeException if multiple beans of the specified type exist
     */
    <T> Optional<T> getBean(Class<T> clazz);

    /**
     * Retrieves a bean with the specified name.
     * @param beanName
     * @param clazz
     * @return Optional containing the bean
     * @param <T> the bean type
     */
    <T> Optional<T> getBean(String beanName, Class<T> clazz);

    /**
     * Registers a bean with the specified name.
     *
     * @param beanName the name to register the bean under
     * @param bean   instance to register (must not be null)
     */
    void register(String beanName, Object bean);

    /**
     * Registers a bean of the specified type with the given name if it is not already registered.
     * If a bean with the given name is already present, the existing instance is returned.
     * Otherwise, the supplier is used to create and register a new instance.
     * @param type the class type of the bean
     * @param supplier a supplier that provides a new instance of the bean if not already registered
     * @param <T> the generic type of the bean
     * @return the existing or newly created and registered bean instance
     */
     <T> T registerIfAbsent(Class<T> type, Supplier<T> supplier);
}
