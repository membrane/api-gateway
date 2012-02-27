package com.predic8.membrane.examples.tests;

import static com.predic8.membrane.examples.AssertUtils.*;

import java.io.File;
import java.io.IOException;

import org.junit.Test;
import org.parboiled.common.FileUtils;

import com.predic8.membrane.examples.DistributionExtractingTestcase;
import com.predic8.membrane.examples.ScriptLauncher;

public class CBRTest extends DistributionExtractingTestcase {
	@Test
	public void test() throws IOException, InterruptedException {
		File baseDir = getExampleDir("cbr");
		ScriptLauncher sl = new ScriptLauncher(baseDir).startScript("router");
		try {
			String result = postAndAssert200("http://localhost:2000/shop", FileUtils.readAllText(new File(baseDir, "order.xml")));
			assertContains("Normal order received.", result);

			result = postAndAssert200("http://localhost:2000/shop", FileUtils.readAllText(new File(baseDir, "express.xml")));
			assertContains("Express order received.", result);
			
			result = postAndAssert200("http://localhost:2000/shop", FileUtils.readAllText(new File(baseDir, "import.xml")));
			assertContains("Order contains import items.", result);
		} finally {
			sl.killScript();
		}
	}


}
