/* Copyright 2025 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */

package com.predic8.membrane.examples.withoutinternet.test;

import com.predic8.membrane.core.util.*;
import com.predic8.membrane.examples.util.*;
import org.jetbrains.annotations.*;
import org.junit.jupiter.api.*;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.regex.*;

import static io.restassured.RestAssured.*;
import static io.restassured.filter.log.LogDetail.*;
import static java.nio.charset.StandardCharsets.*;
import static java.nio.file.StandardOpenOption.CREATE;
import static java.nio.file.StandardOpenOption.TRUNCATE_EXISTING;
import static org.hamcrest.CoreMatchers.*;
import static org.hamcrest.MatcherAssert.*;

class Loadbalancing6HealthMonitorExampleTest extends DistributionExtractingTestcase {

    static final String ADMIN_URL = "http://localhost:9000/admin/clusters/show?balancer=Default&cluster=Production";

    static final String ADMIN_URL_HTTPS = "https://localhost:9443/admin/clusters/show?balancer=Default&cluster=Production";
    static final String PROXIES_TLS_XML_OPTION = "-c proxies-tls.xml";
    public static final String DOWN = "DOWN";
    public static final String UP = "UP";

    @Override
    protected String getExampleDirName() {
        return "loadbalancing/6-health-monitor";
    }

    Process2.Builder builder;

    @BeforeEach
    void setUp() {
        builder = new Process2.Builder().in(baseDir).script("membrane").waitForMembrane();
    }

    /**
     * Without TLS (proxies.xml)
     **/
    @Test
    void http_backendsReachable() throws Exception {
        try (Process2 ignored = builder.start()) {
            // @formatter:off
            get("http://localhost:8001/")
                .then()
                    .statusCode(200)
                    .body(containsString("backend 1!"));
            get("http://localhost:8002/")
                .then()
                    .statusCode(200)
                    .body(containsString("backend 2!"));
            // @formatter:on
        }
    }

    @Test
    void http_lbAlternates() throws Exception {
        try (Process2 ignored = builder.start()) {
            Set<String> seen = new HashSet<>();
            for (int i = 0; i < 4; i++) {
                String body = get("http://localhost:8000/").then().statusCode(200).extract().asString();
                if (body.contains("backend 1")) seen.add("b1");
                if (body.contains("backend 2")) seen.add("b2");
                if (seen.size() == 2) break;
            }
            assertThat("LB should reach both backends within a few requests", seen.size(), equalTo(2));
        }
    }

    @Test
    void http_adminShowsNodesUp() throws Exception {
        try (Process2 ignored = builder.start()) {
            // @formatter:off
            String html = get(ADMIN_URL)
                            .then()
                                .log().ifValidationFails(ALL)
                                .statusCode(200)
                                .extract().asString();
            // @formatter:on
            assertThat(html, containsString("localhost:8001"));
            assertThat(html, containsString("localhost:8002"));
            assertThat(html, containsString(UP));
        }
    }

    @Test
    void http_simulateNodeDown() {
        withBackedUpFile("proxies.xml", () -> {
            try {
                setNode1Delay(baseDir.toPath().resolve("proxies.xml"), 3000);
                try (Process2 ignored = builder.start()) {
                    Thread.sleep(2200);
                    String html = get(ADMIN_URL).then().statusCode(200).extract().asString();
                    assertThat(html, containsString("localhost:8001"));
                    assertThat(html, containsString("localhost:8002"));
                    assertThat(html, containsString(DOWN));
                }
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }

    /**
     * With TLS (proxies-tls.xml)
     **/
    @Test
    void https_backendsReachable() throws Exception {
        ensureCertificates();
        try (Process2 ignored = builder.parameters(PROXIES_TLS_XML_OPTION).start()) {
            // @formatter:off
            given()
                .relaxedHTTPSValidation()
            .when()
                .get("https://localhost:8001/")
            .then()
                .statusCode(200)
                .and()
                    .body(containsString("Hello from backend 1!"));
            given()
                .relaxedHTTPSValidation()
            .when()
                .get("https://localhost:8002/").then().statusCode(200)
                .and()
                    .body(containsString("Hello from backend 2!"));
            // @formatter:on
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void https_lbAlternates() throws Exception {
        ensureCertificates();
        try (Process2 ignored = builder.parameters(PROXIES_TLS_XML_OPTION).start()) {
            Set<String> seen = new HashSet<>();
            for (int i = 0; i < 4; i++) {
                // @formatter:off
                String body = given()
                    .relaxedHTTPSValidation()
                .when()
                    .get("https://localhost:443/")
                .then()
                    .statusCode(200)
                    .extract().asString();
                // @formatter:on
                if (body.contains("backend 1")) seen.add("b1");
                if (body.contains("backend 2")) seen.add("b2");
                if (seen.size() == 2) break;
            }
            assertThat("LB should reach both backends within a few requests", seen.size(), equalTo(2));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void https_adminShowsNodesUp() throws Exception {
        ensureCertificates();
        try (Process2 ignored = builder.parameters(PROXIES_TLS_XML_OPTION).start()) {
            // @formatter:off
            String html = given()
                .relaxedHTTPSValidation()
            .get(ADMIN_URL_HTTPS).then()
                .statusCode(200)
                .extract().asString();
            // @formatter:on
            assertThat(html, containsString("localhost:8001"));
            assertThat(html, containsString("localhost:8002"));
            assertThat(html, containsString(UP));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

    }

    @Test
    void https_simulateNodeDown() throws Exception {
        ensureCertificates();
        withBackedUpFile("proxies-tls.xml", () -> {
            try {
                setNode1Delay(baseDir.toPath().resolve("proxies-tls.xml"), 3000);
                try (Process2 ignored = builder.parameters(PROXIES_TLS_XML_OPTION).start()) {
                    Thread.sleep(2200); // Wait for health check to happen
                    // @formatter:off
                    String html = given()
                        .relaxedHTTPSValidation()
                    .get(ADMIN_URL_HTTPS).then()
                        .statusCode(200)
                        .extract().asString();
                    // @formatter:on
                    assertThat(html, containsString("localhost:8001"));
                    assertThat(html, containsString("localhost:8002"));
                    assertThat(html, containsString(DOWN));
                }
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }

    private void ensureCertificates() throws Exception {
        File b1 = new File(baseDir, "certificates/backend-1.p12");
        File b2 = new File(baseDir, "certificates/backend-2.p12");
        File bal = new File(baseDir, "certificates/balancer.p12");
        if (b1.exists() && b2.exists() && bal.exists())
            return;

        Process p = startScript();
        int exit = p.waitFor();
        if (exit != 0)
            throw new IllegalStateException("create-certificates script failed with exit code " + exit);
    }

    private @NotNull Process startScript() throws IOException {
        ProcessBuilder pb;
        if (OSUtil.isWindows() && new File(baseDir, "create-certificates.cmd").exists()) {
            pb = new ProcessBuilder("cmd", "/c", "create-certificates.cmd");
        } else if (new File(baseDir, "create-certificates.sh").exists()) {
            pb = new ProcessBuilder("bash", "create-certificates.sh");
        } else {
            throw new IllegalStateException("No certificate script found (create-certificates.[cmd|sh]).");
        }
        pb.directory(baseDir);
        pb.inheritIO();
        return pb.start();
    }

    static void setNode1Delay(Path proxiesXml, int millis) throws IOException {
        String xml = Files.readString(proxiesXml, UTF_8);
        Pattern api8001 = Pattern.compile(
                "(<api\\s+[^>]*port=\"8001\"[\\s\\S]*?<groovy>)([\\s\\S]*?Thread\\.sleep\\()\\d+(\\)[\\s\\S]*?</groovy>[\\s\\S]*?</api>)",
                Pattern.CASE_INSENSITIVE);
        Matcher m = api8001.matcher(xml);
        if (!m.find())
            throw new IllegalStateException("Could not find Node 1 <groovy> Thread.sleep(...) in " + proxiesXml);
        String replaced = m.replaceFirst(Matcher.quoteReplacement(m.group(1)) + m.group(2) + millis + m.group(3));
        Files.writeString(proxiesXml, replaced, UTF_8, TRUNCATE_EXISTING);
    }

    private void withBackedUpFile(String fileName, Runnable action) {
        Path file = baseDir.toPath().resolve(fileName);
        String backup = null;
        try {
            if (Files.exists(file))
                backup = Files.readString(file, UTF_8);
            action.run();
        } catch (IOException e) {
            throw new RuntimeException(e);
        } finally {
            if (backup != null) {
                try {
                    Files.writeString(file, backup, UTF_8, TRUNCATE_EXISTING, CREATE);
                } catch (IOException ignored) {
                }
            }
        }
    }
}
