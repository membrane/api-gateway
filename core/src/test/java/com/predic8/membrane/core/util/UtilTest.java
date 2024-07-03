package com.predic8.membrane.core.util;

import org.junit.jupiter.api.Test;

import static com.predic8.membrane.core.util.Util.setIfNull;
import static org.junit.jupiter.api.Assertions.*;

class UtilTest {

    static class TestObject {
        private String value;

        public String getValue() {
            return value;
        }

        public void setValue(String value) {
            this.value = value;
        }
    }

    @Test
    void testSetIfNull() {
        TestObject testObject = new TestObject();

        // Case where value is initially null
        setIfNull(testObject, TestObject::getValue, TestObject::setValue, "default");
        assertEquals("default", testObject.getValue());

        // Case where value is not null
        testObject.setValue("not null");
        setIfNull(testObject, TestObject::getValue, TestObject::setValue, "default");
        assertEquals("not null", testObject.getValue());

        // Case where object itself is null
        assertThrows(NullPointerException.class, () -> setIfNull(null, TestObject::getValue, TestObject::setValue, "default"));
    }
}