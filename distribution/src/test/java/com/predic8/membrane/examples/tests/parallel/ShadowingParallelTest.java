package com.predic8.membrane.examples.tests.parallel;

import com.predic8.membrane.examples.util.AbstractSampleMembraneStartStopTestcase;
import com.predic8.membrane.examples.util.BufferLogger;
import com.predic8.membrane.examples.util.Process2;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static com.predic8.membrane.test.AssertUtils.assertContains;
import static io.restassured.RestAssured.given;
import static io.restassured.RestAssured.when;
import static java.lang.Thread.sleep;
import static org.hamcrest.Matchers.equalTo;

public class ShadowingParallelTest extends AbstractSampleMembraneStartStopTestcase {

    BufferLogger logger;

    @Override
    protected String getExampleDirName() {
        return "parallel/shadowing";
    }

    @BeforeEach
    void startMembrane() throws IOException, InterruptedException {
        logger = new BufferLogger();
        process = new Process2.Builder().in(baseDir).script("service-proxy").withWatcher(logger).waitForMembrane().start();
    }

    @Test
    public void checkPrimaryFinish() {
        when()
            .get("http://localhost:2000")
        .then()
            .assertThat()
            .statusCode(200);
    }

    @Test
    public void checkSecondaryFinish() throws InterruptedException {
        when()
            .get("http://localhost:2000")
        .then()
            .assertThat()
            .statusCode(200);

        sleep(10000);

        assertContains("http://localhost:3000: HTTP/1.1 200 Ok", logger.toString());
    }
}
