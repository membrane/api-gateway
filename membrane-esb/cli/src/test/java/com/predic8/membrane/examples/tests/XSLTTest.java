package com.predic8.membrane.examples.tests;

import static com.predic8.membrane.examples.AssertUtils.getAndAssert200;

import java.io.File;
import java.io.IOException;

import org.junit.Test;

import com.predic8.membrane.examples.AssertUtils;
import com.predic8.membrane.examples.DistributionExtractingTestcase;
import com.predic8.membrane.examples.ScriptLauncher;

public class XSLTTest extends DistributionExtractingTestcase {
	
	@Test
	public void test() throws IOException, InterruptedException {
		File baseDir = getExampleDir("xslt");
		ScriptLauncher sl = new ScriptLauncher(baseDir).startScript("router");
		try {
			String result = getAndAssert200(BasicAuthTest.CUSTOMER_HOST_REMOTE + BasicAuthTest.CUSTOMER_PATH);
			AssertUtils.assertContains("FIRSTNAME", result);
			
			result = getAndAssert200(BasicAuthTest.CUSTOMER_HOST_LOCAL + BasicAuthTest.CUSTOMER_PATH);
			AssertUtils.assertContains("first", result);
		} finally {
			sl.killScript();
		}
	}


}
