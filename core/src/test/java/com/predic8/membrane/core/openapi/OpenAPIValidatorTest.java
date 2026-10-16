/*
 *  Copyright 2022 predic8 GmbH, www.predic8.com
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */

package com.predic8.membrane.core.openapi;

import com.predic8.membrane.core.openapi.model.Request;
import com.predic8.membrane.core.openapi.serviceproxy.OpenAPIRecord;
import com.predic8.membrane.core.openapi.serviceproxy.OpenAPISpec;
import com.predic8.membrane.core.util.ConfigurationException;
import com.predic8.membrane.core.util.URIFactory;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.Paths;
import io.swagger.v3.oas.models.info.Info;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static com.predic8.membrane.core.openapi.model.Request.get;
import static com.predic8.membrane.core.openapi.util.OpenAPITestUtils.getResourceAsStream;
import static com.predic8.membrane.core.openapi.util.OpenAPITestUtils.parseOpenAPI;
import static com.predic8.membrane.core.openapi.validators.ValidationContext.ValidatedEntityType.METHOD;
import static com.predic8.membrane.core.openapi.validators.ValidationContext.ValidatedEntityType.PATH;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpenAPIValidatorTest {

    OpenAPIValidator validator;

    @BeforeEach
    public void setUp() throws IOException {
        validator = new OpenAPIValidator(new URIFactory(), new OpenAPIRecord(parseOpenAPI(getResourceAsStream(this, "/openapi/specs/customers.yml")), new OpenAPISpec()));
    }

    @Test
    void validateSimple() {
        assertEquals(0, validator.validate(get().path("/customers")).size());
    }

    @Test
    void rightMethod() {
        assertEquals(0, validator.validate(get().path("/customers/7")).size());
    }

    @Test
    void wrongPath() {
        var errors = validator.validate(get().path("/foo"));
        assertEquals(1, errors.size());
        assertEquals(404, errors.get(0).getContext().getStatusCode());
        assertEquals(PATH, errors.get(0).getContext().getValidatedEntityType());
    }

    @Test
    void wrongMethod() {
        var errors = validator.validate(Request.patch().path("/customers/7"));
        assertEquals(1, errors.size());
        assertEquals(405, errors.get(0).getContext().getStatusCode());
        assertEquals(METHOD, errors.get(0).getContext().getValidatedEntityType());
    }

    @Test
    void prepareValidationCanBeUsedForRequestValidation() {
        var validationPlan = validator.prepareValidation(get().path("/customers"));
        assertEquals(0, validationPlan.validateRequest(get().path("/customers")).size());
    }

    @Test
    void queryStringArrayExploded() {
        assertEquals(0, validator.validate(get().path("/customers?ids=a&ids=b&ids=c")).size());
    }

    @Test
    void queryStringArrayExplodedWithNumbers() {
        assertEquals(0, validator.validate(get().path("/customers?ids=1&ids=2&ids=3")).size());
    }

    @Test
    void validatorIsReusableForSeveralRequests() {
        assertEquals(0, validator.validate(get().path("/customers/7")).size());
        assertEquals(404, validator.validate(get().path("/foo")).getFirst().getContext().getStatusCode());
        assertEquals(0, validator.validate(get().path("/customers")).size());
    }

    @Test
    void pathThatIsNoValidTemplateIsAConfigurationError() {
        var api = new OpenAPI().info(new Info().title("Broken API")).paths(new Paths().addPathItem("/a/{b}}", new PathItem()));
        var spec = new OpenAPISpec();
        spec.setLocation("broken.yaml");
        var rec = new OpenAPIRecord(api, spec);
        var e = assertThrows(ConfigurationException.class, () -> new OpenAPIValidator(new URIFactory(), rec));
        assertTrue(e.getMessage().contains("/a/{b}}"));
        assertTrue(e.getMessage().contains("Broken API"));
        assertTrue(e.getMessage().contains("broken.yaml"));
    }

    @Test
    void parameterOccurringTwiceIsAConfigurationError() {
        var api = new OpenAPI().info(new Info().title("Broken API")).paths(new Paths().addPathItem("/a/{id}/b/{id}", new PathItem()));
        var e = assertThrows(ConfigurationException.class, () -> new OpenAPIValidator(new URIFactory(), new OpenAPIRecord(api, new OpenAPISpec())));
        assertTrue(e.getMessage().contains("/a/{id}/b/{id}"));
    }
}
