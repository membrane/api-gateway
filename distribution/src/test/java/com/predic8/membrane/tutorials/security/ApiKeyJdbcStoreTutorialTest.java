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

package com.predic8.membrane.tutorials.security;

import com.predic8.membrane.examples.util.DistributionExtractingTestcase;
import com.predic8.membrane.examples.util.Process2;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;

import static io.restassured.RestAssured.given;
import static java.io.File.separator;
import static org.apache.commons.io.FileUtils.copyFileToDirectory;

/**
 * Runs the JDBC API key store tutorial (130-API-Key-JDBC-Store.yaml) without an
 * external database. The tutorial ships configured for PostgreSQL; before starting
 * Membrane this test rewrites the datasource to an embedded H2 database (shared with
 * the test JVM via {@code AUTO_SERVER=TRUE}) and drops the H2 driver into Membrane's
 * {@code lib} directory, mirroring what a user would do with the PostgreSQL driver.
 * <p>
 * {@code NON_KEYWORDS=KEY,SCOPE} keeps the tutorial's {@code key}/{@code scope} table
 * names usable in H2, where {@code KEY} would otherwise be a reserved word.
 */
public class ApiKeyJdbcStoreTutorialTest extends DistributionExtractingTestcase {

    private static final String TUTORIAL_YAML = "130-API-Key-JDBC-Store.yaml";

    @Override
    protected String getExampleDirName() {
        return ".." + separator + "tutorials" + separator + "security";
    }

    @Override
    protected String getParameters() {
        return "-c " + TUTORIAL_YAML;
    }

    @Test
    void rejectsRequestWithoutKeyAndAcceptsKeyStoredInDatabase() throws Exception {
        copyH2JarToMembraneLib();
        rewriteDatasourceToH2();

        try (Process2 ignored = startServiceProxyScript()) {
            insertKeys();

            // No key -> 401
            given()
            .when()
                .get("http://localhost:2000")
            .then()
                .statusCode(401);

            // Key present in the database -> authenticated and forwarded to the backend
            given()
                .header("X-Api-Key", "demo-key")
            .when()
                .get("http://localhost:2000")
            .then()
                .statusCode(200);
        }
    }

    private void insertKeys() throws Exception {
        try (Connection con = DriverManager.getConnection(h2JdbcUrl(), "postgres", "secret");
             Statement st = con.createStatement()) {
            st.executeUpdate("INSERT INTO key (apikey) VALUES ('demo-key')");
            st.executeUpdate("INSERT INTO key (apikey) VALUES ('admin-key')");
            st.executeUpdate("INSERT INTO scope (apikey, scope) VALUES ('admin-key', 'admin')");
        }
    }

    private void rewriteDatasourceToH2() throws IOException {
        replaceInFile2(TUTORIAL_YAML, "org.postgresql.Driver", "org.h2.Driver");
        replaceInFile2(TUTORIAL_YAML, "jdbc:postgresql://localhost:5432/postgres", h2JdbcUrl());
    }

    private String h2JdbcUrl() {
        return "jdbc:h2:" + new File(baseDir, "membranedb").getAbsolutePath().replace('\\', '/')
                + ";AUTO_SERVER=TRUE;NON_KEYWORDS=KEY,SCOPE";
    }

    private void copyH2JarToMembraneLib() throws IOException {
        try {
            File jar = new File(org.h2.Driver.class.getProtectionDomain()
                    .getCodeSource().getLocation().toURI());

            if (!jar.isFile() || !jar.getName().endsWith(".jar"))
                throw new AssertionError("H2 is not loaded from a jar: " + jar);

            copyFileToDirectory(jar, new File(getMembraneHome(), "lib"));
        } catch (Exception e) {
            throw new IOException("Failed to locate/copy H2 jar.", e);
        }
    }
}
