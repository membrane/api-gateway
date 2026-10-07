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

package com.predic8.membrane.tutorials.logging;

import com.predic8.membrane.tutorials.AbstractMembraneTutorialTest;

import java.io.IOException;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Map;

import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;

public abstract class AbstractLoggingTutorialTest extends AbstractMembraneTutorialTest {

    protected abstract String getLoggingConfig();

    @Override
    protected void startMembrane() throws Exception {
        var tutorialDir = baseDir.toPath();
        Files.copy(tutorialDir.resolve(getLoggingConfig()), tutorialDir.resolve("log4j2.yaml"), REPLACE_EXISTING);
        super.startMembrane();
    }

    @Override
    protected Map<String, String> getEnvs() {
        return Map.of("MEMBRANE_DISABLE_TERM_COLORS", "true");
    }

    @Override
    protected String getTutorialDir() {
        return "logging";
    }

    /**
     * Reads the log file whose name starts with the given prefix, for files named
     * with a ${date:...} lookup like access-2026-10-02_09-56-04.log.
     */
    protected String readLogFileStartingWith(String prefix) throws IOException {
        var files = baseDir.listFiles((dir, name) -> name.startsWith(prefix) && name.endsWith(".log"));
        if (files == null || files.length != 1)
            throw new AssertionError("Expected exactly one log file starting with %s but found %s".formatted(prefix, Arrays.toString(files)));
        return readFile(files[0].getName());
    }
}
