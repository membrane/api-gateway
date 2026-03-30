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

package com.predic8.membrane.annot.yaml.parsing.support;

import com.predic8.membrane.annot.beanregistry.BeanRegistryAware;
import com.predic8.membrane.annot.yaml.ParsingContext;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import static org.springframework.util.ReflectionUtils.doWithMethods;

public final class BeanLifecycleInvoker {

    public <T> T apply(ParsingContext<?> ctx, T bean) {
        if (ctx != null && ctx.getRegistry() != null && bean instanceof BeanRegistryAware beanRegistryAware) {
            beanRegistryAware.setRegistry(ctx.getRegistry());
        }
        doWithMethods(bean.getClass(), method -> {
            if (method.isAnnotationPresent(PostConstruct.class))
                invokeNoArg(bean, method);
            if (ctx != null && ctx.getRegistry() != null && method.isAnnotationPresent(PreDestroy.class)) {
                method.setAccessible(true);
                ctx.getRegistry().addPreDestroyCallback(bean, method);
            }
        });
        return bean;
    }

    private static void invokeNoArg(Object bean, Method method) {
        try {
            method.setAccessible(true);
            method.invoke(bean);
        } catch (InvocationTargetException e) {
            throw new RuntimeException(e.getTargetException());
        } catch (Throwable t) {
            throw new RuntimeException(t);
        }
    }
}
