package com.predic8.membrane.core.openapi.validators;

import com.predic8.membrane.core.openapi.*;
import com.predic8.membrane.core.openapi.model.*;
import com.predic8.membrane.core.openapi.validators.*;
import org.junit.*;

import java.io.*;
import java.util.*;

import static com.predic8.membrane.core.openapi.util.JsonUtil.*;
import static org.junit.Assert.*;


public class DiscriminatorTest {

    OpenAPIValidator validator;

    @Before
    public void setUp() {
        validator = new OpenAPIValidator(getResourceAsStream("/openapi/discriminator.yml"));
    }

    @Test
    public void discriminatorLengthWrong() {

        Map<String,Object> publicTransport = new HashMap<>();
        publicTransport.put("kind","Train");
        publicTransport.put("name","Bimmelbahn");
        publicTransport.put("seats",254);
        publicTransport.put("length","5cm");

        ValidationErrors errors = validator.validate(Request.post().path("/public-transports").body(mapToJson(publicTransport)));
        System.out.println("errors = " + errors);
        assertEquals(2,errors.size());

        ValidationError numberError = errors.stream().filter(e -> e.getMessage().contains("number")).findFirst().orElseThrow(() -> {
            throw new RuntimeException("No number error!");
        });

        assertEquals("/length", numberError.getValidationContext().getJSONpointer());

        ValidationError allOf = errors.stream().filter(e -> e.getMessage().contains("allOf")).findFirst().orElseThrow(() -> {
            throw new RuntimeException("Does not contain allOf Message!");
        });
        assertTrue(allOf.getMessage().contains("subschemas"));
    }

    @Test
    @SuppressWarnings("OptionalGetWithoutIsPresent")
    public void discriminatorNoWheels() {

        Map<String,Object> publicTransport = new HashMap<>();
        publicTransport.put("kind","Bus");
        publicTransport.put("name","Postbus");
        publicTransport.put("seats",45);

        ValidationErrors errors = validator.validate(Request.post().path("/public-transports").body(mapToJson(publicTransport)));
        System.out.println("errors = " + errors);
        assertEquals(2,errors.size());


        ValidationError requiredError = errors.stream().filter(e -> e.getMessage().toLowerCase().contains("required")).findAny().get();

        assertEquals("/wheels", requiredError.getValidationContext().getJSONpointer());

        ValidationError allOf = errors.stream().filter(e -> e.getMessage().contains("allOf")).findAny().get();
        assertTrue(allOf.getMessage().contains("subschemas"));
    }

//    @Test
//    public void allOfTooLongInvalid() {
//
//        Map m = new HashMap();
//        m.put("firstname","123456");
//
//        ValidationErrors errors = validator.validate(Request.post().path("/composition").body(mapToJson(m)));
////        System.out.println("errors = " + errors);
//        assertEquals(2,errors.size());
//
//        ValidationError enumError = errors.stream().filter(e -> e.getMessage().contains("axLength")).findAny().get();
//        assertEquals("/firstname", enumError.getValidationContext().getJSONpointer());
//
//        ValidationError allOf = errors.stream().filter(e -> e.getMessage().contains("allOf")).findAny().get();
//        assertEquals("/firstname", allOf.getValidationContext().getJSONpointer());
//        assertTrue(allOf.getMessage().contains("subschemas"));
//
//    }


    private InputStream getResourceAsStream(String fileName) {
        return this.getClass().getResourceAsStream(fileName);
    }

}