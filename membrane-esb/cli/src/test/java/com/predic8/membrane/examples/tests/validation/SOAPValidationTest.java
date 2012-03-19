package com.predic8.membrane.examples.tests.validation;

import static com.predic8.membrane.examples.AssertUtils.postAndAssert;
import static org.apache.commons.io.FileUtils.readFileToString;

import java.io.File;
import java.io.IOException;

import org.junit.Test;

import com.predic8.membrane.examples.DistributionExtractingTestcase;
import com.predic8.membrane.examples.Process2;

public class SOAPValidationTest extends DistributionExtractingTestcase {
	
	@Test
	public void test() throws IOException, InterruptedException {
		File baseDir = getExampleDir("validation" + File.separator + "soap");
		Process2 sl = new Process2.Builder().in(baseDir).script("router").waitForMembrane().start();
		try {
			String url = "http://localhost:2000/axis2/services/BLZService/getBankResponse";
			String[] headers = new String[] { "Content-Type", "application/soap+xml" };
			postAndAssert(200, url, headers, readFileToString(new File(baseDir, "blz-soap.xml")));
			postAndAssert(400, url, headers, readFileToString(new File(baseDir, "invalid-blz-soap.xml")));
		} finally {
			sl.killScript();
		}
	}


}
