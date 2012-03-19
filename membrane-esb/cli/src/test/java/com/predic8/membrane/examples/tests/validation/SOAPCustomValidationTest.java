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
import com.predic8.membrane.examples.util.ConsoleLogger;

public class SOAPCustomValidationTest extends DistributionExtractingTestcase {
	
	@Test
	public void test() throws IOException, InterruptedException {
		File baseDir = getExampleDir("validation" + File.separator + "soap-custom");
		Process2 sl = new Process2.Builder().in(baseDir).script("router").waitForMembrane().start();
		try {
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
			Assert.assertTrue(exception[0]);
			
			File source = new File(baseDir, "src" + File.separator + "ArticleClient.java");
			FileUtils.writeStringToFile(source, readFileToString(source).replace("//aType", "aType"));
			
			exception[0] = false;
			ant = new Process2.Builder().
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
			Assert.assertFalse(exception[0]);

		} finally {
			sl.killScript();
		}
	}


}
