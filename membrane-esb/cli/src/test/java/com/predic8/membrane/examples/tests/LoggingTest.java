package com.predic8.membrane.examples.tests;

import static org.junit.Assert.assertEquals;

import java.io.IOException;

import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.methods.GetMethod;
import org.junit.Test;

import com.predic8.membrane.examples.AbstractConsoleWatcher;
import com.predic8.membrane.examples.DistributionExtractingTestcase;
import com.predic8.membrane.examples.ScriptLauncher;
import com.predic8.membrane.examples.util.SubstringWaitableConsoleEvent;

public class LoggingTest extends DistributionExtractingTestcase {

	@Test
	public void test() throws IOException, InterruptedException {
		ScriptLauncher sl = new ScriptLauncher(getExampleDir("logging")).startScript("router");
		try {
			SubstringWaitableConsoleEvent logged = new SubstringWaitableConsoleEvent(sl, "HTTP/1.1");
			assertEquals(200, new HttpClient().executeMethod(new GetMethod("http://localhost:2000/")));
			assertEquals(true, logged.occurred());
		} finally {
			sl.killScript();
		}
	}

}
