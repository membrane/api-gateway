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

import com.predic8.membrane.core.router.Router;
import com.predic8.membrane.test.TestAppender;
import org.apache.commons.cli.ParseException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.Logger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

class RouterCLITest {

    @Test
    void getUserDir() {
        assertTrue(RouterCLI.getUserDir().endsWith("/"));
    }

    /**
     * Dry run (-t) on a YAML configuration must parse it as YAML instead of feeding it to the XML
     * parser (which failed with "Content is not allowed in prolog", see issue #3104).
     */
    @Test
    void verifyYamlConfiguration() {
        assertDoesNotThrow(() ->
                RouterCLI.verifyConfiguration("src/test/resources/configuration/dry-run.apis.yaml"));
    }

    /**
     * <code>oas -l ""</code> passes commons-cli's required check (it only guarantees presence) and
     * used to end in a message-less RuntimeException, see issue #3224.
     */
    @Test
    void getLocationRejectsEmptyLocation() throws ParseException {
        MembraneCommandLine cl = new MembraneCommandLine();
        cl.parse(new String[]{"oas", "-l", ""});

        assertEquals("Invalid value for -l: the OpenAPI location must not be empty.",
                assertThrows(InvalidOptionValueException.class, () -> RouterCLI.getLocation(cl)).getMessage());
    }

    /**
     * Argon2 defines only 0x10 and 0x13, so the range 16..19 wrongly accepted 17 and 18. Bouncy
     * Castle then threw "unknown Argon2 version" - after the password prompt had already run.
     */
    @ParameterizedTest
    @ValueSource(strings = {"17", "18"})
    void argon2VersionRejectsUndefinedVersions(String version) throws ParseException {
        MembraneCommandLine cl = new MembraneCommandLine();
        cl.parse(new String[]{"argon2id", "-v", version});

        assertEquals("Invalid value for -v: %s is not a supported Argon2 version. Use 16 (0x10) or 19 (0x13).".formatted(version),
                assertThrows(InvalidOptionValueException.class, () -> RouterCLI.getArgon2Version(cl)).getMessage());
    }

    @ParameterizedTest
    @ValueSource(ints = {16, 19})
    void argon2VersionAcceptsDefinedVersions(int version) throws ParseException {
        MembraneCommandLine cl = new MembraneCommandLine();
        cl.parse(new String[]{"argon2id", "-v", String.valueOf(version)});

        assertEquals(version, RouterCLI.getArgon2Version(cl));
    }

    @Test
    void argon2VersionDefaultsTo19() throws ParseException {
        MembraneCommandLine cl = new MembraneCommandLine();
        cl.parse(new String[]{"argon2id"});

        assertEquals(19, RouterCLI.getArgon2Version(cl));
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

        Router router = null;
        try {
            router = RouterCLI.initRouterByYAML("src/test/resources/configuration/config.apis.yaml");
            appender.awaitContainsOrThrow("running", Duration.ofSeconds(10));
        } finally {
            if (router != null) {
                router.stop();
            }
            logger.removeAppender(appender);
            appender.stop();
        }
    }
}