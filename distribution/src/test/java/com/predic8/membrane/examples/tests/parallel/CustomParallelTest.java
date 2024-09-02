package com.predic8.membrane.examples.tests.parallel;

import com.predic8.membrane.examples.util.AbstractSampleMembraneStartStopTestcase;
import com.predic8.membrane.examples.util.BufferLogger;
import com.predic8.membrane.examples.util.Process2;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static com.predic8.membrane.test.AssertUtils.assertContains;
import static io.restassured.RestAssured.when;
import static java.lang.Thread.sleep;
import static org.hamcrest.Matchers.containsString;

public class CustomParallelTest extends AbstractSampleMembraneStartStopTestcase {

    @Override
    protected String getExampleDirName() {
        return "parallel/custom-strategy";
    }

    @Test
    public void checkPrimaryFinish() {
        when()
            .get("http://localhost:2000")
        .then()
            .assertThat()
            .statusCode(200)
            .body(containsString("Number 1"));
    }
}
