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

package com.predic8.membrane.examples.tests.oauth2;

import com.predic8.membrane.examples.util.*;
import io.restassured.*;
import io.restassured.filter.log.*;
import org.junit.jupiter.api.*;

import java.io.*;

import static org.junit.jupiter.api.Assertions.*;

public class OAuth2APIExampleTest extends DistributionExtractingTestcase {

    @Override
    protected String getExampleDirName() {
        return "security/oauth2/api";
    }

    Process2 authorizationServer;
    Process2 tokenValidator;

    @BeforeEach
    void startMembrane() throws IOException, InterruptedException {
        authorizationServer = new Process2.Builder().in(getExampleDir( getExampleDirName() + "/authorization_server")).script("membrane").waitForMembrane().start();
        tokenValidator = new Process2.Builder().in(getExampleDir( getExampleDirName() + "/token_validator")).script("membrane").waitForMembrane().start();

        // Dump HTTP
        RestAssured.filters(new RequestLoggingFilter(), new ResponseLoggingFilter());
    }

    @AfterEach
    void stopMembrane() {
        tokenValidator.killScript();
        authorizationServer.killScript();
    }

    // Can fail due to powershell execution policy. Can be ignored
    @Test
    void testIt() throws Exception {
        BufferLogger logger = new BufferLogger();
        // client.sh prints true when the script was successful. The logger waits for this string "true"
        try(Process2 ignored = new Process2.Builder()
                .in(getExampleDir(getExampleDirName()))
                .withWatcher(logger)
                .script("client")
                .parameters("john password")
                .waitAfterStartFor("true")
                .start()) {
            assertTrue(logger.contains("success"));
            assertTrue(logger.contains("true"));
        }
    }
}
