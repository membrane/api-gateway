package com.predic8.membrane.core.openapi.serviceproxy;

import com.predic8.membrane.core.openapi.OpenAPIValidator;
import com.predic8.membrane.core.openapi.model.Request;
import com.predic8.membrane.core.openapi.validators.ValidationErrors;
import com.predic8.membrane.core.util.URIFactory;
import io.swagger.v3.parser.OpenAPIV3Parser;
import jakarta.mail.internet.ParseException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.predic8.membrane.core.http.MimeType.APPLICATION_JSON;
import static com.predic8.membrane.test.AssertUtils.assertContains;
import static org.hamcrest.Matchers.contains;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class OpenAPI31RefTest {

    OpenAPIValidator validator;

    @BeforeEach
    void setUp() {
        OpenAPIRecord apiRecord = new OpenAPIRecord(
                new OpenAPIV3Parser().read("src/test/resources/openapi/specs/openapi-v3_1/external-references/client.yaml"),
                null,
                new OpenAPISpec()
        );
        validator = new OpenAPIValidator(new URIFactory(), apiRecord);
    }

    @Test
    void validateGetClientById() {
        assertEquals(0, validator.validate(Request.get().path("/clients/1")).size());
    }

    @Test
    void validatePostClient() throws ParseException {
        String requestBody = """
        {
            "firstName": "John",
            "lastName": "Doe",
            "address": {
                "street": "123 Elm Street",
                "city": "Springfield",
                "postalCode": "12345",
                "country": "USA"
            }
        }
        """;
        assertEquals(
                0,
                validator.validate(Request.post().path("/clients").body(requestBody).mediaType(APPLICATION_JSON)).size()
        );
    }

    @Test
    void validatePostClientWrongBody() throws ParseException {
        String requestBody = """
        {
            "firstName": 1.0,
            "address": "foo"
        }
        """;
        ValidationErrors errors = validator.validate(Request.post().path("/clients").body(requestBody).mediaType(APPLICATION_JSON));
        assertEquals(2, errors.size());
        System.out.println("errors = " + errors);
    }

    @Test
    void validatePostClientWithPerson() throws ParseException {
        String requestBody = """
        {
            "firstName": "Jane",
            "lastName": "Smith",
            "address": {
                "street": "456 Oak Avenue",
                "city": "Metropolis",
                "postalCode": "67890",
                "country": "USA"
            },
            "additionalInformation": "foo"
        }
        """;
        assertEquals(
                0,
                validator.validate(Request.post().path("/clients-with-person").body(requestBody).mediaType(APPLICATION_JSON)).size()
        );
    }
}
