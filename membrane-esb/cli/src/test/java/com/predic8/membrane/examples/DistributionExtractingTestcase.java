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

import org.junit.After;
import org.junit.Before;
import org.parboiled.common.FileUtils;

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
					return name.startsWith("membrane-esb") && name.endsWith(".zip");
				}
			});
			if (files.length > 1)
				throw new RuntimeException("found more than one membrane-esb*.zip");
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

		System.out.println("unzipping router distribution...");
		unzip(zip, unzipDir);
		
		membraneHome = unzipDir.listFiles(new FilenameFilter() {
			@Override
			public boolean accept(File dir, String name) {
				return name.startsWith("membrane-esb");
			}
		})[0];
		
		replaceLog4JConfig();
		
		System.out.println("running test...");
	}
	
	private void replaceLog4JConfig() {
		File log4jproperties = new File(membraneHome, "conf" + File.separator + "log4j.properties");
		if (!log4jproperties.exists())
			throw new RuntimeException("log4j.properties does not exits.");
		
		FileUtils.writeAllText( 
				"log4j.appender.stdout=org.apache.log4j.ConsoleAppender\r\n" + 
				"log4j.appender.stdout.Target=System.out\r\n" + 
				"log4j.appender.stdout.layout=org.apache.log4j.PatternLayout\r\n" + 
				"log4j.appender.stdout.layout.ConversionPattern=%d{ABSOLUTE} %5p %c{1}:%L - %m%n\r\n" + 
				"\r\n" + 
				"log4j.logger.com.predic8=debug, stdout", log4jproperties);
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
			ZipEntry entry = (ZipEntry) entries.nextElement();
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
