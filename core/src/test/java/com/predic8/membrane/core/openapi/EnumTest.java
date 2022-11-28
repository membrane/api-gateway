package com.predic8.membrane.core.openapi;

import com.predic8.membrane.core.openapi.validators.*;
import org.junit.*;

import java.io.*;
import java.util.*;

import static com.predic8.membrane.core.openapi.util.JsonUtil.*;
import static org.junit.Assert.*;


public class EnumTest {

    OpenAPIValidator validator;

    @Before
    public void setUp() {
        validator = new OpenAPIValidator(getResourceAsStream("/openapi/enum.yml"));
    }

    /*

    Enum without type is not possible with openapi parser. The parser assumes string!
    https://json-schema.org/understanding-json-schema/reference/generic.html

     */
//    @Test
//    public void readOnlyValid() {
//
//        Map m = new HashMap();
//        m.put("state",42);
//
//        ValidationErrors errors = validator.validate(Request.post().path("/enum").body(mapToJson(m)));
//        System.out.println("errors = " + errors);
//        assertEquals(0,errors.size());
//    }


    private InputStream getResourceAsStream(String fileName) {
        return this.getClass().getResourceAsStream(fileName);
    }

}