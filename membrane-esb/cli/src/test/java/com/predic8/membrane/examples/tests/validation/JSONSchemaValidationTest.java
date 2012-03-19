package com.predic8.membrane.examples.tests.validation;

import static com.predic8.membrane.examples.AssertUtils.postAndAssert;
import static org.apache.commons.io.FileUtils.readFileToString;

import java.io.File;
import java.io.IOException;

import org.junit.Test;

import com.predic8.membrane.examples.DistributionExtractingTestcase;
import com.predic8.membrane.examples.Process2;

public class JSONSchemaValidationTest extends DistributionExtractingTestcase {
	
	@Test
	public void test() throws IOException, InterruptedException {
		File baseDir = getExampleDir("validation" + File.separator + "json-schema");
		Process2 sl = new Process2.Builder().in(baseDir).script("router").waitForMembrane().start();
		try {
			for (int port : new int[] { 2000, 2001 }) {
				postAndAssert(200, "http://localhost:" + port + "/", readFileToString(new File(baseDir, "good" + port + ".json")));
				postAndAssert(400, "http://localhost:" + port + "/", readFileToString(new File(baseDir, "bad" + port + ".json")));
			}

		} finally {
			sl.killScript();
		}
	}


}
