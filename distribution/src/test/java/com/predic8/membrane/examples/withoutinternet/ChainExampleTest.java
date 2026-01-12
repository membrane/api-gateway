/* Copyright 2026 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */

package com.predic8.membrane.examples.withoutinternet;

import com.predic8.membrane.core.http.*;
import com.predic8.membrane.examples.util.*;
import org.jetbrains.annotations.*;
import org.junit.jupiter.api.*;

import java.util.concurrent.atomic.*;

import static io.restassured.RestAssured.*;
import static org.junit.jupiter.api.Assertions.*;

public class ChainExampleTest extends AbstractSampleMembraneStartStopTestcase {

    @Override
    protected String getExampleDirName() {
        return "/extending-membrane/reusable-plugin-chains";
    }


    @Test
    void request1() {
        AtomicBoolean pathFound = addPathWatcher();

        // @formatter:off
        given()
        .when()
            .get("http://localhost:2000/foo")
        .then()
            .assertThat()
            .statusCode(200);
        // @formatter:on

        assertTrue(pathFound.get());
    }

    @Test
    void request2() {
        AtomicBoolean pathFound = addPathWatcher();

        // @formatter:off
        given()
        .when()
            .get("http://localhost:2000/bar")
        .then()
            .assertThat()
                .header(Header.ACCESS_CONTROL_ALLOW_ORIGIN, "*")
            .statusCode(200);
        // @formatter:on

        assertTrue(pathFound.get());
    }

    private @NotNull AtomicBoolean addPathWatcher() {
        AtomicBoolean pathFound = new AtomicBoolean();
        process.addConsoleWatcher((error, line) -> {
            if (line.contains("Path:")) pathFound.set(true);
        });
        return pathFound;
    }
}
