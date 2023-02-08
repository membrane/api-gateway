/* Copyright 2023 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */

package com.predic8.membrane.examples.tests.template.xml;

import com.predic8.membrane.examples.util.*;
import org.junit.jupiter.api.*;

import java.io.*;

import static com.predic8.membrane.core.http.MimeType.*;
import static io.restassured.RestAssured.*;
import static java.nio.file.Files.*;
import static org.hamcrest.Matchers.*;

public class XMLTemplateTest extends AbstractSampleMembraneStartStopTestcase {

    @Override
    protected String getExampleDirName() {
        return "template/xml";
    }

    @Test
    void xmlInputAndTemplate() throws IOException {
        given()
            .contentType(APPLICATION_XML)
            .body(readString(new File(baseDir + "/cities.xml").toPath()))
            .post("http://localhost:2001")
        .then().assertThat()
            .body("destinations.answer", equalToCompressingWhiteSpace("42"))
            .body("destinations.destination[0]", equalToCompressingWhiteSpace("Hong Kong"))
            .body("destinations.destination[1]", equalToCompressingWhiteSpace("Tokio"))
            .body("destinations.destination[2]", equalToCompressingWhiteSpace("Berlin"));
    }

    @Test
    void xPathAndText() {
        given()
            .contentType(APPLICATION_XML)
            .body("""
                    <person firstname="Juan"/>""")
            .post("http://localhost:2000")
        .then().assertThat()
            .body(containsString("Buenas Noches, Juansito!"));

    }
}