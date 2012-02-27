package com.predic8.membrane.examples.tests;

import static com.predic8.membrane.examples.AssertUtils.getAndAssert;
import static com.predic8.membrane.examples.AssertUtils.getAndAssert200;

import java.io.File;
import java.io.IOException;

import org.junit.Test;

import com.predic8.membrane.examples.AssertUtils;
import com.predic8.membrane.examples.DistributionExtractingTestcase;
import com.predic8.membrane.examples.Process2;

public class ACLTest extends DistributionExtractingTestcase {

	@Test
	public void test() throws IOException, InterruptedException {
		File baseDir = getExampleDir("acl");
		Process2 sl = new Process2.Builder().in(baseDir).script("router").waitForMembrane().start();
		try {
			getAndAssert200("http://localhost:2000/");
			
			String result = getAndAssert(404, "http://localhost:2000/contacts/");
			// this request succeeds through membrane, but fails on the backend with 404
			AssertUtils.assertContains("Tomcat", result);
			
			getAndAssert(403, "http://localhost:2000/open-source/");
		} finally {
			sl.killScript();
		}
	}

}
