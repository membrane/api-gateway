package com.predic8.membrane.examples.tests;

import static com.predic8.membrane.examples.AssertUtils.getAndAssert200;

import java.io.File;
import java.io.IOException;

import org.junit.Assert;
import org.junit.Test;

import com.predic8.membrane.examples.DistributionExtractingTestcase;
import com.predic8.membrane.examples.Process2;

public class ThrottleTest extends DistributionExtractingTestcase {
	
	@Test
	public void test() throws IOException, InterruptedException {
		File baseDir = getExampleDir("throttle");
		Process2 sl = new Process2.Builder().in(baseDir).script("router").waitForMembrane().start();
		try {
			getAndAssert200("http://localhost:2000/");
			long start = System.currentTimeMillis();
			getAndAssert200("http://localhost:2000/");
			long elapsedMillis = System.currentTimeMillis() - start;
			Assert.assertTrue(elapsedMillis >= 1000);
		} finally {
			sl.killScript();
		}
	}


}
