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
import static org.junit.jupiter.api.Assertions.assertEquals;

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
        ValidationErrors errors = validator.validate(Request.get().path("/clients/1"));
        assertEquals(0, errors.size());
    }

    @Test
    void validatePostClient() throws ParseException {
        String requestBody = """
        {
            "firstName": "John",
            "lastName": "Doe",
            "contactDetails": {
                "email": "john.doe@example.com",
                "phoneNumber": "+1234567890"
            }
        }
        """;
        ValidationErrors errors = validator.validate(
                Request.post().path("/clients").body(requestBody).mediaType(APPLICATION_JSON)
        );
        assertEquals(0, errors.size());
    }

    @Test
    void validatePostClientWithPerson() throws ParseException {
        String requestBody = """
        {
            "firstName": "Jane",
            "lastName": "Smith",
            "contactDetails": {
                "email": "jane.smith@example.com",
                "phoneNumber": "+0987654321"
            },
            "membershipLevel": "Gold"
        }
        """;
        assertEquals(0, validator.validate(
                Request.post().path("/clients/with-person/").body(requestBody).mediaType(APPLICATION_JSON)
        ).size());
    }
}
