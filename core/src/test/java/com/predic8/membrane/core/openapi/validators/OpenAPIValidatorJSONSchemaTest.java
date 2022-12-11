package com.predic8.membrane.core.openapi.validators;


import com.predic8.membrane.core.openapi.*;
import com.predic8.membrane.core.openapi.model.*;
import org.junit.*;

import static com.predic8.membrane.core.openapi.util.TestUtils.getResourceAsStream;
import static com.predic8.membrane.core.openapi.validators.ValidationContext.ValidatedEntityType.PATH_PARAMETER;
import static org.junit.Assert.assertEquals;

public class OpenAPIValidatorJSONSchemaTest {

    OpenAPIValidator validator;

    @Before
    public void setUp() {
        validator = new OpenAPIValidator(getResourceAsStream(this, "/openapi/specs/customers.yml"));
    }

    @Test
    public void stringInsteadOfIntegerParameter() {
        ValidationErrors errors = validator.validate(Request.get().path("/customers/abc"));
//        System.out.println("errors = " + errors);
        assertEquals(1,errors.size());
        ValidationError e = errors.get(0);
        assertEquals(400, e.getContext().getStatusCode());
        assertEquals(PATH_PARAMETER, e.getContext().getValidatedEntityType());
        assertEquals("cid", e.getContext().getValidatedEntity());
        assertEquals("/customers/{cid}", e.getContext().getUriTemplate());
        assertEquals("REQUEST/PATH_PARAMETER/cid", e.getContext().getLocationForRequest());
    }

    @Test
    public void floatInsteadOfIntegerPathParameter() {
        ValidationErrors errors = validator.validate(Request.get().path("/images/1.0"));
//        System.out.println("errors = " + errors);
        assertEquals(1,errors.size());
        ValidationError e = errors.get(0);
        assertEquals(400, e.getContext().getStatusCode());
        assertEquals(PATH_PARAMETER, e.getContext().getValidatedEntityType());
        assertEquals("iid", e.getContext().getValidatedEntity());
        assertEquals("REQUEST/PATH_PARAMETER/iid", e.getContext().getLocationForRequest());
    }

    @Test
    public void minimumNumberPathParameter() {
        ValidationErrors errors = validator.validate(Request.get().path("/images/-1"));
//        System.out.println("errors = " + errors);
        assertEquals(1,errors.size());
        assertEquals(400, errors.get(0).getContext().getStatusCode());
        assertEquals(PATH_PARAMETER, errors.get(0).getContext().getValidatedEntityType());
        assertEquals("iid", errors.get(0).getContext().getValidatedEntity());
    }

    @Test
    public void stringMaxLengthOk() {
        ValidationErrors errors = validator.validate(Request.get().path("/contracts/abcde"));
        assertEquals(0,errors.size());
    }

    @Test
    public void stringMaxLengthToLong() {
        ValidationErrors errors = validator.validate(Request.get().path("/contracts/abcdef"));
//        System.out.println("errors = " + errors);
        assertEquals(1,errors.size());
        assertEquals(400, errors.get(0).getContext().getStatusCode());
        assertEquals(PATH_PARAMETER, errors.get(0).getContext().getValidatedEntityType());
        assertEquals("cid", errors.get(0).getContext().getValidatedEntity());
    }
}