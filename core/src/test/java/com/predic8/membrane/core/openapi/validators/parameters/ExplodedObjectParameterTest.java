package com.predic8.membrane.core.openapi.validators.parameters;

import com.predic8.membrane.core.openapi.util.*;
import com.predic8.membrane.core.openapi.validators.*;
import io.swagger.v3.oas.models.parameters.*;
import org.junit.jupiter.api.*;

import java.util.*;

import static com.predic8.membrane.core.openapi.model.Request.get;
import static com.predic8.membrane.core.openapi.validators.JsonSchemaValidator.OBJECT;
import static org.junit.jupiter.api.Assertions.*;

class ExplodedObjectParameterTest extends AbstractValidatorTest {

    AbstractParameter parameter;
    Parameter exploded;

    @Override
    protected String getOpenAPIFileName() {
        return "/openapi/specs/oas31/parameters/object.yaml";
    }

    @BeforeEach
    public void setUp() throws Exception {
        super.setUp();
        exploded = OpenAPIUtil.getParameter( OpenAPIUtil.getPath(validator.getApi(),"/exploded").getGet(),"rgb");
        parameter = AbstractParameter.instance(validator.getApi(),OBJECT, exploded);
    }

    @Nested
    class Exploded {

        @Test
        void colors() {
            ValidationErrors err = validator.validate(get().path("/exploded?R=100&G=200&B=150"));
            assertEquals(0, err.size());
        }

        @Test
        void valid() throws Exception {
            Map<String, List<String>> params = Map.of("R",List.of("100"),"G",List.of("200"),"B",List.of("150"));
            parameter.setValues(params);
            var fields = parameter.getJson();
            assertEquals(3, fields.size());
            assertEquals(100, fields.get("R").asInt());
            assertEquals(200, fields.get("G").asInt());
            assertEquals(150, fields.get("B").asInt());
        }
    }

}