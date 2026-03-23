package com.predic8.membrane.examples.withoutinternet.test;

import com.predic8.membrane.examples.util.AbstractSampleMembraneStartStopTestcase;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.when;
import static org.hamcrest.Matchers.equalTo;

public class ConfigurationIncludesExampleTest extends AbstractSampleMembraneStartStopTestcase {

    @Override
    protected String getExampleDirName() {
        return "configuration/includes";
    }

    @Test
    void includesFromFileAndDirectoryAreLoaded() {
        // @formatter:off
        when().get("http://localhost:2000/root")
        .then()
            .statusCode(200)
            .body("source", equalTo("root"));

        when().get("http://localhost:2000/from-file")
        .then()
            .statusCode(200)
            .body("source", equalTo("from-file"));

        when().get("http://localhost:2000/nested")
        .then()
            .statusCode(200)
            .body("source", equalTo("nested"));

        when().get("http://localhost:2000/from-directory-a")
        .then()
            .statusCode(200)
            .body("source", equalTo("from-directory-a"));

        when().get("http://localhost:2000/from-directory-b")
        .then()
            .statusCode(200)
            .body("source", equalTo("from-directory-b"));
        // @formatter:on
    }

    @Test
    void includeDirectoryIgnoresNonApisYamlFiles() {
        // @formatter:off
        when().get("http://localhost:2000/ignored")
        .then()
            .statusCode(404);
        // @formatter:on
    }
}
