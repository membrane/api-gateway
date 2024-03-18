/* Copyright 2009, 2011 predic8 GmbH, www.predic8.com

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

import com.predic8.membrane.core.openapi.model.*;
import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;

class PathParametersTest extends AbstractValidatorTest {

    @Override
protected String getOpenAPIFileName() {
        return "/openapi/specs/path-parameters.yml";
    }

    @Test
    public void oneUuid() {
        ValidationErrors errors = validator.validate(Request.get().path("/v1/uuid-parameter/134"));
//        System.out.println("errors = " + errors);
        assertEquals(1,errors.size());
        ValidationContext vc = errors.get(0).getContext();
        assertEquals("/v1/uuid-parameter/134", vc.getPath());
        assertTrue(errors.get(0).getMessage().contains("UUID"));
    }

    @Test
    public void twoPathParams() {
        ValidationErrors errors = validator.validate(Request.get().path("/v1/two-path-params/1/true"));
//        System.out.println("errors = " + errors);
        assertEquals(0,errors.size());
    }

    @Test
    public void twoPathParamsErrors() {
        ValidationErrors errors = validator.validate(Request.get().path("/v1/two-path-params/a/b"));
//        System.out.println("errors = " + errors);
        assertEquals(2,errors.size());

        ValidationContext vc1 = errors.get(0).getContext();
        assertEquals("/v1/two-path-params/a/b", vc1.getPath());
        assertTrue(errors.get(0).getMessage().contains("not an integer"));

        ValidationContext vc2 = errors.get(1).getContext();
        assertEquals("/v1/two-path-params/a/b", vc2.getPath());
        assertTrue(errors.get(1).getMessage().contains("not a boolean"));
    }

    @Test
    public void twoUUIDPathParams() {
        ValidationErrors errors = validator.validate(Request.get().path("/v1/two-path-uuid-params/7555dd94-1799-4678-b0e0-50ac42748710/409268c1-cc60-4255-ab94-8b5e973fd0a2"));
//        System.out.println("errors = " + errors);
        assertEquals(0,errors.size());
    }

    @Test
    public void twoUUIDPathParamsErrors() {
        ValidationErrors errors = validator.validate(Request.get().path("/v1/two-path-uuid-params/a/b"));
//        System.out.println("errors = " + errors);
        assertEquals(2,errors.size());

        ValidationContext vc1 = errors.get(0).getContext();
        assertEquals("/v1/two-path-uuid-params/a/b", vc1.getPath());
        assertTrue(errors.get(0).getMessage().contains("not a valid UUID"));

        ValidationContext vc2 = errors.get(1).getContext();
        assertEquals("/v1/two-path-uuid-params/a/b", vc2.getPath());
        assertTrue(errors.get(1).getMessage().contains("not a valid UUID"));
    }



}