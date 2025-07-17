package com.predic8.membrane.examples.withinternet.test;

import com.predic8.membrane.examples.util.DistributionExtractingTestcase;
import com.predic8.membrane.examples.util.Process2;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;

public class ConfigurationPropertiesTest extends DistributionExtractingTestcase {

    @Override
    protected String getExampleDirName() {
        return "extending-membrane/configuration-properties";
    }

    @Test
    public void test() throws Exception {
        try (Process2 process2 = startServiceProxyScript()) {
            // @formatter:off
            given()
                    .redirects().follow(false)
                    .get("http://localhost:2000/")
                    .then()
                    .statusCode(301);
            // @formatter:on
        }
    }
}
