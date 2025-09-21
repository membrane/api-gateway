package com.predic8.membrane.core.openapi.validators.parameters;

import com.predic8.membrane.core.openapi.util.*;
import com.predic8.membrane.core.openapi.validators.*;
import io.swagger.v3.oas.models.parameters.*;
import org.junit.jupiter.api.*;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class ObjectParameterTest extends AbstractValidatorTest {

    AbstractParameter parameter;
    Parameter color;

    @Override
    protected String getOpenAPIFileName() {
        return "/openapi/specs/oas31/parameters/object.yaml";
    }

    @BeforeEach
    public void setUp() throws Exception {
        super.setUp();
        color = OpenAPIUtil.getParameter( OpenAPIUtil.getPath(validator.getApi(),"/color").getGet(),"rgb");
        parameter = AbstractParameter.instance( validator.getApi(),"object", color);
    }

    @Nested
    class ExplodeFalse {


        @Test
        void valid() throws Exception {
            Map<String, List<String>> params = Map.of("rgb",List.of("R,100,G,200,B,150"));
            parameter.setValues(params);
            var fields = parameter.getJson();
            System.out.println("fields = " + fields);
            assertEquals(3, fields.size());
            assertEquals(100, fields.get("R").asInt());
            assertEquals(200, fields.get("G").asInt());
            assertEquals(150, fields.get("B").asInt());
        }

//        @Test
//        void one_too_much() throws Exception {
//            parameter.addAllValues(List.of("R,100,G,200,B,150,NoValue"));
//            var fields = parameter.getJson();
//            System.out.println("fields = " + fields);
//            assertEquals(4, fields.size());
//            assertEquals(100, fields.get("R").asInt());
//            assertEquals(200, fields.get("G").asInt());
//            assertEquals(150, fields.get("B").asInt());
//            assertEquals("null", fields.get("NoValue").asText());
//        }

//        @Test
//        void empty() throws Exception {
//            parameter.addAllValues(List.of(""));
//            assertEquals(0, parameter.getJson().size());
//        }

//        @Test
//        void single_value() throws Exception {
//            parameter.addAllValues(List.of("foo"));
//            JsonNode fields = parameter.getJson();
//            assertEquals(1, fields.size());
//            assertEquals("null", fields.get("foo").asText());
//        }
    }

}