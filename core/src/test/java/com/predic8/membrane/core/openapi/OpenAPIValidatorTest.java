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
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.Paths;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.IntegerSchema;
import io.swagger.v3.oas.models.parameters.PathParameter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;

import static com.predic8.membrane.core.openapi.model.Request.get;
import static com.predic8.membrane.core.openapi.util.OpenAPITestUtils.getResourceAsStream;
import static com.predic8.membrane.core.openapi.util.OpenAPITestUtils.parseOpenAPI;
import static com.predic8.membrane.core.openapi.validators.ValidationContext.ValidatedEntityType.METHOD;
import static com.predic8.membrane.core.openapi.validators.ValidationContext.ValidatedEntityType.PATH;
import static org.junit.jupiter.api.Assertions.*;

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

    /**
     * OpenAPI: concrete paths are matched before their templated counterparts, independent of their order.
     */
    @Test
    void concretePathPrecedesTemplatedPathWithOtherMethod() {
        var api = new OpenAPI().paths(new Paths()
                .addPathItem("/pets/{id}", new PathItem().post(new Operation()))
                .addPathItem("/pets/mine", new PathItem().get(new Operation())));
        var validator = new OpenAPIValidator(new URIFactory(), new OpenAPIRecord(api, new OpenAPISpec()));
        assertEquals(0, validator.validate(get().path("/pets/mine")).size());
        assertEquals(405, validator.validate(get().path("/pets/7")).getFirst().getContext().getStatusCode());
    }

    @Test
    void concretePathPrecedesTemplatedPathWithSameMethod() {
        var api = new OpenAPI().paths(new Paths()
                .addPathItem("/pets/{id}", new PathItem().get(new Operation()
                        .addParametersItem(new PathParameter().name("id").schema(new IntegerSchema()))))
                .addPathItem("/pets/mine", new PathItem().get(new Operation())));
        var validator = new OpenAPIValidator(new URIFactory(), new OpenAPIRecord(api, new OpenAPISpec()));
        assertEquals(0, validator.validate(get().path("/pets/mine")).size());
        assertEquals(0, validator.validate(get().path("/pets/7")).size());
    }

    /**
     * A matching concrete path is the matching path, even if it lacks the method: no fallback to a templated path.
     */
    @Test
    void concretePathWithoutMethodIsNotAllowed() {
        var api = new OpenAPI().paths(new Paths()
                .addPathItem("/users/me", new PathItem().get(new Operation()))
                .addPathItem("/users/{id}", new PathItem().delete(new Operation())));
        var validator = new OpenAPIValidator(new URIFactory(), new OpenAPIRecord(api, new OpenAPISpec()));
        var ctx = validator.validate(Request.delete().path("/users/me")).getFirst().getContext();
        assertEquals(405, ctx.getStatusCode());
        assertEquals(List.of("GET"), ctx.getAllowedMethods());
        assertEquals(0, validator.validate(Request.delete().path("/users/7")).size());
    }

    /**
     * Ambiguous templated paths are left to the tooling by OpenAPI: the first matching path that has the method is used.
     */
    @Test
    void ambiguousTemplatedPathWithoutMethodIsSkipped() {
        var api = new OpenAPI().paths(new Paths()
                .addPathItem("/books/{id}", new PathItem().post(new Operation()))
                .addPathItem("/{entity}/me", new PathItem().get(new Operation())));
        var validator = new OpenAPIValidator(new URIFactory(), new OpenAPIRecord(api, new OpenAPISpec()));
        assertEquals(0, validator.validate(get().path("/books/me")).size());
        assertEquals(0, validator.validate(Request.post().path("/books/me")).size());
        var putMe = validator.validate(Request.put().path("/books/me")).getFirst().getContext();
        assertEquals(405, putMe.getStatusCode());
        assertEquals(List.of("POST", "GET"), putMe.getAllowedMethods());
        var get7 = validator.validate(get().path("/books/7")).getFirst().getContext();
        assertEquals(405, get7.getStatusCode());
        assertEquals(List.of("POST"), get7.getAllowedMethods());
        assertEquals(404, validator.validate(get().path("/books/7/x")).getFirst().getContext().getStatusCode());
    }

    /**
     * The path a request is validated against, also if it lacks the method.
     */
    @Test
    void getMatchingPath() {
        var api = new OpenAPI().paths(new Paths()
                .addPathItem("/books/{id}", new PathItem().post(new Operation()))
                .addPathItem("/{entity}/me", new PathItem().get(new Operation()))
                .addPathItem("/books/mine", new PathItem().get(new Operation())));
        var validator = new OpenAPIValidator(new URIFactory(), new OpenAPIRecord(api, new OpenAPISpec()));
        assertEquals("/{entity}/me", validator.getMatchingPath("GET", "/books/me"));
        assertEquals("/books/{id}", validator.getMatchingPath("POST", "/books/me?x=1"));
        assertEquals("/books/{id}", validator.getMatchingPath("PUT", "/books/me")); // No path has PUT: the first matching one
        assertEquals("/books/mine", validator.getMatchingPath("POST", "/books/mine")); // Concrete, even without POST
        assertNull(validator.getMatchingPath("GET", "/books/7/x"));
    }

    /**
     * OpenAPI: templated paths with the same hierarchy but different templated names MUST NOT exist as they are identical.
     */
    @Test
    void equivalentTemplatedPathsAreAConfigurationError() {
        var api = new OpenAPI().info(new Info().title("Broken API")).paths(new Paths()
                .addPathItem("/pets/{id}", new PathItem().get(new Operation()))
                .addPathItem("/pets/{name}", new PathItem().post(new Operation())));
        var e = assertThrows(ConfigurationException.class, () -> new OpenAPIValidator(new URIFactory(), new OpenAPIRecord(api, new OpenAPISpec())));
        assertTrue(e.getMessage().contains("/pets/{id}"));
        assertTrue(e.getMessage().contains("/pets/{name}"));
    }

    @Test
    void equivalentTemplatedPathsWithSeveralParametersAreAConfigurationError() {
        var api = new OpenAPI().paths(new Paths()
                .addPathItem("/{a}/files/{b}.json", new PathItem().get(new Operation()))
                .addPathItem("/{x}/files/{y}.json", new PathItem().get(new Operation())));
        assertThrows(ConfigurationException.class, () -> new OpenAPIValidator(new URIFactory(), new OpenAPIRecord(api, new OpenAPISpec())));
    }

    @Test
    void templatedPathsWithDifferentHierarchyAreNoConfigurationError() {
        var api = new OpenAPI().paths(new Paths()
                .addPathItem("/pets/{id}", new PathItem().get(new Operation()))
                .addPathItem("/pets/{id}/toys", new PathItem().get(new Operation()))
                .addPathItem("/pets/{name}.json", new PathItem().get(new Operation()))
                .addPathItem("/{entity}/{id}", new PathItem().get(new Operation())));
        new OpenAPIValidator(new URIFactory(), new OpenAPIRecord(api, new OpenAPISpec()));
    }
}
