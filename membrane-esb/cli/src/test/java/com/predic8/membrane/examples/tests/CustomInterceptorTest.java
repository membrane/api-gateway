package com.predic8.membrane.examples.tests;

import static com.predic8.membrane.examples.AssertUtils.getAndAssert200;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;

import org.apache.commons.io.FileUtils;
import org.junit.Assert;
import org.junit.Test;

import com.predic8.membrane.examples.DistributionExtractingTestcase;
import com.predic8.membrane.examples.ScriptLauncher;
import com.predic8.membrane.examples.util.BufferLogger;
import com.predic8.membrane.examples.util.SubstringWaitableConsoleEvent;

public class CustomInterceptorTest extends DistributionExtractingTestcase {
	@Test
	public void test() throws IOException, InterruptedException {
		File baseDir = getExampleDir("custom-interceptor");
		
		BufferLogger b = new BufferLogger();
		ScriptLauncher ant = new ScriptLauncher(baseDir).startExecutable("ant compile", b);
		try {
			int exitCode = ant.waitFor(60000);
			System.out.println(b.toString());
			if (exitCode != 0)
				throw new RuntimeException("Ant exited with code " + exitCode + ": " + b.toString());
		} finally {
			ant.killScript();
		}
		
		FileUtils.copyDirectoryToDirectory(new File(baseDir, "build/classes"), getMembraneHome());
		
		ScriptLauncher sl = new ScriptLauncher(baseDir).startScript("router");
		try {
			SubstringWaitableConsoleEvent invoked = new SubstringWaitableConsoleEvent(sl, "MyInterceptor invoked");
			getAndAssert200("http://localhost:2000/");
			assertTrue(invoked.occurred());
		} finally {
			sl.killScript();
		}
	}

}
