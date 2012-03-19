package com.predic8.membrane.examples.tests.validation;

import static org.apache.commons.io.FileUtils.readFileToString;

import java.io.File;
import java.io.IOException;

import org.apache.commons.io.FileUtils;
import org.junit.Assert;
import org.junit.Test;

import com.predic8.membrane.examples.AbstractConsoleWatcher;
import com.predic8.membrane.examples.DistributionExtractingTestcase;
import com.predic8.membrane.examples.Process2;

public class SOAPCustomValidationTest extends DistributionExtractingTestcase {
	
	@Test
	public void test() throws IOException, InterruptedException {
		File baseDir = getExampleDir("validation" + File.separator + "soap-custom");
		Process2 sl = new Process2.Builder().in(baseDir).script("router").waitForMembrane().start();
		try {
			assertAntRunProducesException(baseDir, true);
			
			File source = new File(baseDir, "src" + File.separator + "ArticleClient.java");
			FileUtils.writeStringToFile(source, readFileToString(source).replace("//aType", "aType"));
			
			assertAntRunProducesException(baseDir, false);
		} finally {
			sl.killScript();
		}
	}

	private void assertAntRunProducesException(File baseDir, boolean expectException) throws IOException, InterruptedException {
		final boolean[] exception = new boolean[1];
		Process2 ant = new Process2.Builder().
				in(baseDir).
				executable("ant run").
				withWatcher(new AbstractConsoleWatcher() {
					@Override
					public void outputLine(boolean error, String line) {
						if (line.contains("ClientTransportException"))
							exception[0] = true;
					}
				}).
				start();
		try {
			ant.waitFor(30000);
		} finally {
			ant.killScript();
		}
		Assert.assertEquals(expectException, exception[0]);
	}

}
