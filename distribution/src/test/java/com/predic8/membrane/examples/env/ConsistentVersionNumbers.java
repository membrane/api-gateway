/* Copyright 2013 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */
package com.predic8.membrane.examples.env;

import com.vdurmont.semver4j.Semver;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.TransformerFactoryConfigurationError;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.predic8.membrane.examples.util.TestFileUtil.getFileContentAsLines;
import static com.predic8.membrane.examples.util.TestFileUtil.writeLinesToFile;
import static com.vdurmont.semver4j.Semver.SemverType.LOOSE;
import static java.util.Objects.requireNonNull;
import static javax.xml.xpath.XPathConstants.NODESET;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * This test checks that all version numbers in the miscellaneous project files
 * are the same.
 *
 * Call from distribution directory.
 * For Release, use:
 * {@code mvn test-compile exec:java -DmainClass="com.predic8.membrane.examples.env.ConsistentVersionNumbers" -Dexec.classpathScope="test" -DmainArgs="-release 5.2"}
 * For Snapshot version, use:
 * {@code mvn test-compile exec:java -DmainClass="com.predic8.membrane.examples.env.ConsistentVersionNumbers" -Dexec.classpathScope="test" -DmainArgs="-snapshot"}
 */
public class ConsistentVersionNumbers {

	public static final Option SNAPSHOT = Option.builder("snapshot").desc("Increase patch version and append SNAPSHOT tag").build();
	public static final Option RELEASE = Option.builder("release").desc("Release version").hasArg().argName("x.y.z").build();

	@Test
	public void doit() throws Exception {
		AtomicReference<Semver> version = new AtomicReference<>(null);
		File base = new File("..");
		validateBase(base);

		transformRecursively(base, (file, old) -> {
			if (version.get() == null)
				version.set(old);
			else {
				try {
					System.out.println(file);
					assertTrue(version.get().isEqualTo(old) || removePatchVersion(version.get()).isEqualTo(old));
				} catch (RuntimeException e) {
					throw new RuntimeException("in file " + file.getAbsolutePath(), e);
				}
			}
			return old;
		});
	}

	private Semver removePatchVersion(Semver version) {
		return new MembraneVersion("%d.%d".formatted(version.getMajor(), version.getMinor()));
	}

	@Test
	public void testRemovePatch() {
		assertTrue(removePatchVersion(new MembraneVersion("5.0.0")).isEqualTo("5.0"));
	}

	public static void main(String[] args) throws Exception {
		var cl = new DefaultParser().parse(getOptions(), args, true);
		if (!cl.hasOption(SNAPSHOT) && !cl.hasOption(RELEASE)) {
			new HelpFormatter().printHelp("ConsistentVersionNumbers", getOptions());
			System.exit(1);
		}
		File base = new File("..");
		validateBase(base);

		transformRecursively(base, (file, old) -> {
			System.out.println(old + " " + file.getAbsolutePath());
			return old;
		});

		if (cl.hasOption(RELEASE)) {
			var version = new MembraneVersion(cl.getOptionValue(RELEASE)).withClearedSuffix();
			transformRecursively(base, (file, old) -> version);
		} else if (cl.hasOption(SNAPSHOT)) {
			var version = readPOMVersion(new File(base, "pom.xml")).withIncPatch().withSuffix("SNAPSHOT");
			transformRecursively(base, (file, old) -> version);
		}
	}

	private static Options getOptions() {
		var options = new Options();
		options.addOption(SNAPSHOT);
		options.addOption(RELEASE);
		return options;
	}

	private static void transformRecursively(File baseDirectory, VersionTransformer versionTransformer) throws Exception {
		recurse(baseDirectory, versionTransformer, 2);

		handlePOM(new File(baseDirectory.getAbsolutePath(), "/distribution/examples/embedding-java/pom.xml"), false, versionTransformer);
		handlePOM(new File(baseDirectory.getAbsolutePath(), "/distribution/examples/stax-interceptor/pom.xml"), false, versionTransformer);

		handleHelpReference(new File(baseDirectory.getAbsolutePath(), "/annot/src/main/java/com/predic8/membrane/annot/generator/HelpReference.java"), versionTransformer);
		handleRpmSpec(new File(baseDirectory.getAbsolutePath(), "/membrane.spec"), versionTransformer);
		handleConstants(new File(baseDirectory.getAbsolutePath(), "core/src/main/java/com/predic8/membrane/core/Constants.java"), versionTransformer);
	}

	private static void handleConstants(File file, VersionTransformer versionTransformer) throws Exception {
		//		String version = "5"; // fallback
		Pattern versionPattern = Pattern.compile("(\\s*String version = \")(\\d+)(\";.*)");
		handleByRegex(file, versionTransformer, versionPattern, v -> v.getMajor().toString());
	}

	private static void handleRpmSpec(File file, VersionTransformer versionTransformer) throws Exception {
		// Version:          5.1.0
		Pattern versionPattern = Pattern.compile("(Version:\\s+)(\\S+)(.*)");
		handleByRegex(file, versionTransformer, versionPattern, Semver::getValue);
	}

	private static void handleHelpReference(File file, VersionTransformer versionTransformer) throws Exception {
		// path.replace("%VERSION%", "5.0")
		Pattern versionPattern = Pattern.compile("(path.replace\\(\"%VERSION%\", \")([^\"]*)(\"\\))");
		handleByRegex(file, versionTransformer, versionPattern, v -> "%d.%d".formatted(v.getMajor(), v.getMinor()));
	}

	private static void handleByRegex(File file, VersionTransformer versionTransformer, Pattern pattern, Function<Semver, String> versionFormatter) throws Exception {
		List<String> content = getFileContentAsLines(file);
		boolean found = false;
		for (int i = 0; i < content.size(); i++) {
			Matcher m = pattern.matcher(content.get(i));
			if (m.find()) {
				found = true;
				var v = versionTransformer.map(file, new MembraneVersion(m.group(2)));
				content.set(i, m.replaceFirst(m.group(1) + versionFormatter.apply(v) + m.group(3)));
			}
		}
		if (!found)
			throw new RuntimeException("Did not find version in %s .".formatted(file.getName()));
		writeLinesToFile(file, content);
	}

	private static void validateBase(File base) throws Exception {
		if (!base.exists() || !base.isDirectory())
			throw new Exception();

		if (!new File(base, "pom.xml").exists())
			throw new Exception("Could not find Membrane's main pom.xml.");

		if (!new File(new File(base, "core"), "pom.xml").exists())
			throw new Exception("Could not find Membrane's main core/pom.xml.");
	}

	private static void recurse(File directory, VersionTransformer versionTransformer, int i) throws Exception {
		if (i == 0)
			return;
		for (File child : requireNonNull(directory.listFiles())) {
			if (child.isFile() && child.getName().equals("pom.xml"))
				handlePOM(child, true, versionTransformer);
			if (child.isFile() && child.getName().equals(".factorypath"))
				handleFactoryPath(child, versionTransformer);
			if (child.isDirectory())
				recurse(child, versionTransformer, i-1);
		}
	}

	private static MembraneVersion readPOMVersion(File pomFilename) throws Exception {
		Document d = readDocument(pomFilename);
		NodeList l = (NodeList) getNodeSelector(true).evaluate(d, NODESET);
		if (l.getLength() == 0) {
			throw new Exception("could not find version number");
		}
		return readVersion(l.item(0));
	}
	private static void handlePOM(File pomFilename, boolean isPartOfProject, VersionTransformer versionTransformer) throws XPathExpressionException, SAXException, IOException, ParserConfigurationException, TransformerException, TransformerFactoryConfigurationError {
		Document d = readDocument(pomFilename);
		NodeList l = (NodeList) getNodeSelector(isPartOfProject).evaluate(d, NODESET);
		for (int i = 0; i < l.getLength(); i++) {
			Element e = (Element) l.item(i);
			e.getFirstChild().setNodeValue(versionTransformer.map(pomFilename, readVersion(l.item(i))).getValue());
		}
		TransformerFactory.newInstance().newTransformer().transform(new DOMSource(d), new StreamResult(pomFilename));
	}

	private static Document readDocument(File pomFilename) throws SAXException, IOException, ParserConfigurationException {
		//System.out.println(pomFilename.getName());
		DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
		//dbf.setNamespaceAware(true);
		return dbf.newDocumentBuilder().parse(pomFilename);
	}

	private static MembraneVersion readVersion(Node node) {
		return new MembraneVersion(node.getFirstChild().getNodeValue());
	}

	private static XPathExpression getNodeSelector(boolean isPartOfProject) throws XPathExpressionException {
		XPathFactory pf = XPathFactory.newInstance();
		XPath p = pf.newXPath();
		return p.compile(
				isPartOfProject ?
						"//project/version | //project/parent/version" :
						"//project/dependencies/dependency[./artifactId='service-proxy-core']/version"
		);

	}

	private static void handleFactoryPath(File factoryPath, VersionTransformer versionTransformer) throws XPathExpressionException, SAXException, IOException, ParserConfigurationException, TransformerException, TransformerFactoryConfigurationError {
		Document d = readDocument(factoryPath);
		XPathFactory pf = XPathFactory.newInstance();
		XPath p = pf.newXPath();
		NodeList l = (NodeList) p.compile("//factorypath/factorypathentry[@kind='VARJAR' and contains(@id, 'service-proxy-annot')]").evaluate(d, NODESET);
		for (int i = 0; i < l.getLength(); i++) {
			Element e = (Element) l.item(i);
			// "M2_REPO/org/membrane-soa/service-proxy-annot/4.0.3/service-proxy-annot-4.0.3.jar"
			Matcher m = Pattern.compile("service-proxy-annot-(.*?).jar").matcher(e.getAttribute("id"));
			if (!m.find())
				throw new RuntimeException("Could not match: " + e.getAttribute("id"));
			String newValue = versionTransformer.map(factoryPath, new MembraneVersion(m.group(1))).getValue();
			e.setAttribute("id", "M2_REPO/org/membrane-soa/service-proxy-annot/" + newValue + "/service-proxy-annot-" + newValue + ".jar");
		}
		TransformerFactory.newInstance().newTransformer().transform(new DOMSource(d), new StreamResult(factoryPath));
	}

	private static class MembraneVersion extends Semver {

		public MembraneVersion(String value) {
			super(value, LOOSE);
		}

		@Override
		public Semver withIncPatch() {
			if (this.getMinor() == null) {
				new MembraneVersion("%d.%d".formatted(this.getMajor(), 0)).withIncPatch();
			}
			if (this.getPatch() == null) {
				return new MembraneVersion("%d.%d.%d".formatted(this.getMajor(), this.getMinor(), 0)).withIncPatch();
			}
			return super.withIncPatch();
		}
	}

	private interface VersionTransformer {
		Semver map(File file, Semver old);
	}
}
