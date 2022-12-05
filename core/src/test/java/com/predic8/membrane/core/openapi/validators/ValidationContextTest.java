package com.predic8.membrane.core.openapi.validators;

import org.junit.*;

import static com.predic8.membrane.core.openapi.validators.ValidationContext.ValidatedEntityType.BODY;
import static org.junit.Assert.*;

public class ValidationContextTest {

    @Test
    public void getLocation() {
        assertEquals("REQUEST/BODY#/only-numbers/1",
                new ValidationContext().path("/array")
                        .complexType("Array")
                        .validatedEntityType(BODY)
                        .validatedEntity("REQUEST")
                        .addJSONpointerSegment("only-numbers")
                        .addJSONpointerSegment("1")
                        .getLocationForRequest());
    }
}