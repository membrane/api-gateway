package com.predic8.membrane.annot.util;

import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.fail;

public class ArchitectureTest {

    @Test
    void yamlParser() {
        try {
            Class.forName("com.predic8.membrane.annot.util.YamlParser");
        } catch (ClassNotFoundException e) {
            fail("Expected class com.predic8.membrane.annot.util.YamlParser to exist.");
        }
    }

}
