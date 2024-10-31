package com.predic8.membrane.core.openapi.serviceproxy;

import com.predic8.membrane.core.openapi.*;
import com.predic8.membrane.core.openapi.model.*;
import com.predic8.membrane.core.openapi.validators.*;
import com.predic8.membrane.core.util.*;
import io.swagger.v3.parser.*;
import jakarta.mail.internet.*;
import org.junit.jupiter.api.*;

import static com.predic8.membrane.core.http.MimeType.*;
import static org.junit.jupiter.api.Assertions.*;

public class OpenAPI31RefTest {

    OpenAPIValidator validator;

    @BeforeEach
    void setUp() {
        OpenAPIRecord apiRecord = new OpenAPIRecord(
                new OpenAPIV3Parser().read("src/test/resources/openapi/specs/openapi-v3_1/external-references/client.yaml"),
                null,
                new OpenAPISpec()
        );

        System.out.println("openAPI = " + apiRecord.getApi());

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
