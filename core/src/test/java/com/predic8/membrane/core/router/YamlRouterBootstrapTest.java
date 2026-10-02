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

package com.predic8.membrane.core.router;

import com.predic8.membrane.annot.yaml.ConfigurationParsingException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static com.predic8.membrane.core.router.YamlRouterBootstrap.loadIntoRouter;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class YamlRouterBootstrapTest {

    @TempDir
    Path tempDir;

    @Test
    void rejectsMoreThanOneTransportComponent() throws Exception {
        Path config = tempDir.resolve("apis.yaml");
        Files.writeString(config, """
                components:
                  first:
                    transport:
                      backlog: 100
                  second:
                    transport:
                      backlog: 200
                ---
                internal:
                  name: dummy
                  target:
                    url: https://example.com
                """);

        var e = assertThrows(ConfigurationParsingException.class, () -> loadIntoRouter(new DefaultRouter(), config.toString()));
        assertTrue(e.getMessage().contains("#/components/first"));
        assertTrue(e.getMessage().contains("#/components/second"));
    }
}
