package com.predic8.membrane.examples.tests;

import static com.predic8.membrane.examples.AssertUtils.disableHTTPAuthentication;
import static com.predic8.membrane.examples.AssertUtils.getAndAssert;
import static com.predic8.membrane.examples.AssertUtils.getAndAssert200;
import static com.predic8.membrane.examples.AssertUtils.setupHTTPAuthentication;

import java.io.File;
import java.io.IOException;

import org.junit.Test;

import com.predic8.membrane.examples.DistributionExtractingTestcase;
import com.predic8.membrane.examples.Process2;

public class BasicAuthTest extends DistributionExtractingTestcase {
	public static final String CUSTOMER_HOST_LOCAL = "http://localhost:2000/";
	public static final String CUSTOMER_HOST_REMOTE = "http://www.thomas-bayer.com/";
	public static final String CUSTOMER_PATH = "sqlrest/CUSTOMER/7/";
	
	@Test
	public void test() throws IOException, InterruptedException {
		File baseDir = getExampleDir("basic-auth");
		Process2 sl = new Process2.Builder().in(baseDir).script("router").waitForMembrane().start();
		try {
			disableHTTPAuthentication();
			getAndAssert(401, CUSTOMER_HOST_LOCAL + CUSTOMER_PATH);

			setupHTTPAuthentication("localhost", 2000, "alice", "membrane");
			getAndAssert200(CUSTOMER_HOST_LOCAL + CUSTOMER_PATH);
		} finally {
			sl.killScript();
		}
	}

}
