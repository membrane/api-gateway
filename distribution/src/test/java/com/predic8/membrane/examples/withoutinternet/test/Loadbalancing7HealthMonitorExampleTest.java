package com.predic8.membrane.examples.withoutinternet.test;

import com.predic8.membrane.examples.util.DistributionExtractingTestcase;
import com.predic8.membrane.examples.util.Process2;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static io.restassured.RestAssured.get;
import static io.restassured.RestAssured.given;
import static java.lang.Thread.sleep;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;

public class Loadbalancing7HealthMonitorExampleTest extends DistributionExtractingTestcase {

    public static final String ADMIN_URL = "http://localhost:9000/admin/clusters/show?balancer=Demo Balancer&cluster=Production";

    @Override
    protected String getExampleDirName() {
        return "loadbalancing/7-healthMonitor";
    }

    /** Without TLS (proxies.xml) **/

    @Test
    void http_backendsReachable() throws Exception {
        try (Process2 ignored = startServiceProxyScript()) {
            get("http://localhost:8001/")
                    .then().statusCode(200).body(containsString("Hello from backend 1!"));
            get("http://localhost:8002/")
                    .then().statusCode(200).body(containsString("Hello from backend 2!"));
        }
    }

    @Test
    void http_lbAlternates() throws Exception {
        try (Process2 ignored = startServiceProxyScript()) {
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
        try (Process2 ignored = startServiceProxyScript()) {
            String html = get(ADMIN_URL).then().statusCode(200).extract().asString();
            assertThat(html, containsString("localhost:8001"));
            assertThat(html, containsString("localhost:8002"));
            assertThat(html, containsString("UP"));
        }
    }

    @Test
    void http_simulateNodeDown() {
        withBackedUpFile("proxies.xml", () -> {
            try {
                setNode1Delay(baseDir.toPath().resolve("proxies.xml"), 3000);
                try (Process2 ignored = startServiceProxyScript()) {
                    sleep(3000);
                    String html = get(ADMIN_URL).then().statusCode(200).extract().asString();
                    assertThat(html, containsString("localhost:8001"));
                    assertThat(html, containsString("localhost:8002"));
                    assertThat(html, containsString("DOWN"));
                }
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }


    /** With TLS (proxies-tls.xml) **/

    @Test
    void https_backendsReachable() throws Exception {
        ensureCertificates();
        withTlsProxies(() -> {
            try (Process2 ignored = startServiceProxyScript()) {
                given().relaxedHTTPSValidation().when()
                        .get("https://localhost:8001/").then().statusCode(200)
                        .and().body(containsString("Hello from backend 1!"));
                given().relaxedHTTPSValidation().when()
                        .get("https://localhost:8002/").then().statusCode(200)
                        .and().body(containsString("Hello from backend 2!"));
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }

    @Test
    void https_lbAlternates() throws Exception {
        ensureCertificates();
        withTlsProxies(() -> {
            try (Process2 ignored = startServiceProxyScript()) {
                Set<String> seen = new HashSet<>();
                for (int i = 0; i < 4; i++) {
                    String body = given().relaxedHTTPSValidation().when()
                            .get("https://localhost:8000/").then().statusCode(200)
                            .extract().asString();
                    if (body.contains("backend 1")) seen.add("b1");
                    if (body.contains("backend 2")) seen.add("b2");
                    if (seen.size() == 2) break;
                }
                assertThat("LB should reach both backends within a few requests", seen.size(), equalTo(2));
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }

    @Test
    void https_adminShowsNodesUp() throws Exception {
        ensureCertificates();
        withTlsProxies(() -> {
            try (Process2 ignored = startServiceProxyScript()) {
                String html = get(ADMIN_URL).then().statusCode(200).extract().asString();
                assertThat(html, containsString("localhost:8001"));
                assertThat(html, containsString("localhost:8002"));
                assertThat(html, containsString("UP"));
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }

    @Test
    void https_simulateNodeDown() throws Exception {
        ensureCertificates();
        withTlsProxies(() -> withBackedUpFile("proxies.xml", () -> {
            try {
                setNode1Delay(baseDir.toPath().resolve("proxies.xml"), 3000);
                try (Process2 ignored = startServiceProxyScript()) {
                    sleep(3000);
                    String html = get(ADMIN_URL).then().statusCode(200).extract().asString();
                    assertThat(html, containsString("localhost:8001"));
                    assertThat(html, containsString("localhost:8002"));
                    assertThat(html, containsString("DOWN"));
                }
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }));
    }

    private void ensureCertificates() throws Exception {
        File b1 = new File(baseDir, "backend-1.p12");
        File b2 = new File(baseDir, "backend-2.p12");
        File bal = new File(baseDir, "balancer.p12");
        if (b1.exists() && b2.exists() && bal.exists())
            return;

        Process p = startScript();
        int exit = p.waitFor();
        if (exit != 0)
            throw new IllegalStateException("create-certificates script failed with exit code " + exit);
    }

    private @NotNull Process startScript() throws IOException {
        ProcessBuilder pb;
        if (isWindows() && new File(baseDir, "create-certificates.cmd").exists()) {
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

    private static boolean isWindows() {
        return System.getProperty("os.name").toLowerCase().contains("win");
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
        Files.writeString(proxiesXml, replaced, UTF_8, StandardOpenOption.TRUNCATE_EXISTING);
    }

    private void withTlsProxies(Runnable action) {
        withBackedUpFile("proxies.xml", () -> {
            try {
                Path tls = baseDir.toPath().resolve("proxies-tls.xml");
                Path std = baseDir.toPath().resolve("proxies.xml");
                Files.copy(tls, std, StandardCopyOption.REPLACE_EXISTING);
                action.run();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
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
                    Files.writeString(file, backup, UTF_8, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.CREATE);
                } catch (IOException ignored) { }
            }
        }
    }
}
