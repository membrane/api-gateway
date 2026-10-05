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
import org.h2.tools.RunScript;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.sql.DriverManager;

import static io.restassured.RestAssured.given;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.apache.commons.io.FileUtils.copyFileToDirectory;
import static org.hamcrest.Matchers.containsString;

/**
 * Runs the database Basic Authentication tutorial without Docker or PostgreSQL.
 * Before starting Membrane, the datasource is rewritten to an embedded H2 database
 * (shared with the test JVM via {@code AUTO_SERVER=TRUE}), seeded with the tutorial's
 * {@code basic-auth-users.sql}, and the H2 driver is dropped into Membrane's {@code lib}.
 */
public class BasicAuthenticationDatabaseTutorialTest extends DistributionExtractingTestcase {

    private static final String TUTORIAL_YAML = "12-Basic-Authentication-database.yaml";

    @Override
    protected String getExampleDirName() {
        return "../tutorials/security";
    }

    @Override
    protected String getParameters() {
        return "-c " + TUTORIAL_YAML;
    }

    @Test
    void requiresCredentialsAndAcceptsUsersFromDatabase() throws Exception {
        copyH2JarToMembraneLib();
        rewriteDatasourceToH2();
        createUsers();

        try (Process2 ignored = startServiceProxyScript()) {
            // @formatter:off
            given()
            .when()
                .get("http://localhost:2000")
            .then()
                .statusCode(401);

            given()
                .auth().preemptive().basic("membrane", "gateway")
            .when()
                .get("http://localhost:2000")
            .then()
                .statusCode(200)
                .body(containsString("You're in!"));

            given()
                .auth().preemptive().basic("membrane", "wrong")
            .when()
                .get("http://localhost:2000")
            .then()
                .statusCode(401);
            // @formatter:on

            insertAlice();

            // @formatter:off
            given()
                .auth().preemptive().basic("alice", "secret")
            .when()
                .get("http://localhost:2000")
            .then()
                .statusCode(200)
                .body(containsString("You're in!"));
            // @formatter:on
        }
    }

    private void createUsers() throws Exception {
        try (final var con = DriverManager.getConnection(h2JdbcUrl(), "postgres", "secret");
             final var reader = new FileReader(new File(baseDir, "basic-auth-users.sql"), UTF_8)) {
            RunScript.execute(con, reader);
        }
    }

    private void insertAlice() throws Exception {
        try (final var con = DriverManager.getConnection(h2JdbcUrl(), "postgres", "secret");
             final var st = con.createStatement()) {
            st.executeUpdate("INSERT INTO users VALUES ('alice', 'secret')");
        }
    }

    private void rewriteDatasourceToH2() throws IOException {
        replaceInFile2(TUTORIAL_YAML, "org.postgresql.Driver", "org.h2.Driver");
        replaceInFile2(TUTORIAL_YAML, "jdbc:postgresql://localhost:5432/postgres", h2JdbcUrl());
    }

    private String h2JdbcUrl() {
        return "jdbc:h2:%s;AUTO_SERVER=TRUE".formatted(
                new File(baseDir, "basicauthdb").getAbsolutePath().replace('\\', '/'));
    }

    private void copyH2JarToMembraneLib() throws IOException {
        try {
            final var jar = new File(org.h2.Driver.class.getProtectionDomain()
                    .getCodeSource().getLocation().toURI());

            if (!jar.isFile() || !jar.getName().endsWith(".jar"))
                throw new AssertionError("H2 is not loaded from a jar: " + jar);

            copyFileToDirectory(jar, new File(getMembraneHome(), "lib"));
        } catch (Exception e) {
            throw new IOException("Failed to locate/copy H2 jar.", e);
        }
    }
}
