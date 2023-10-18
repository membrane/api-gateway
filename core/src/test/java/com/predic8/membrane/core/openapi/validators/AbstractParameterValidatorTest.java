package com.predic8.membrane.core.openapi.validators;

import com.predic8.membrane.core.interceptor.rest.QueryParameter;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.parameters.HeaderParameter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AbstractParameterValidatorTest {

    ParameterValidatorMock validatorMock;

    static class ParameterValidatorMock extends AbstractParameterValidator{
        public ParameterValidatorMock(OpenAPI api, PathItem pathItem) {
            super(api, pathItem);
        }
    }

    @BeforeEach
    void setup() {
        validatorMock = new ParameterValidatorMock(null, null);
    }

    @Test
    void isNotTypeOfTest() {
        assertFalse(validatorMock.isTypeOf(new HeaderParameter(), QueryParameter.class));
    }

    @Test
    void isTypeOfTest() {
        assertTrue(validatorMock.isTypeOf(new HeaderParameter(), HeaderParameter.class));
    }
}