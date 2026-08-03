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

package com.predic8.membrane.examples.withoutinternet.cli;

import com.predic8.membrane.examples.util.BufferLogger;
import com.predic8.membrane.examples.util.DistributionExtractingTestcase;
import com.predic8.membrane.examples.util.Process2;
import org.junit.jupiter.api.Test;

import java.io.File;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.apache.commons.io.FileUtils.writeStringToFile;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * Tests the "verify configuration" command line option (<code>-t</code> / <code>--test</code>),
 * which parses a configuration file, reports whether it is valid and terminates without opening
 * any ports.
 * <p>
 * A YAML configuration must be parsed as YAML instead of being fed to the XML parser (which used
 * to fail with "Content is not allowed in prolog", see issue #3104).
 */
public class VerifyConfigurationTest extends DistributionExtractingTestcase {

    @Override
    protected String getExampleDirName() {
        return "..";
    }

    @Test
    void verifiesValidYamlConfiguration() throws Exception {
        writeStringToFile(new File(baseDir, "verify-valid.apis.yaml"), """
                api:
                  port: 2003
                  flow:
                    - adminConsole: {}
                """, UTF_8);

        BufferLogger logger = new BufferLogger();
        assertEquals(0, verify("verify-valid.apis.yaml", logger), logger::toString);
        assertFalse(logger.contains("Content is not allowed in prolog"), logger::toString);
    }

    @Test
    void verifiesValidXmlConfiguration() throws Exception {
        writeStringToFile(new File(baseDir, "verify-valid.proxies.xml"), """
                <spring:beans xmlns="http://membrane-soa.org/proxies/1/"
                              xmlns:spring="http://www.springframework.org/schema/beans"
                              xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                              xsi:schemaLocation="http://www.springframework.org/schema/beans
                              http://www.springframework.org/schema/beans/spring-beans-3.0.xsd
                              http://membrane-soa.org/proxies/1/ http://membrane-soa.org/schemas/proxies-1.xsd">
                    <router>
                        <api port="2004">
                            <return/>
                        </api>
                    </router>
                </spring:beans>
                """, UTF_8);

        BufferLogger logger = new BufferLogger();
        assertEquals(0, verify("verify-valid.proxies.xml", logger), logger::toString);
    }

    @Test
    void reportsInvalidYamlConfiguration() throws Exception {
        writeStringToFile(new File(baseDir, "verify-invalid.apis.yaml"), """
                api:
                  port: 2003
                  flow:
                    - thisPluginDoesNotExist: {}
                """, UTF_8);

        BufferLogger logger = new BufferLogger();
        assertEquals(1, verify("verify-invalid.apis.yaml", logger), logger::toString);
    }

    private int verify(String configFile, BufferLogger logger) throws Exception {
        try (Process2 membrane = new Process2.Builder()
                .in(baseDir)
                .script("membrane")
                .withParameters("-t " + configFile)
                .withWatcher(logger)
                .start()) {
            return membrane.waitForExit(60_000);
        }
    }
}
