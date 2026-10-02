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

package com.predic8.membrane.core.interceptor.antivirus;

import com.predic8.membrane.core.router.DefaultRouter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

import static com.predic8.membrane.core.router.YamlRouterBootstrap.loadIntoRouter;
import static org.junit.jupiter.api.Assertions.*;

class ClamAntiVirusConfigurationTest {
    @TempDir
    Path directory;

    @ParameterizedTest
    @ValueSource(strings = {"block", "pass"})
    void readsScanFailurePolicy(String value) throws Exception {
        var interceptor = parse(value);
        assertEquals(ClamAntiVirusInterceptor.ScanFailureAction.valueOf(value.toUpperCase(Locale.ROOT)),
                interceptor.getOnScanFailure());
    }

    @Test
    void rejectsUnknownScanFailurePolicy() {
        assertThrows(Exception.class, () -> parse("ignore"));
    }

    private ClamAntiVirusInterceptor parse(String value) throws Exception {
        Path file = directory.resolve("apis.yaml");
        Files.writeString(file, """
                api:
                  port: 2000
                  flow:
                    - clamav:
                        onScanFailure: %s
                """.formatted(value));
        var router = new DefaultRouter();
        try {
            loadIntoRouter(router, file.toString());
            return assertInstanceOf(ClamAntiVirusInterceptor.class,
                    router.getRuleManager().getRules().getFirst().getFlow().getFirst());
        } finally {
            router.stop();
        }
    }
}
