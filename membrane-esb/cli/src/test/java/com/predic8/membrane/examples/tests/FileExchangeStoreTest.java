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

import static com.predic8.membrane.examples.AssertUtils.getAndAssert200;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;

import org.junit.Test;

import com.predic8.membrane.examples.DistributionExtractingTestcase;
import com.predic8.membrane.examples.Process2;

public class FileExchangeStoreTest extends DistributionExtractingTestcase {
	
	@Test
	public void test() throws IOException, InterruptedException {
		File baseDir = getExampleDir("file-exchangestore");
		Process2 sl = new Process2.Builder().in(baseDir).script("router").waitForMembrane().start();
		try {
			getAndAssert200("http://localhost:2000/");
			
			File exchangesDir = new File(baseDir, "exchanges");
			if (!containsRecursively(exchangesDir, new FilenameFilter() {
				@Override
				public boolean accept(File dir, String name) {
					return name.endsWith(".msg");
				}
			}))
				throw new AssertionError("Did not find *.msg in exchanges dir.");
		} finally {
			sl.killScript();
		}
	}
	
	private boolean containsRecursively(File base, FilenameFilter filter) {
		for (File f : base.listFiles()) {
			if (f.isDirectory())
				if (containsRecursively(f, filter))
					return true;
			if (f.isFile() && filter.accept(base, f.getName()))
				return true;
		}
		return false;
	}

}
