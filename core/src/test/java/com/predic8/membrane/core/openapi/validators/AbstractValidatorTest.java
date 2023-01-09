package com.predic8.membrane.core.openapi.validators;

import com.fasterxml.jackson.databind.*;
import com.predic8.membrane.core.openapi.*;
import com.predic8.membrane.core.util.*;
import org.junit.jupiter.api.*;

import java.io.*;

import static com.predic8.membrane.core.openapi.util.TestUtils.getResourceAsStream;

public abstract class AbstractValidatorTest {

    protected final static ObjectMapper objectMapper = new ObjectMapper();

    OpenAPIValidator validator;

    @BeforeEach
    public void setUp() {
        validator = new OpenAPIValidator(new URIFactory(), getResourceAsStream(getOpenAPIFileName()));
    }

    abstract String getOpenAPIFileName();

    public InputStream getResourceAsStream(String fileName) {
        return this.getClass().getResourceAsStream(fileName);
    }
}
