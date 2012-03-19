package com.predic8.membrane.examples.tests;

import static com.predic8.membrane.examples.AssertUtils.assertContains;
import static com.predic8.membrane.examples.AssertUtils.getAndAssert200;

import java.io.File;
import java.io.IOException;

import org.junit.Test;

import com.predic8.membrane.examples.DistributionExtractingTestcase;
import com.predic8.membrane.examples.Process2;

public class REST2SOAPJSONTest extends DistributionExtractingTestcase {
	
	@Test
	public void test() throws IOException, InterruptedException {
		File baseDir = getExampleDir("rest2soap-json");
		Process2 sl = new Process2.Builder().in(baseDir).script("router").waitForMembrane().start();
		try {
			assertContains("\"ns1:bic\":\"COLSDE33XXX\"", getAndAssert200("http://localhost:2000/bank/37050198"));
			assertContains("\"ns1:bic\":\"GENODE61KIR\"", getAndAssert200("http://localhost:2000/bank/66762332"));
		} finally {
			sl.killScript();
		}
	}


}
