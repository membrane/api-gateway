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

import org.junit.jupiter.api.*;
import org.mockito.*;

import java.util.*;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BeanRegistryImplementationTest {

    private BeanRegistryImplementation registry;
    private BeanRegistryAware aware;

    @BeforeEach
    void setup() {
        aware = Mockito.mock(BeanRegistryAware.class);
        registry = new BeanRegistryImplementation(null, aware, null);
    }

    @Test
    void register() {
        A a1 = new A("a1");
        A a2 = new A("a2");
        A a3 = new A("a3");
        registry.register("bean1", a1);
        registry.register("bean2", a2);
        registry.register("bean3", a3);
        List<A> as = registry.getBeans(A.class);
        assertEquals(3, as.size());
        assertEquals(Set.of(a1,a2,a3),new HashSet<>(as));
    }

    @Test
    void getBean() {
        A a1 = new A("a1");
        registry.register("bean1", a1);
        assertEquals(a1, registry.getBean(A.class).orElseThrow());
    }

    class A {
        String value;

        public A(String value) {
            this.value = value;
        }
    }
}