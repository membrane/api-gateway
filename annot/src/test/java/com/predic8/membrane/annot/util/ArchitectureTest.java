package com.predic8.membrane.annot.util;

import org.junit.jupiter.api.*;

import static com.predic8.membrane.annot.util.CompilerHelper.YAML_PARSER_CLASS_NAME;
import static org.junit.jupiter.api.Assertions.fail;

public class ArchitectureTest {

    @Test
    void yamlParser() {
        try {
            Class.forName(YAML_PARSER_CLASS_NAME);
        } catch (ClassNotFoundException e) {
            fail("Expected class %s to exist.".formatted(YAML_PARSER_CLASS_NAME));
        }
    }

}
