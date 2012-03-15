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
