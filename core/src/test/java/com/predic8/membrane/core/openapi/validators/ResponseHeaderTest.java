/* Copyright 2023 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */
package com.predic8.membrane.core.openapi.validators;

import com.predic8.membrane.core.exchange.*;
import com.predic8.membrane.core.http.*;
import org.junit.jupiter.api.*;

import java.net.*;

import static com.predic8.membrane.core.openapi.util.Utils.*;
import static com.predic8.membrane.core.openapi.validators.ValidationContext.ValidatedEntityType.*;
import static org.junit.jupiter.api.Assertions.*;

class ResponseHeaderTest extends AbstractValidatorTest {


    @Override
    protected String getOpenAPIFileName() {
        return "/openapi/specs/header-params.yml";
    }

    @Test
    void headerIsOfRightType() throws Exception {
        Exchange exc = getExchange("/header-in-response");

        exc.setResponse(Response.ok().header("X-REQUIRED-INTEGER","8").header("X-REQUIRED-STRING","dummy").build());

        ValidationErrors errors = validator.validateResponse(getOpenapiValidatorRequest(exc), getOpenapiValidatorResponse(exc));

        System.out.println("errors = " + errors);

        assertTrue(errors.isEmpty());
    }

    @Test
    void headerIsOfRightTypeCasing() throws Exception {
        Exchange exc = getExchange("/header-in-response");

        // Different Casing
        exc.setResponse(Response.ok().header("X-Required-integeR","8").header("X-REQUIREd-sTRING","dummy").build());

        ValidationErrors errors = validator.validateResponse(getOpenapiValidatorRequest(exc), getOpenapiValidatorResponse(exc));

        System.out.println("errors = " + errors);

        assertTrue(errors.isEmpty());
    }


    @Test
    void requiredHeaderIsMissing() throws Exception {
        Exchange exc = getExchange("/header-in-response");
        exc.setResponse(Response.ok().header("X-REQUIRED-INTEGER", "7").build());

        ValidationErrors errors = validator.validateResponse(getOpenapiValidatorRequest(exc), getOpenapiValidatorResponse(exc));

        assertEquals(1,errors.size());
        ValidationError e = errors.get(0);

        assertEquals(HEADER_PARAMETER,e.getContext().getValidatedEntityType());
        assertEquals("X-REQUIRED-STRING", e.getContext().getValidatedEntity());
    }

    @Test
    void headerIsOfWrongType() throws Exception {
        Exchange exc = getExchange("/header-in-response");

        exc.setResponse(Response.ok().header("X-REQUIRED-INTEGER","abc").header("X-REQUIRED-STRING","dummy").build());

        ValidationErrors errors = validator.validateResponse(getOpenapiValidatorRequest(exc), getOpenapiValidatorResponse(exc));

        System.out.println("errors = " + errors);
        
        assertEquals(1,errors.size());
        ValidationError e = errors.get(0);

        assertEquals(HEADER_PARAMETER,e.getContext().getValidatedEntityType());
        assertEquals("X-REQUIRED-INTEGER", e.getContext().getValidatedEntity());
        assertTrue(e.getMessage().contains("not an integer"));
    }

    @Test
    void responseRef() throws Exception {
        Exchange exc = getExchange("/response-ref");
        exc.setResponse(Response.ok().header("X-REQUIRED","$").build());

        ValidationErrors errors = validator.validateResponse(getOpenapiValidatorRequest(exc), getOpenapiValidatorResponse(exc));

        assertEquals(1,errors.size());
        ValidationError e = errors.get(0);

        assertEquals(HEADER_PARAMETER,e.getContext().getValidatedEntityType());
        assertEquals("X-REQUIRED", e.getContext().getValidatedEntity());
        assertTrue(e.getMessage().contains("'$' does not match"));
    }

    @Test
    void headerNoSchemaValid() throws Exception {
        Exchange exc = getExchange("/header-no-schema");
        exc.setResponse(Response.ok().header("X-NO-SCHEMA","11").build());

        ValidationErrors errors = validator.validateResponse(getOpenapiValidatorRequest(exc), getOpenapiValidatorResponse(exc));

        assertTrue(errors.isEmpty());
    }

    @Test
    void headerNoSchemaInvalid() throws Exception {
        Exchange exc = getExchange("/header-no-schema");
        exc.setResponse(Response.ok().build());

        ValidationErrors errors = validator.validateResponse(getOpenapiValidatorRequest(exc), getOpenapiValidatorResponse(exc));

        assertEquals(1,errors.size());
        ValidationError e = errors.get(0);

        assertEquals(HEADER_PARAMETER,e.getContext().getValidatedEntityType());
        assertEquals("X-NO-SCHEMA", e.getContext().getValidatedEntity());
        assertTrue(e.getMessage().contains("Missing"));
    }

    @Test
    void headerRef() throws Exception {
        Exchange exc = getExchange("/header-ref");
        exc.setResponse(Response.ok().header("X-HEADER-REF","NoBoolean").build());

        ValidationErrors errors = validator.validateResponse(getOpenapiValidatorRequest(exc), getOpenapiValidatorResponse(exc));

        System.out.println("errors = " + errors);
        assertEquals(1,errors.size());
        ValidationError e = errors.get(0);

        assertEquals(HEADER_PARAMETER,e.getContext().getValidatedEntityType());
        assertEquals("X-HEADER-REF", e.getContext().getValidatedEntity());
        assertTrue(e.getMessage().contains("not a boolean"));
    }

    @Test
    void headerRequiredNotPresentMissingHeader() throws Exception {
        Exchange exc = getExchange("/header-required-not-present");
        exc.setResponse(Response.ok().build());
        assertEquals(0, validator.validateResponse(getOpenapiValidatorRequest(exc), getOpenapiValidatorResponse(exc)).size());
    }

    @Test
    void headerRequiredNotPresentWithHeaderOk() throws Exception {
        Exchange exc = getExchange("/header-required-not-present");
        exc.setResponse(Response.ok().header("X-BAZ","abc").build());
        assertEquals(0, validator.validateResponse(getOpenapiValidatorRequest(exc), getOpenapiValidatorResponse(exc)).size());
    }

    @Test
    void headerRequiredNotPresentWithHeaderTooLong() throws Exception {
        Exchange exc = getExchange("/header-required-not-present");
        exc.setResponse(Response.ok().header("X-BAZ","abcdef").build());
        ValidationErrors errors = validator.validateResponse(getOpenapiValidatorRequest(exc), getOpenapiValidatorResponse(exc));
        assertEquals(1,errors.size());
        ValidationError e = errors.get(0);
        assertEquals(HEADER_PARAMETER,e.getContext().getValidatedEntityType());
        assertEquals("X-BAZ", e.getContext().getValidatedEntity());
        assertTrue(e.getMessage().contains("axLength"));
    }

    @Test
    void headerRequiredFalseMissingHeader() throws Exception {
        Exchange exc = getExchange("/header-required-false");
        exc.setResponse(Response.ok().build());
        assertEquals(0, validator.validateResponse(getOpenapiValidatorRequest(exc), getOpenapiValidatorResponse(exc)).size());
    }

    @Test
    void headerRequiredFalseHeaderOk() throws Exception {
        Exchange exc = getExchange("/header-required-false");
        exc.setResponse(Response.ok().header("X-BAZ","abc").build());
        assertEquals(0, validator.validateResponse(getOpenapiValidatorRequest(exc), getOpenapiValidatorResponse(exc)).size());
    }

    @Test
    void headerRequiredFalseHeaderTooLong() throws Exception {
        Exchange exc = getExchange("/header-required-false");
        exc.setResponse(Response.ok().header("X-BAZ","abcdef").build());
        ValidationErrors errors = validator.validateResponse(getOpenapiValidatorRequest(exc), getOpenapiValidatorResponse(exc));
        assertEquals(1,errors.size());
        ValidationError e = errors.get(0);
        assertEquals(HEADER_PARAMETER,e.getContext().getValidatedEntityType());
        assertEquals("X-BAZ", e.getContext().getValidatedEntity());
        assertTrue(e.getMessage().contains("axLength"));
    }

    private static Exchange getExchange(String path) throws URISyntaxException {
        Exchange exc = new Exchange(null);
        exc.setRequest(Request.get(path).build());
        exc.setOriginalRequestUri(path);
        return exc;
    }
}