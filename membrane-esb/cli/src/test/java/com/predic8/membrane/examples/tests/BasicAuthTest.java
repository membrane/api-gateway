package com.predic8.membrane.examples.tests;

import static com.predic8.membrane.examples.AssertUtils.disableHTTPAuthentication;
import static com.predic8.membrane.examples.AssertUtils.getAndAssert;
import static com.predic8.membrane.examples.AssertUtils.getAndAssert200;
import static com.predic8.membrane.examples.AssertUtils.setupHTTPAuthentication;

import java.io.File;
import java.io.IOException;

import org.junit.Test;

import com.predic8.membrane.examples.DistributionExtractingTestcase;
import com.predic8.membrane.examples.ScriptLauncher;

public class BasicAuthTest extends DistributionExtractingTestcase {

	@Test
	public void test() throws IOException, InterruptedException {
		File baseDir = getExampleDir("basic-auth");
		ScriptLauncher sl = new ScriptLauncher(baseDir).startScript("router");
		try {
			String url = "http://localhost:2000/sqlrest/CUSTOMER/6/";
			
			disableHTTPAuthentication();
			getAndAssert(401, url);

			setupHTTPAuthentication("localhost", 2000, "alice", "membrane");
			getAndAssert200(url);
		} finally {
			sl.killScript();
		}
	}

}
