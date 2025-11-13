package com.predic8.membrane.core.openapi.validators;

import org.junit.jupiter.api.*;

import static com.predic8.membrane.core.openapi.validators.JsonSchemaValidator.BOOLEAN;
import static org.junit.jupiter.api.Assertions.*;

class BooleanValidatorTest {

    BooleanValidator validator = new BooleanValidator();

    @Test
    void can_validate() {
        assertEquals(BOOLEAN,validator.canValidate("true"));
        assertEquals(BOOLEAN,validator.canValidate("false"));
        assertEquals(BOOLEAN,validator.canValidate("NO"));
        assertEquals(BOOLEAN,validator.canValidate("yes"));
        assertNull(validator.canValidate(null));
    }

    @Test
    void do_validation() {
        assertNull(validator.validate(null,"Yes"));
        assertNull(validator.validate(null,"no"));
        assertNull(validator.validate(null,"true"));
        assertNull(validator.validate(null,"false"));
    }

}