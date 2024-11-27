package com.predic8.membrane.core.openapi.serviceproxy;

import com.predic8.membrane.core.*;
import io.swagger.v3.oas.models.parameters.Parameter;
import org.junit.jupiter.api.*;

import java.io.*;
import java.util.*;

import static com.predic8.membrane.core.http.MimeType.APPLICATION_JSON;
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

    @Test
    void v2Test() throws IOException {
        OpenAPIRecord rec = factory.create(new ArrayList<>() {{
            add(new OpenAPISpec() {{
                setLocation("fruitshop-swagger-2.0.json");
            }});
        }}).get("fruit-shop-api-swagger-2-v1-0-0");
        assertNotNull(rec);
        assertEquals("Fruit Shop API Swagger 2", rec.api.getInfo().getTitle());
        assertEquals(V30, rec.api.getSpecVersion());
    }

    @Test
    void referencesTest() throws IOException {
        OpenAPIRecord rec = factory.create(new ArrayList<>() {{
            add(new OpenAPISpec() {{
                setLocation("oas31/request-reference.yaml");
            }});
        }}).get("demo-v1-0-0");
        assertNotNull(rec);
        assertEquals("Demo", rec.api.getInfo().getTitle());
        assertEquals(V31, rec.api.getSpecVersion());
        assertNotNull(
            rec.api.getPaths().get("/users").getPost()
                   .getRequestBody().getContent().get(APPLICATION_JSON)
                   .getSchema().getProperties().get("email")
        );
    }

    @Test
    void s2() throws IOException {}
}