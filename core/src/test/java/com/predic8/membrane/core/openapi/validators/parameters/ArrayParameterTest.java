package com.predic8.membrane.core.openapi.validators.parameters;

import com.predic8.membrane.core.openapi.util.*;
import com.predic8.membrane.core.openapi.validators.*;
import io.swagger.v3.oas.models.parameters.*;
import org.junit.jupiter.api.*;

import java.util.*;

import static com.predic8.membrane.core.openapi.validators.JsonSchemaValidator.ARRAY;
import static com.predic8.membrane.core.openapi.validators.JsonSchemaValidator.NUMBER;
import static org.junit.jupiter.api.Assertions.*;

class ArrayParameterTest extends AbstractValidatorTest {

    AbstractParameter parameter;
    Parameter number;

    @Override
    protected String getOpenAPIFileName() {
        return "/openapi/specs/oas31/parameters/simple.yaml";
    }

    @BeforeEach
    public void setUp() throws Exception {
        super.setUp();
        number = OpenAPIUtil.getParameter(OpenAPIUtil.getPath(validator.getApi(), "/array").getGet(), NUMBER);
        parameter = AbstractParameter.instance(validator.getApi(), ARRAY, number);
    }

    @Test
    void normal() throws Exception {
        Map<String, List<String>> params = Map.of("number",List.of("1","2","3"),"cuckoo",List.of("ignore"));
        parameter.setValues(params);
        var items = parameter.getJson();
        assertEquals(3, items.size());
        assertEquals(1, items.get(0).asInt());
        assertEquals(2, items.get(1).asInt());
        assertEquals(3, items.get(2).asInt());
    }


}