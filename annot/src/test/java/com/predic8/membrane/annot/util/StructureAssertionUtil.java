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

package com.predic8.membrane.annot.util;

import com.predic8.membrane.annot.yaml.BeanRegistry;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class StructureAssertionUtil {
    public static void assertStructure(BeanRegistry registry, Asserter... asserter) {
        assertEquals(registry.getBeans().size(), asserter.length);
        for (int i = 0; i < asserter.length; i++) {
            asserter[i].assertStructure(registry.getBeans().get(i));
        }
    }

    public interface Asserter {
        void assertStructure(Object bean);
    }

    public interface Property {
        void assertStructure(Object bean);
    }

    public static Asserter clazz(String clazzName, Property... properties) {
        return new Asserter() {
            @Override
            public void assertStructure(Object bean) {
                assertTrue(bean.getClass().getSimpleName().equals(clazzName));
                for (Property p : properties) {
                    p.assertStructure(bean);
                }
            }
        };
    }

    public static Asserter value(Object value) {
        return new Asserter() {
            @Override
            public void assertStructure(Object bean) {
                assertEquals(value, bean);
            }
        };
    }

    public static Property property(String name, Asserter asserter) {
        return new Property() {
            @Override
            public void assertStructure(Object bean) {
                try {
                    Method getter = bean.getClass().getMethod("get" + Character.toUpperCase(name.charAt(0)) + name.substring(1));
                    Object propertyValue = getter.invoke(bean);
                    asserter.assertStructure(propertyValue);
                } catch (NoSuchMethodException | IllegalAccessException | java.lang.reflect.InvocationTargetException e) {
                    throw new RuntimeException(e);
                }
            }
        };
    }

}
