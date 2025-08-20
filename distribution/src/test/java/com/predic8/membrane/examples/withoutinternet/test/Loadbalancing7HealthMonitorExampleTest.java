package com.predic8.membrane.examples.withoutinternet.test;

import com.predic8.membrane.examples.util.AbstractSampleMembraneStartStopTestcase;
import com.predic8.membrane.examples.util.Process2;
import com.predic8.membrane.test.HttpAssertions;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static io.restassured.RestAssured.get;
import static java.lang.Thread.sleep;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;

public class Loadbalancing7HealthMonitorExampleTest extends AbstractSampleMembraneStartStopTestcase {

    @Override
    protected String getExampleDirName() {
        return "loadbalancing/7-healthMonitor";
    }

    @Test
    void step1BackendsReachable() {
        get("http://localhost:8001/")
                .then().statusCode(200)
                .body(containsString("Hello from backend 1!"));

        get("http://localhost:8002/")
                .then().statusCode(200)
                .body(containsString("Hello from backend 2!"));
    }

    @Test
    void step2LbAlternates() {
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < 2; i++) {
            String body = get("http://localhost:8000/").then()
                    .statusCode(200)
                    .extract().asString();
            if (body.contains("backend 1")) seen.add("b1");
            if (body.contains("backend 2")) seen.add("b2");
            if (seen.size() == 2) break;
        }
        assertThat("LB should reach both backends within a two requests", seen.size(), equalTo(2));
    }

    @Test
    void step3AdminShowsNodesUp() {
        String url = "http://localhost:9000/admin/clusters/show?balancer=Demo Balancer&cluster=Production";

        String html = get(url).then().statusCode(200).extract().asString();

        assertThat(html, containsString("localhost:8001"));
        assertThat(html, containsString("localhost:8002"));

        assertThat(html, containsString("UP"));
    }

    @Test
    void step4SimulateNodeDown() throws Exception {
        if (process != null)
            process.killScript();

        setNode1Delay(Paths.get(String.valueOf(baseDir), "proxies.xml"), 3000);

        try(Process2 ignored = startServiceProxyScript(null,"membrane"); HttpAssertions ha = new HttpAssertions()) {
            sleep(1000);

            String html = get("http://localhost:9000/admin/clusters/show?balancer=Demo Balancer&cluster=Production").then().statusCode(200).extract().asString();

            assertThat(html, containsString("localhost:8001"));
            assertThat(html, containsString("localhost:8002"));

            assertThat(html, containsString("DOWN"));
        }
    }

    static void setNode1Delay(Path proxiesXml, int millis) throws IOException {
        String xml = Files.readString(proxiesXml, StandardCharsets.UTF_8);
        Pattern api8001 = Pattern.compile(
                "(<api\\s+[^>]*port=\"8001\"[\\s\\S]*?<groovy>)([\\s\\S]*?Thread\\.sleep\\()\\d+(\\)[\\s\\S]*?</groovy>[\\s\\S]*?</api>)",
                Pattern.CASE_INSENSITIVE);
        Matcher m = api8001.matcher(xml);
        if (!m.find())
            throw new IllegalStateException("Could not find Node 1 <groovy> Thread.sleep(...) in " + proxiesXml);

        Files.writeString(proxiesXml, m.replaceFirst(
                Matcher.quoteReplacement(m.group(1)) + m.group(2) + millis + m.group(3)
        ), StandardCharsets.UTF_8, StandardOpenOption.TRUNCATE_EXISTING);
    }


}