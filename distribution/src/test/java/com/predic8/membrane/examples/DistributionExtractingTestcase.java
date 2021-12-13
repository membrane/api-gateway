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

package com.predic8.membrane.examples;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Enumeration;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import org.apache.commons.io.FileUtils;
import org.junit.After;
import org.junit.Before;

/**
 * Extracts the .zip distribution built by Maven.
 */
public class DistributionExtractingTestcase {
	private File targetDir, unzipDir, membraneHome;

	@Before
	public void init() throws IOException, InterruptedException {
		targetDir = new File("target").getCanonicalFile();
		if (!targetDir.exists())
			throw new RuntimeException("membraneHome " + targetDir.getName() + " does not exist.");

		File zip = null;
		{
			File[] files = targetDir.listFiles(new FilenameFilter() {
				@Override
				public boolean accept(File dir, String name) {
					return name.startsWith("membrane-service-proxy") && name.endsWith(".zip");
				}
			});
			if (files.length > 1)
				throw new RuntimeException("found more than one service-proxy*.zip");
			if (files.length == 1)
				zip = files[0];
		}

		if (zip == null)
			throw new RuntimeException("TODO: calling 'ant dist-router' automatically is not implemented.");

		unzipDir = new File(targetDir, "examples-automatic");
		if (unzipDir.exists()) {
			recursiveDelete(unzipDir);
			Thread.sleep(1000);
		}
		if (!unzipDir.mkdir())
			throw new RuntimeException("Could not mkdir " + unzipDir.getAbsolutePath());

		System.out.println("unzipping router distribution [" + getClass().getSimpleName() + "]...");
		unzip(zip, unzipDir);

		membraneHome = unzipDir.listFiles(new FilenameFilter() {
			@Override
			public boolean accept(File dir, String name) {
				return name.startsWith("membrane-service-proxy");
			}
		})[0];

		replaceLog4JConfig();

		System.out.println("running test...");
	}

	private void replaceLog4JConfig() throws IOException {
		File log4jproperties = new File(membraneHome, "conf" + File.separator + "log4j2.xml");
		if (!log4jproperties.exists())
			throw new RuntimeException("log4j2.xml does not exits.");

		FileUtils.writeStringToFile(
				log4jproperties,
				"<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
						"<Configuration>\n" +
						"    <Appenders>\n" +
						"        <Console name=\"STDOUT\" target=\"SYSTEM_OUT\">\n" +
						"            <PatternLayout pattern=\"%d{ABSOLUTE} %5p %c{1}:%L - %m%n\" />\n" +
						"        </Console>\n" +
						"    </Appenders>\n" +
						"    <Loggers>\n" +
						"        <Logger name=\"com.predic8\" level=\"debug\" />\n" +
						"        <Root level=\"warn\">\n" +
						"            <AppenderRef ref=\"STDOUT\" />\n" +
						"        </Root>\n" +
						"    </Loggers>\n" +
						"</Configuration>");
	}

	public File getExampleDir(String name) {
		File exampleDir = new File(membraneHome, "examples" + File.separator + name);
		if (!exampleDir.exists())
			throw new RuntimeException("Example dir " + exampleDir.getAbsolutePath() + " does not exist.");
		return exampleDir;
	}

	public File getMembraneHome() {
		return membraneHome;
	}

	@After
	public void done() {
		System.out.println("cleaning up...");
		recursiveDelete(unzipDir);
		System.out.println("done.");
	}

	private void recursiveDelete(File file) {
		if (file.isDirectory())
			for (File child : file.listFiles())
				recursiveDelete(child);
		if (!file.delete())
			throw new RuntimeException("could not delete " + file.getAbsolutePath());
	}

	public static final void unzip(File zip, File target) throws IOException {
		ZipFile zipFile = new ZipFile(zip);
		Enumeration<? extends ZipEntry> entries = zipFile.entries();
		while (entries.hasMoreElements()) {
			ZipEntry entry = entries.nextElement();
			if (entry.isDirectory()) {
				// Assume directories are stored parents first then children.
				// This is not robust, just for demonstration purposes.
				new File(target, entry.getName()).mkdir();
			} else {
				FileOutputStream fos = new FileOutputStream(new File(target, entry.getName()));
				try {
					copyInputStream(zipFile.getInputStream(entry),
							new BufferedOutputStream(fos));
				} finally {
					fos.close();
				}
			}
		}
		zipFile.close();
	}

	public static final void copyInputStream(InputStream in, OutputStream out)
			throws IOException {
		byte[] buffer = new byte[1024];
		int len;

		while ((len = in.read(buffer)) >= 0)
			out.write(buffer, 0, len);

		in.close();
		out.close();
	}

}
