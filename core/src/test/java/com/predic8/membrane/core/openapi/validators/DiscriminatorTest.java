package com.predic8.membrane.core.openapi.validators;

import com.predic8.membrane.core.openapi.*;
import com.predic8.membrane.core.openapi.model.*;
import org.junit.*;

import java.io.*;
import java.util.*;

import static com.predic8.membrane.core.openapi.util.JsonUtil.*;
import static org.junit.Assert.*;


@SuppressWarnings("OptionalGetWithoutIsPresent")
public class DiscriminatorTest {

    OpenAPIValidator validator;

    @Before
    public void setUp() {
        validator = new OpenAPIValidator(getResourceAsStream("/openapi/discriminator.yml"));
    }

    @Test
    public void discriminatorLengthWrong() throws RuntimeException{

        Map<String,Object> publicTransport = new HashMap<>();
        publicTransport.put("kind","Train");
        publicTransport.put("name","Bimmelbahn");
        publicTransport.put("seats",254);
        publicTransport.put("length","5cm");

        ValidationErrors errors = validator.validate(Request.post().path("/public-transports").body(mapToJson(publicTransport)));
//        System.out.println("errors = " + errors);
        assertEquals(2,errors.size());

        ValidationError numberError = errors.stream().filter(e -> e.getMessage().contains("number")).findFirst().get();
        assertEquals("/length", numberError.getContext().getJSONpointer());

        ValidationError allOf = errors.stream().filter(e -> e.getMessage().contains("allOf")).findFirst().get();
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
//        System.out.println("errors = " + errors);
        assertEquals(2,errors.size());


        ValidationError requiredError = errors.stream().filter(e -> e.getMessage().toLowerCase().contains("required")).findAny().get();
        assertEquals("/wheels", requiredError.getContext().getJSONpointer());

        ValidationError allOf = errors.stream().filter(e -> e.getMessage().contains("allOf")).findAny().get();
        assertTrue(allOf.getMessage().contains("subschemas"));
    }

    private InputStream getResourceAsStream(String fileName) {
        return this.getClass().getResourceAsStream(fileName);
    }

}