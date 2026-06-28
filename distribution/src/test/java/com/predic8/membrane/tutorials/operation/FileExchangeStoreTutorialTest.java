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

package com.predic8.membrane.tutorials.operation;

import org.junit.jupiter.api.Test;

import java.io.File;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class FileExchangeStoreTutorialTest extends AbstractOperationTutorialTest {

    @Override
    protected String getTutorialYaml() {
        return "70-FileExchangeStore.yaml";
    }

    @Test
    void writesExchangeFilesToDisk() throws InterruptedException {
        given()
        .when()
            .get("http://localhost:2000/")
        .then()
            .statusCode(200);

        Thread.sleep(300);

        assertTrue(containsMsgFile(new File(baseDir, "exchanges")),
                "Expected *.msg files under exchanges/ after a request");
    }

    private boolean containsMsgFile(File dir) {
        if (!dir.exists() || !dir.isDirectory()) return false;
        File[] files = dir.listFiles();
        if (files == null) return false;
        for (File f : files) {
            if (f.isDirectory() && containsMsgFile(f)) return true;
            if (f.isFile() && f.getName().endsWith(".msg")) return true;
        }
        return false;
    }
}
