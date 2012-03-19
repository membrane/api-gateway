package com.predic8.membrane.examples.tests.validation;

import static com.predic8.membrane.examples.AssertUtils.getAndAssert;

import java.io.File;
import java.io.IOException;

import org.junit.Test;

import com.predic8.membrane.examples.DistributionExtractingTestcase;
import com.predic8.membrane.examples.Process2;

public class FormValidationTest extends DistributionExtractingTestcase {
	
	@Test
	public void test() throws IOException, InterruptedException {
		File baseDir = getExampleDir("validation" + File.separator + "form");
		Process2 sl = new Process2.Builder().in(baseDir).script("router").waitForMembrane().start();
		try {
			getAndAssert(400, "http://localhost:2000/?name=Abcde0");
			getAndAssert(200, "http://localhost:2000/?name=Abcde");
		} finally {
			sl.killScript();
		}
	}


}
