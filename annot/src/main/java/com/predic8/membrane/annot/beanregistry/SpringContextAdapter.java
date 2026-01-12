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

import com.predic8.membrane.annot.Grammar;
import org.springframework.context.ApplicationContext;
import org.springframework.context.support.AbstractRefreshableApplicationContext;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Adapter between Membrane's BeanRegistry and Spring's ApplicationContext.
 *
 * Methods are only implemented on a need-to-use basis.
 */
public class SpringContextAdapter implements BeanRegistry {

    private final AbstractRefreshableApplicationContext ac;

    public SpringContextAdapter(AbstractRefreshableApplicationContext ac) {
        this.ac = ac;
    }

    @Override
    public Object resolve(String url) {
        throw new UnsupportedOperationException();
    }

    @Override
    public List<Object> getBeans() {
        return List.of(ac.getBeanDefinitionNames()).stream().map(ac::getBean).toList();
    }

    @Override
    public Grammar getGrammar() {
        throw new UnsupportedOperationException();
    }

    @Override
    public <T> List<T> getBeans(Class<T> clazz) {
        throw new UnsupportedOperationException();
    }

    @Override
    public <T> Optional<T> getBean(Class<T> clazz) {
        throw new UnsupportedOperationException();
    }

    @Override
    public <T> Optional<T> getBean(String name, Class<T> clazz) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void register(String beanName, Object bean) {
        throw new UnsupportedOperationException();
    }

    @Override
    public <T> T registerIfAbsent(Class<T> type, Supplier<T> supplier) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void close() {
        ac.close();
    }

    public ApplicationContext getApplicationContext() {
        return ac;
    }
}
