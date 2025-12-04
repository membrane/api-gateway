package com.predic8.membrane.annot.util;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class StructureAssertionUtil {
    public static void assertStructure(Object o1, Asserter asserter) {
        asserter.assertStructure(o1);
    }

    private interface Asserter {
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
