package com.predic8.membrane.examples.tests;

import static com.predic8.membrane.examples.AssertUtils.getAndAssert200;

import java.io.File;
import java.io.IOException;

import org.junit.Test;

import com.predic8.membrane.examples.AssertUtils;
import com.predic8.membrane.examples.DistributionExtractingTestcase;
import com.predic8.membrane.examples.Process2;

public class REST2SOAPTest extends DistributionExtractingTestcase {
	
	@Test
	public void test() throws IOException, InterruptedException {
		File baseDir = getExampleDir("rest2soap");
		Process2 sl = new Process2.Builder().in(baseDir).script("router").waitForMembrane().start();
		try {
			AssertUtils.assertContains("<ns1:bic>COLSDE33XXX</ns1:bic>", getAndAssert200("http://localhost:2000/bank/37050198"));
			AssertUtils.assertContains("<ns1:bic>GENODE61KIR</ns1:bic>", getAndAssert200("http://localhost:2000/bank/66762332"));
		} finally {
			sl.killScript();
		}
	}


}
