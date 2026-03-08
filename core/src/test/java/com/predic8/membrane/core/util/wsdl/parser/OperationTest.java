package com.predic8.membrane.core.util.wsdl.parser;

import org.junit.jupiter.api.*;

import static com.predic8.membrane.core.util.wsdl.parser.Operation.Direction.INPUT;
import static com.predic8.membrane.core.util.wsdl.parser.Operation.Direction.OUTPUT;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OperationTest {

    @Test
    void direction() {
        assertTrue(INPUT.matches("input"));
        assertTrue(OUTPUT.matches("OutPut"));
    }

}