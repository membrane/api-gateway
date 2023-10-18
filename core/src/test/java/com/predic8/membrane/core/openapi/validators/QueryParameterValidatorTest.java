package com.predic8.membrane.core.openapi.validators;

import io.swagger.v3.oas.models.parameters.*;
import org.junit.jupiter.api.*;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class QueryParameterValidatorTest extends  AbstractValidatorTest{

    QueryParameterValidator queryParameterValidator;

    @Override
    String getOpenAPIFileName() {
        return "/openapi/specs/query-params.yml";
    }

    @BeforeEach
    public void setUp() {
        super.setUp();
        queryParameterValidator = new QueryParameterValidator(validator.getApi(),validator.getApi().getPaths().get("/cities"));
    }

    @Test
    void getPathAndOperationParameters() {

        List<Parameter> parameterSchemas = getParameterSchemas(queryParameterValidator);

        assertEquals(6,parameterSchemas.size());

        // All Parameters must have a name. Referenced params do not have a name.
        assertFalse(parameterSchemas.stream().anyMatch(param -> param.getName() == null));
    }

    private List<Parameter> getParameterSchemas(QueryParameterValidator val) {
        return val.getAllParameterSchemas(validator.getApi().getPaths().get("/cities").getGet());
    }

    @Test
    void resolveReferencedParameter() {
        Parameter referencingParam = validator.getApi().getPaths().get("/cities").getParameters().get(1);
        Parameter resolvedParam = queryParameterValidator.resolveReferencedParameter(referencingParam);
        assertEquals("bar",resolvedParam.getName());
    }
}