/* Copyright 2025 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */
package com.predic8.membrane.core.proxies;

import com.predic8.membrane.core.*;
import com.predic8.membrane.core.config.*;
import com.predic8.membrane.core.interceptor.*;
import com.predic8.membrane.core.interceptor.server.*;
import org.junit.jupiter.api.*;

import static com.predic8.membrane.test.TestUtil.getPathFromResource;
import static io.restassured.RestAssured.*;
import static io.restassured.filter.log.LogDetail.ALL;
import static org.hamcrest.Matchers.*;

@SuppressWarnings("HttpUrlsUsage")
public class ServiceProxyWSDLInterceptorsTest {

    static Router router;

    @BeforeAll
    static void setUp() {
        router = new HttpRouter();
        router.setHotDeploy(false);
    }

    @AfterAll
    static void teardown() {
        router.stop();
    }


    @Test
    void publisherOnly() throws Exception {
        ServiceProxy sp = new ServiceProxy() {{
            key = new ServiceProxyKey(2000);
        }};
        sp.setPath(new Path(false, "/articles"));

        WSDLInterceptor rewritter = new WSDLInterceptor();
        rewritter.setHost("api.predic8.de");
        rewritter.setPort("1234");
        sp.getInterceptors().add(rewritter);

        WSDLPublisherInterceptor publisher = new WSDLPublisherInterceptor();
        publisher.setWsdl(getPathFromResource("validation/ArticleService.wsdl"));
        sp.getInterceptors().add(publisher);

        sp.setTarget(new AbstractServiceProxy.Target("localhost", 8888));

        router.add(sp);
        router.start();
        downloadAndVerifyDocuments("api.predic8.de:1234");
    }

    private static void downloadAndVerifyDocuments(String host) {
        // @formatter:off
        given()
            .get("http://localhost:2000/articles?wsdl")
        .then()
                .log().all()
            .body("definitions.service.port.address.@location",
                    equalTo("http://%s/material/ArticleService".formatted(host)))
            .body("definitions.types.schema.import[0].@schemaLocation", equalTo("./articles?xsd=1"))
            .body("definitions.types.schema.import[1].@schemaLocation", equalTo("./articles?xsd=2"));

        given()
            .get("http://localhost:2000/articles?xsd=1")
        .then()
            .log().ifValidationFails(ALL)
            .body("schema.import.@schemaLocation",equalTo("./articles?xsd=3"));

        given()
            .get("http://localhost:2000/articles?xsd=2")
        .then()
            .body("definitions.include.@schemaLocation",equalTo("./articles?xsd=4"));

        given()
            .get("http://localhost:2000/articles?xsd=3")
        .then()
            .body("definitions.annotation.documentation",containsString("Common"));

        given()
            .get(("http://localhost:2000/articles?xsd=4"))
        .then()
            .body("definitions.complexType.@name",equalTo("MoneyType"));
        // @formatter:off
    }
}
