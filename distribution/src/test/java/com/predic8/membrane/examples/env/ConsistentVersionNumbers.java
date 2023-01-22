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

import org.junit.jupiter.api.*;
import org.w3c.dom.*;
import org.xml.sax.*;

import javax.xml.parsers.*;
import javax.xml.transform.*;
import javax.xml.transform.dom.*;
import javax.xml.transform.stream.*;
import javax.xml.xpath.*;
import java.io.*;
import java.util.*;
import java.util.regex.*;

import static com.predic8.membrane.examples.util.TestFileUtil.*;
import static java.util.Objects.*;
import static javax.xml.xpath.XPathConstants.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * This test checks that all version numbers in the miscellaneous project files
 * are the same.
 */
public class ConsistentVersionNumbers {

	static Handler handler;
	String version;

	@Test
	public void doit() throws Exception {
		File base = new File("..");
		validateBase(base);

		handler = (file, old) -> {
			if (version == null)
				version = old;
			else {
				try {
					System.out.println(file);
					assertEquals(version, old);
				} catch (RuntimeException e) {
					throw new RuntimeException("in file " + file.getAbsolutePath(), e);
				}
			}
			return old;
		};
		run(base);
	}

	public static void main(String[] args) throws Exception {
		File base = new File("..");
		validateBase(base);

		handler = (file, old) -> {
			System.out.println(old + " " + file.getAbsolutePath());
			return old;
		};

		run(base);

		System.out.println("Please enter the new version:");
		final String version = new BufferedReader(new InputStreamReader(System.in)).readLine();

		handler = (file, old) -> version;

		run(base);
	}

	private static void run(File base) throws Exception {
		recurse(base, 2);

		handlePOM(new File(base.getAbsolutePath() + "/distribution/examples/embedding-java/pom.xml"), false);
		handlePOM(new File(base.getAbsolutePath() + "/distribution/examples/stax-interceptor/pom.xml"), false);

		handleHelpReference(new File(base.getAbsolutePath() + "/annot/src/main/java/com/predic8/membrane/annot/generator/HelpReference.java"));
	}

	private static void handleHelpReference(File file) throws Exception {
		// path.replace("%VERSION%", "5.0")
		List<String> content = getFileContentAsLines(file);
		boolean found = false;
		Pattern pattern = Pattern.compile("(path.replace\\(\"%VERSION%\", \")([^\"]*)(\"\\))");
		for (int i = 0; i < content.size(); i++) {
			Matcher m = pattern.matcher(content.get(i));
			if (m.find()) {
				found = true;
				String v = handler.handle(file, m.group(2));
				// only put "$major.$minor" into HelpReference.java
				v = v.replaceAll("([^.]+\\.[^.]+)\\..*", "$1");
				content.set(i, m.replaceFirst(m.group(1) + v + m.group(3)));
			}
		}
		if (!found)
			throw new RuntimeException("Did not find version in HelpReference.java .");
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

	private static void recurse(File base, int i) throws Exception {
		if (i == 0)
			return;
		for (File child : requireNonNull(base.listFiles())) {
			if (child.isFile() && child.getName().equals("pom.xml"))
				handlePOM(child, true);
			if (child.isFile() && child.getName().equals(".factorypath"))
				handleFactoryPath(child);
			if (child.isDirectory())
				recurse(child, i-1);
		}
	}

	private static void handlePOM(File child, boolean isPartOfProject) throws XPathExpressionException, SAXException, IOException, ParserConfigurationException, TransformerException, TransformerFactoryConfigurationError {
		//System.out.println(child.getName());
		DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
		//dbf.setNamespaceAware(true);
		Document d = dbf.newDocumentBuilder().parse(child);
		XPathFactory pf = XPathFactory.newInstance();
		XPath p = pf.newXPath();
		NodeList l = (NodeList) p.compile(
				isPartOfProject ?
						"//project/version | //project/parent/version" :
							"//project/dependencies/dependency[./artifactId='service-proxy-core']/version"
				).evaluate(d, NODESET);
		for (int i = 0; i < l.getLength(); i++) {
			Element e = (Element) l.item(i);
			e.getFirstChild().setNodeValue(handler.handle(child, e.getFirstChild().getNodeValue()));
		}
		TransformerFactory.newInstance().newTransformer().transform(new DOMSource(d), new StreamResult(child));
	}

	private static void handleFactoryPath(File child) throws XPathExpressionException, SAXException, IOException, ParserConfigurationException, TransformerException, TransformerFactoryConfigurationError {
		DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
		//dbf.setNamespaceAware(true);
		Document d = dbf.newDocumentBuilder().parse(child);
		XPathFactory pf = XPathFactory.newInstance();
		XPath p = pf.newXPath();
		NodeList l = (NodeList) p.compile("//factorypath/factorypathentry[@kind='VARJAR' and contains(@id, 'service-proxy-annot')]").evaluate(d, NODESET);
		for (int i = 0; i < l.getLength(); i++) {
			Element e = (Element) l.item(i);
			// "M2_REPO/org/membrane-soa/service-proxy-annot/4.0.3/service-proxy-annot-4.0.3.jar"
			Matcher m = Pattern.compile("service-proxy-annot-(.*?).jar").matcher(e.getAttribute("id"));
			if (!m.find())
				throw new RuntimeException("Could not match: " + e.getAttribute("id"));
			String newValue = handler.handle(child, m.group(1));
			e.setAttribute("id", "M2_REPO/org/membrane-soa/service-proxy-annot/" + newValue + "/service-proxy-annot-" + newValue + ".jar");
		}
		TransformerFactory.newInstance().newTransformer().transform(new DOMSource(d), new StreamResult(child));
	}

	private interface Handler {
		String handle(File file, String old);
	}
}
