package com.predic8.membrane.core.openapi.serviceproxy;

import com.predic8.membrane.core.*;
import org.junit.jupiter.api.*;

import java.io.*;
import java.util.*;

import static io.swagger.v3.oas.models.SpecVersion.*;
import static org.junit.jupiter.api.Assertions.*;

class OpenAPIRecordFactoryTest {

    static OpenAPIRecordFactory factory;

    @BeforeAll
    static void setUp() {
        Router router = new Router();
        router.setBaseLocation("src/test/resources/openapi/specs/");
        factory = new OpenAPIRecordFactory(router);
    }
    
    @Test
    void readAndParseOpenAPI31() throws IOException {

        Collection<OpenAPISpec> specs = new ArrayList<>();
        specs.add(new OpenAPISpec() {{
            setLocation("customers.yml");
        }});
        
        Map<String, OpenAPIRecord> recs = factory.create(specs);
        OpenAPIRecord rec = recs.get("customers-api-v1-0");
        assertNotNull(rec);
        assertEquals("Customers API", rec.api.getInfo().getTitle());
        assertEquals(V30, rec.api.getSpecVersion());


        //  rec.node Test

    }

    // Test with version 2

    // Test with refs

    @Test
    void s2() throws IOException {}

}