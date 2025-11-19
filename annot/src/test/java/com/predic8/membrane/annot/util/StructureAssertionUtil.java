package com.predic8.membrane.annot.util;

import com.predic8.membrane.annot.yaml.BeanRegistry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class StructureAssertionUtil {
    public static void assertStructure(BeanRegistry o1, Asserter... asserter) {
        assertEquals(o1.getBeans().size(), asserter.length);
        for (int i = 0; i < asserter.length; i++) {
            asserter[i].assertStructure(o1.getBeans().get(i));
        }
    }

    public interface Asserter {
        void assertStructure(Object o1);
    }

    public static Asserter clazz(String clazzName) {
        return new Asserter() {
            @Override
            public void assertStructure(Object o1) {
                assertTrue(o1.getClass().getSimpleName().equals(clazzName));
            }
        };
    }

}
