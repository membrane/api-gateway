package com.predic8.membrane.core.openapi;

import com.predic8.membrane.core.openapi.model.*;
import com.predic8.membrane.core.openapi.validators.*;
import org.junit.*;

import java.io.*;

import static com.predic8.membrane.core.openapi.validators.ValidationContext.ValidatedEntityType.*;
import static org.junit.Assert.*;


public class ReferencesTest {

    OpenAPIValidator validator;

    @Before
    public void setUp() {
        validator = new OpenAPIValidator(getResourceAsStream("/openapi/references.yml"));
    }

    @Test
    public void pathParamOk()  {
        ValidationErrors errors = validator.validate(Request.get().path("/references/6"));
//        System.out.println("errors = " + errors);
        assertEquals(0,errors.size());
    }

    @Test
    public void pathParamInvalid()  {
        ValidationErrors errors = validator.validate(Request.get().path("/references/foo"));
//        System.out.println("errors = " + errors);
        assertEquals(1,errors.size());
        ValidationError e = errors.get(0);
        assertEquals(PATH_PARAMETER,e.getValidationContext().getValidatedEntityType());
        assertEquals("rid",e.getValidationContext().getValidatedEntity());
        assertEquals("integer",e.getValidationContext().getSchemaType());
        assertEquals(400,e.getValidationContext().getStatusCode());
    }

    @Test
    public void queryParamOk() {
        ValidationErrors errors = validator.validate(Request.get().path("/references/6?limit=10"));
//        System.out.println("errors = " + errors);
        assertEquals(0,errors.size());
    }

    @Test
    public void queryParamInvalid() {
        ValidationErrors errors = validator.validate(Request.get().path("/references/6?limit=150"));
//        System.out.println("errors = " + errors);
        assertEquals(1,errors.size());
        ValidationError e = errors.get(0);
        assertEquals(QUERY_PARAMETER,e.getValidationContext().getValidatedEntityType());
        assertEquals("limit",e.getValidationContext().getValidatedEntity());
        assertEquals("integer",e.getValidationContext().getSchemaType());
        assertEquals(400,e.getValidationContext().getStatusCode());
    }

    @Test
    public void bodyAsRefPrimitiveOk() {
        ValidationErrors errors = validator.validate(Request.post().path("/body-as-ref-primitive").body("42"));
//        System.out.println("errors = " + errors);
        assertEquals(0,errors.size());
    }

    @Test
    public void bodyAsRefPrimitiveInvalid() {
        ValidationErrors errors = validator.validate(Request.post().path("/body-as-ref-primitive").body("-1"));
//        System.out.println("errors = " + errors);
        assertEquals(1,errors.size());
        ValidationError e = errors.get(0);
        assertEquals(BODY,e.getValidationContext().getValidatedEntityType());
        assertEquals("REQUEST",e.getValidationContext().getValidatedEntity());
        assertEquals("integer",e.getValidationContext().getSchemaType());
        assertEquals(400,e.getValidationContext().getStatusCode());
    }

    @Test
    public void objRefsObjOK() throws FileNotFoundException {
        InputStream fis = getResourceAsStream("/openapi/references-customer-ok.json");
        ValidationErrors errors = validator.validate(Request.post().path("/obj-ref-obj").body(fis));
//        System.out.println("errors = " + errors);
        assertEquals(0,errors.size());
    }

    @Test
    public void objRefsObjOKInvalid() throws FileNotFoundException {
        InputStream fis = getResourceAsStream("/openapi/references-customer-invalid.json");
        ValidationErrors errors = validator.validate(Request.post().path("/obj-ref-obj").body(fis));
//        System.out.println("errors = " + errors);
        assertEquals(1,errors.size());
        ValidationContext e = errors.get(0).getValidationContext();
        assertEquals("REQUEST",e.getValidatedEntity());
        assertEquals("string",e.getSchemaType());
        assertEquals(400,e.getStatusCode());
        assertEquals("/contract/details", e.getJSONpointer());
    }


    private InputStream getResourceAsStream(String fileName) {
        return this.getClass().getResourceAsStream(fileName);
    }

}