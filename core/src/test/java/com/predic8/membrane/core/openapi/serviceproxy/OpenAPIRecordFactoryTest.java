/* Copyright 2024 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */
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
    void readAndParseOpenAPI31() {
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
    void swagger2ConversionNoticeAdded() throws IOException {
        OpenAPIRecord rec = getOpenAPIRecord("fruitshop-swagger-2.0.json", "fruit-shop-api-swagger-2-v1-0-0");
        String description = rec.api.getInfo().getDescription();
        assertTrue(description.contains("Membrane API Gateway"));
    }

    @Test
    void swagger2ConversionNoticeAddedWithExistingDescription() throws IOException {
        OpenAPIRecord rec = getOpenAPIRecord("fruitshop-swagger-2.0.json", "fruit-shop-api-swagger-2-v1-0-0");
        String description = rec.api.getInfo().getDescription();
        assertTrue(description.startsWith("This is a showcase"));
        assertTrue(description.contains("Membrane API Gateway"));
    }

    @Test
    void openapi3NoConversionNoticeAdded() throws IOException {
        OpenAPIRecord rec = getOpenAPIRecord("fruitshop-api-v2-openapi-3.yml", "fruit-shop-api-v2-0-0");
        assertFalse("OpenAPI description was converted to OAS 3 from Swagger 2 by Membrane API Gateway.".contains(
                rec.api.getInfo().getDescription()
        ));
    }

    @Test
    void referencesTest() {
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

    private static OpenAPIRecord getOpenAPIRecord(String fileName, String id) {
        return factory.create(new ArrayList<>() {{
            add(new OpenAPISpec() {{
                setLocation(fileName);
            }});
        }}).get(id);
    }

    @Test
    void getUniqueIdNoCollision() {
        assertEquals("customers-api-v1-0",  factory.getUniqueId(new HashMap<>(), new OpenAPIRecord(getApi("/openapi/specs/customers.yml"),null)));
    }

    @Test
    void getUniqueIdCollision() {
        HashMap<String, OpenAPIRecord> recs = new HashMap<>();
        recs.put("customers-api-v1-0",new OpenAPIRecord());
        assertEquals("customers-api-v1-0-0",  factory.getUniqueId(recs, new OpenAPIRecord(getApi("/openapi/specs/customers.yml"),null)));
    }

    private OpenAPI getApi(String pfad) {
        return new OpenAPIParser().readContents(readInputStream(getResourceAsStream(this,pfad)), null, null).getOpenAPI();
    }
}