package com.predic8.membrane.core.openapi.validators.parameters;

import com.predic8.membrane.core.openapi.validators.*;
import org.junit.jupiter.api.*;

import static com.predic8.membrane.core.openapi.model.Request.*;
import static org.junit.jupiter.api.Assertions.*;

public class OverwriteParameterTest extends AbstractValidatorTest {

    @Override
    protected String getOpenAPIFileName() {
        return "/openapi/specs/oas31/parameters/overwrite.yaml";
    }

    @Test
    void operationParameterOverwritesPathParameter() {
        ValidationErrors err = validator.validate(get().path("/overwrite?a=foo"));
        assertEquals(1, err.size());
        assertTrue(err.get(0).getMessage().contains("of [number]"));
    }

    @Test
    void headerParameterDoesNotInterfere() {
        ValidationErrors err = validator.validate(get().path("/overwrite?b=foo"));
        assertEquals(1, err.size());
        assertTrue(err.get(0).getMessage().contains("of [boolean]"));
    }
}