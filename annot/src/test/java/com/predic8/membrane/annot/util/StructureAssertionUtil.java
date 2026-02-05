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

import com.predic8.membrane.annot.beanregistry.BeanRegistry;
import org.junit.jupiter.api.Assertions;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class StructureAssertionUtil {
    public static void assertStructure(BeanRegistry registry, Asserter... asserter) {
        assertStructure(registry.getBeans(), asserter);
    }

    public static void assertStructure(List<?> beans, Asserter... asserter) {
        assertEquals(asserter.length, beans.size());

        boolean[] used = new boolean[beans.size()];
        AssertionError failure = matchAnyOrder(beans, asserter, used, 0);

        if (failure != null) throw failure;
    }

    private static AssertionError matchAnyOrder(List<?> beans, Asserter[] expected, boolean[] used, int idx) {
        if (idx == expected.length) return null;

        AssertionError last = null;

        for (int i = 0; i < beans.size(); i++) {
            if (used[i]) continue;

            try {
                expected[idx].assertStructure(beans.get(i));
                used[i] = true;

                AssertionError res = matchAnyOrder(beans, expected, used, idx + 1);
                if (res == null) return null;

                used[i] = false;
                last = res;
            } catch (AssertionError e) {
                last = e;
            } catch (RuntimeException e) {
                last = new AssertionError(e.getMessage(), e);
            }
        }

        return last != null ? last : new AssertionError("No matching bean found for expected index " + idx);
    }

    public interface Asserter {
        void assertStructure(Object bean);
    }

    public interface Property {
        void assertStructure(Object bean);
    }

    public static Asserter clazz(String clazzName, Property... properties) {
        return bean -> {
            assertEquals(clazzName, bean.getClass().getSimpleName());
            for (Property p : properties) {
                p.assertStructure(bean);
            }
        };
    }

    public static Asserter value(Object value) {
        return bean -> Assertions.assertEquals(value, bean);
    }

    public static Asserter isNull() {
        return bean -> Assertions.assertNull(bean);
    }

    public static Asserter convertedToString(String value) {
        return bean -> Assertions.assertEquals(value, bean.toString());
    }

    public static Asserter list(Asserter... asserters) {
        return bean -> {
            assertInstanceOf(List.class, bean);
            List<?> list = (List<?>) bean;
            assertEquals(asserters.length, list.size());
            for (int i = 0; i < asserters.length; i++) {
                asserters[i].assertStructure(list.get(i));
            }
        };
    }

    public static Property property(String name, Asserter asserter) {
        return bean -> {
            try {
                asserter.assertStructure(bean.getClass().getMethod("get" + Character.toUpperCase(name.charAt(0)) + name.substring(1)).invoke(bean));
            } catch (NoSuchMethodException | IllegalAccessException | java.lang.reflect.InvocationTargetException e) {
                throw new RuntimeException(e);
            }
        };
    }

}
