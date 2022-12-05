package com.predic8.membrane.core.openapi.validators;

import com.predic8.membrane.core.openapi.*;
import com.predic8.membrane.core.openapi.model.*;
import org.junit.*;

import static com.predic8.membrane.core.openapi.util.TestUtils.getResourceAsStream;
import static com.predic8.membrane.core.openapi.validators.ValidationContext.ValidatedEntityType.*;
import static org.junit.Assert.*;


public class ReferencesTest {

    OpenAPIValidator validator;

    @Before
    public void setUp() {
        validator = new OpenAPIValidator(getResourceAsStream(this,"/openapi/references.yml"));
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
        assertEquals(PATH_PARAMETER,e.getContext().getValidatedEntityType());
        assertEquals("rid",e.getContext().getValidatedEntity());
        assertEquals("integer",e.getContext().getSchemaType());
        assertEquals(400,e.getContext().getStatusCode());
        assertEquals("REQUEST/PATH_PARAMETER/rid", e.getContext().getLocationForRequest());

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
        assertEquals(QUERY_PARAMETER,e.getContext().getValidatedEntityType());
        assertEquals("limit",e.getContext().getValidatedEntity());
        assertEquals("integer",e.getContext().getSchemaType());
        assertEquals(400,e.getContext().getStatusCode());
        assertEquals("REQUEST/QUERY_PARAMETER/limit", e.getContext().getLocationForRequest());
    }

    @Test
    public void bodyAsRefPrimitiveOk() {
        ValidationErrors errors = validator.validate(Request.post().path("/body-as-ref-primitive").json().body("42"));
//        System.out.println("errors = " + errors);
        assertEquals(0,errors.size());
    }

    @Test
    public void bodyAsRefPrimitiveInvalid() {
        ValidationErrors errors = validator.validate(Request.post().path("/body-as-ref-primitive").json().body("-1"));
//        System.out.println("errors = " + errors);
        assertEquals(1,errors.size());
        ValidationError e = errors.get(0);
        assertEquals(BODY,e.getContext().getValidatedEntityType());
        assertEquals("REQUEST",e.getContext().getValidatedEntity());
        assertEquals("integer",e.getContext().getSchemaType());
        assertEquals(400,e.getContext().getStatusCode());
        assertEquals("REQUEST/BODY", e.getContext().getLocationForRequest());
    }

    @Test
    public void objRefsObjOK() {
        ValidationErrors errors = validator.validate(Request.post().path("/obj-ref-obj").json().body(getResourceAsStream(this,"/openapi/references-customer-ok.json")));
//        System.out.println("errors = " + errors);
        assertEquals(0,errors.size());
    }

    @Test
    public void objRefsObjOKInvalid() {
        ValidationErrors errors = validator.validate(Request.post().path("/obj-ref-obj").json().body(getResourceAsStream(this,"/openapi/references-customer-invalid.json")));
//        System.out.println("errors = " + errors);
        assertEquals(1,errors.size());
        ValidationContext ctx = errors.get(0).getContext();
        assertEquals("REQUEST",ctx.getValidatedEntity());
        assertEquals("string",ctx.getSchemaType());
        assertEquals(400,ctx.getStatusCode());
        assertEquals("/contract/details", ctx.getJSONpointer());
        assertEquals("REQUEST/BODY#/contract/details", ctx.getLocationForRequest());
    }
}