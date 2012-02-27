package com.predic8.membrane.examples.tests;

import static org.junit.Assert.assertEquals;

import java.io.IOException;

import org.junit.Test;

import com.predic8.membrane.examples.AssertUtils;
import com.predic8.membrane.examples.DistributionExtractingTestcase;
import com.predic8.membrane.examples.ScriptLauncher;
import com.predic8.membrane.examples.util.SubstringWaitableConsoleEvent;

public class LoggingTest extends DistributionExtractingTestcase {

	@Test
	public void test() throws IOException, InterruptedException {
		ScriptLauncher sl = new ScriptLauncher(getExampleDir("logging")).startScript("router");
		try {
			SubstringWaitableConsoleEvent logged = new SubstringWaitableConsoleEvent(sl, "HTTP/1.1");
			AssertUtils.getAndAssert200("http://localhost:2000/");
			assertEquals(true, logged.occurred());
		} finally {
			sl.killScript();
		}
	}

}
