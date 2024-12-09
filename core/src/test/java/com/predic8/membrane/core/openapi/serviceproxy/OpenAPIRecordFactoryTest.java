package com.predic8.membrane.core.openapi.serviceproxy;

import com.predic8.membrane.core.*;
import io.swagger.parser.*;
import io.swagger.v3.oas.models.*;
import org.junit.jupiter.api.*;

import java.io.*;
import java.util.*;

import static com.predic8.membrane.core.http.MimeType.*;
import static com.predic8.membrane.core.openapi.util.TestUtils.*;
import static com.predic8.membrane.core.util.FileUtil.*;
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
    }

    @Test
    void readAndParseSwagger2() throws IOException {
        OpenAPIRecord rec = getOpenAPIRecord("fruitshop-swagger-2.0.json", "fruit-shop-api-swagger-2-v1-0-0");
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
        assertNotNull(getMail(rec));
    }

    @Test
    void referencesRelativeFilesInSameDirectory() throws IOException {

        OpenAPIRecord rec = getOpenAPIRecord("oas31/references/request-reference.yaml", "demo-v1-0-0");

        assertEquals("Demo", rec.api.getInfo().getTitle());
        assertEquals(V31, rec.api.getSpecVersion());
        assertNotNull(getMail(rec));
    }

    @Test
    void referencesRelativeFilesInSameDirectory2() throws IOException {

        OpenAPIRecord rec = getOpenAPIRecord("oas31/references/request-reference.yaml", "demo-v1-0-0");

        assertEquals("Demo", rec.api.getInfo().getTitle());
        assertEquals(V31, rec.api.getSpecVersion());
        assertNotNull(getMail(rec));
    }

    @Test
    void deep() throws IOException {

        OpenAPIRecord rec = getOpenAPIRecord("oas31/references/deep/deep.oas.yaml", "deep-refs-v1-0-0");

        assertEquals("Deep Refs", rec.api.getInfo().getTitle());
        assertEquals(V31, rec.api.getSpecVersion());
        assertNotNull(getMail(rec));
    }

    private static Object getMail(OpenAPIRecord rec) {
        return rec.api.getPaths().get("/users").getPost()
                .getRequestBody().getContent().get(APPLICATION_JSON)
                .getSchema().getProperties().get("email");
    }

    // @TODO
    private static OpenAPIRecord getOpenAPIRecord(String fileName, String id) throws IOException {
        return factory.create(new ArrayList<>() {{
            add(new OpenAPISpec() {{
                setLocation(fileName);
            }});
        }}).get(id);
    }

    @Test
    void getUniqueIdNoCollision() {
        assertEquals("customers-api-v1-0",  factory.getUniqueId(new HashMap<String, OpenAPIRecord>(), new OpenAPIRecord(getApi("/openapi/specs/customers.yml"),null,null)));
    }

    @Test
    void getUniqueIdCollision() {
        HashMap<String, OpenAPIRecord> recs = new HashMap<>();
        recs.put("customers-api-v1-0",new OpenAPIRecord());
        assertEquals("customers-api-v1-0-0",  factory.getUniqueId(recs, new OpenAPIRecord(getApi("/openapi/specs/customers.yml"),null,null)));
    }

    private OpenAPI getApi(String pfad) {
        return new OpenAPIParser().readContents(readInputStream(getResourceAsStream(this,pfad)), null, null).getOpenAPI();
    }
}