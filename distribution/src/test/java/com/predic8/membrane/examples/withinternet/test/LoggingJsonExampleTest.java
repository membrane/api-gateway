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

package com.predic8.membrane.examples.withinternet.test;

import com.predic8.membrane.examples.util.DistributionExtractingTestcase;
import com.predic8.membrane.examples.util.Process2;
import com.predic8.membrane.examples.util.SubstringWaitableConsoleEvent;
import com.predic8.membrane.test.HttpAssertions;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

import static java.lang.System.nanoTime;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class LoggingJsonExampleTest extends DistributionExtractingTestcase {

    @Override
    protected String getExampleDirName() {
        return "logging/json";
    }

    @Test
    public void test() throws Exception {
        Path logFile = baseDir.toPath().resolve("membrane_json.log");
        Files.deleteIfExists(logFile);

        try (Process2 sl = startServiceProxyScriptWithEnv("JAVA_OPTS", "-Dlog4j.configurationFile=examples/logging/json/log4j2_json.xml -Dlog4j.debug=true");
             HttpAssertions ha = new HttpAssertions()) {

            SubstringWaitableConsoleEvent logged = new SubstringWaitableConsoleEvent(sl, "HTTP/1.1");

            ha.getAndAssert200("http://localhost:2000/");
            assertTrue(logged.occurred());

            waitForNonEmptyFile(logFile, Duration.ofSeconds(10));
            assertTrue(!Files.readString(logFile).isBlank(),"Expected membrane_json.log to contain log output, but it was blank: " + logFile.toAbsolutePath());
        }
    }

    private static void waitForNonEmptyFile(Path file, Duration timeout) throws Exception {
        long deadline = nanoTime() + timeout.toNanos();
        while (nanoTime() < deadline) {
            if (Files.exists(file) && Files.size(file) > 0) return;
            Thread.sleep(100);
        }
        throw new AssertionError("Timed out waiting for non-empty log file: " + file.toAbsolutePath());
    }

}
