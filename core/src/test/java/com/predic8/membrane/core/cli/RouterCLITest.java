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

package com.predic8.membrane.core.cli;

import com.predic8.membrane.test.TestAppender;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.Logger;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static java.lang.Thread.startVirtualThread;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RouterCLITest {

    @Test
    void getUserDir() {
        assertTrue(RouterCLI.getUserDir().endsWith("/"));
    }

    /**
     * Tests if the basepath is set on the configuration object. If not the resolving of
     * the openapi file in the config will fail.
     */
    @Test
    void basepath() throws Exception {
        var logger = (Logger) LogManager.getRootLogger();

        var appender = new TestAppender("TestAppender");
        appender.start();
        logger.addAppender(appender);


        startVirtualThread(() -> {
            try {
                RouterCLI.main(new String[]{"-c", "src/test/resources/configuration/config.apis.yaml"});
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });

        try {
            appender.awaitContainsOrThrow("running", Duration.ofSeconds(10));
        } finally {
            logger.removeAppender(appender);
            appender.stop();
        }
    }
}