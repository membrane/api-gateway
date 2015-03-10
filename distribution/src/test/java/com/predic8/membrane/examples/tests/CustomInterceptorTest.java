/* Copyright 2012 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */

package com.predic8.membrane.examples.tests;

import static com.predic8.membrane.test.AssertUtils.getAndAssert200;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;

import org.apache.commons.io.FileUtils;
import org.junit.Test;

import com.predic8.membrane.examples.DistributionExtractingTestcase;
import com.predic8.membrane.examples.Process2;
import com.predic8.membrane.examples.util.BufferLogger;
import com.predic8.membrane.examples.util.SubstringWaitableConsoleEvent;

public class CustomInterceptorTest extends DistributionExtractingTestcase {
	@Test
	public void test() throws IOException, InterruptedException {
		File baseDir = getExampleDir("custom-interceptor");
		
		BufferLogger b = new BufferLogger();
		Process2 ant = new Process2.Builder().in(baseDir).executable("ant compile").withWatcher(b).start();
		try {
			int exitCode = ant.waitFor(60000);
			if (exitCode != 0)
				throw new RuntimeException("Ant exited with code " + exitCode + ": " + b.toString());
		} finally {
			ant.killScript();
		}
		
		FileUtils.copyDirectoryToDirectory(new File(baseDir, "build/classes"), getMembraneHome());
		
		Process2 sl = new Process2.Builder().in(baseDir).script("service-proxy").waitForMembrane().start();
		try {
			SubstringWaitableConsoleEvent invoked = new SubstringWaitableConsoleEvent(sl, "MyInterceptor invoked");
			getAndAssert200("http://localhost:2000/");
			assertTrue(invoked.occurred());
		} finally {
			sl.killScript();
		}
	}

}
