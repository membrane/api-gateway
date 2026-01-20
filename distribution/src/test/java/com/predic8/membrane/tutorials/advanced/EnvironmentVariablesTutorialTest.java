package com.predic8.membrane.tutorials.advanced;

import com.predic8.membrane.examples.util.ConsoleWatcher;
import com.predic8.membrane.examples.util.Process2;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;

public class EnvironmentVariablesTutorialTest extends AbstractAdvancedTutorialTest{

    @Override
    protected String getTutorialYaml() {
        return "90-Environment-Variables.yaml";
    }

    @Test
    void environmentVariablesAreResolved() {
        // @formatter:off
        given()
        .when()
            .get("http://localhost:2000/")
        .then()
            .statusCode(200)
            .body(containsString("Hello from port 3000"));
        // @formatter:on
    }

    @Override
    protected Process2 startServiceProxyScript(ConsoleWatcher watch, String script) throws IOException, InterruptedException {
        Process2.Builder builder = new Process2.Builder().in(baseDir).env("PORT", "3000");

        if (watch != null)
            builder = builder.withWatcher(watch);

        return builder.script(script).waitForMembrane().parameters("-c %s".formatted(getTutorialYaml())).start();
    }

}
