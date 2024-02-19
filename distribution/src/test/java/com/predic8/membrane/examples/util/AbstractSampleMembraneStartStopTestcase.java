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

package com.predic8.membrane.examples.util;

import io.restassured.*;
import io.restassured.filter.log.*;
import org.junit.jupiter.api.*;

import java.io.*;

public class AbstractSampleMembraneStartStopTestcase extends DistributionExtractingTestcase {

    protected Process2 process;

    @BeforeEach
    void startMembrane() throws IOException, InterruptedException {
        process = startServiceProxyScript();

        // Dump HTTP
        // RestAssured.filters(new RequestLoggingFilter(), new ResponseLoggingFilter());
    }

    @AfterEach
    void stopMembrane() throws IOException, InterruptedException {
        process.killScript();
    }
}
