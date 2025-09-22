package com.predic8.membrane.core.openapi.validators;

import com.fasterxml.jackson.databind.node.*;
import org.junit.jupiter.api.*;

class IntegerValidatorTest {

    static final JsonNodeFactory FACTORY = JsonNodeFactory.instance;

    IntegerValidator validator;
    ValidationContext ctx = ValidationContext.create();

    @BeforeEach
    void setUp() {
        validator = new IntegerValidator();
    }

    @Test
    void string() {
        validator.validate(ctx,"1");
    }

    @Test
    void integer() {
        validator.validate(ctx,1);
    }

    @Test
    void number() {
        validator.validate(ctx,FACTORY.numberNode(1));
    }

    @Test
    void numberLong() {
        validator.validate(ctx,FACTORY.numberNode(111111111111111111L));
    }

    @Test
    void invalid() {
        validator.validate(ctx,"fff");
    }
}