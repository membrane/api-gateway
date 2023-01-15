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

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.TransformerFactoryConfigurationError;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;

import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import static java.util.Objects.requireNonNull;
import static org.junit.jupiter.api.Assertions.assertEquals;

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
				).evaluate(d, XPathConstants.NODESET);
		for (int i = 0; i < l.getLength(); i++) {
			Element e = (Element) l.item(i);
			String newValue = handler.handle(child, e.getFirstChild().getNodeValue());
			e.getFirstChild().setNodeValue(newValue);
		}
		TransformerFactory.newInstance().newTransformer().transform(new DOMSource(d), new StreamResult(child));
	}

	private static void handleFactoryPath(File child) throws XPathExpressionException, SAXException, IOException, ParserConfigurationException, TransformerException, TransformerFactoryConfigurationError {
		DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
		//dbf.setNamespaceAware(true);
		Document d = dbf.newDocumentBuilder().parse(child);
		XPathFactory pf = XPathFactory.newInstance();
		XPath p = pf.newXPath();
		NodeList l = (NodeList) p.compile("//factorypath/factorypathentry[@kind='VARJAR' and contains(@id, 'service-proxy-annot')]").evaluate(d, XPathConstants.NODESET);
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
