package com.predic8.membrane.examples.env;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
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

import org.junit.Assert;
import org.junit.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

/**
 * This test checks that all version numbers in the miscellaneous project files
 * are the same.
 */
public class ConsistentVersionNumbers {

	String version;
	
	@Test
	public void doit() throws Exception {
		File base = new File("..");
		
		validateBase(base);

		handler = new Handler() {
			@Override
			public String handle(File file, String old) {
				if (version == null)
					version = old;
				else {
					try {
						Assert.assertEquals(version, old);
					} catch (RuntimeException e) {
						throw new RuntimeException("in file " + file.getAbsolutePath(), e);
					}
				} 
				return old;
			}
		};

		run(base);
	}
	
	
	
	static Handler handler;
	
	public static void main(String[] args) throws Exception {
		File base = new File("..");
		//base = new File("C:\\Users\\tobias\\git\\membrane\\service-proxy");

		validateBase(base);
		
		handler = new Handler() {
			@Override
			public String handle(File file, String old) {
				System.out.println(old + " " + file.getAbsolutePath());
				return old;
			}
		};

		run(base);
		
		System.out.println("Please enter the new version:");
		BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
		final String version = br.readLine();
		
		handler = new Handler() {
			@Override
			public String handle(File file, String old) {
				return version;
			}
		};

		run(base);
	}

	private static void run(File base) throws Exception {
		recurse(base, 2);
		
		handlePOM(new File(base.getAbsolutePath() + "/cli/examples/embedding-java/pom.xml"), false);
		handleJBossDeploymentStructure(new File(base.getAbsolutePath() + "/sar/src/main/resources/META-INF/jboss-deployment-structure.xml"));
	}


	private static void handleJBossDeploymentStructure(File file) throws Exception {
		DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
		//dbf.setNamespaceAware(true);
		Document d = dbf.newDocumentBuilder().parse(file);
		XPathFactory pf = XPathFactory.newInstance();
		XPath p = pf.newXPath();
		NodeList l = (NodeList) p.compile(
				"//jboss-deployment-structure/deployment/resources/resource-root"
				).evaluate(d, XPathConstants.NODESET);
		for (int i = 0; i < l.getLength(); i++) {
			Element e = (Element) l.item(i);
			String oldValue = e.getAttribute("path");
			if (oldValue != null && oldValue.length() != 0) {
				String prefix = "lib/service-proxy-core-";
				String suffix = ".jar";
				if (oldValue.startsWith(prefix) && oldValue.endsWith(suffix)) {
					String oldVersion = oldValue.substring(prefix.length(), oldValue.length() - suffix.length());
					String newVersion = handler.handle(file, oldVersion);
					e.setAttribute("path", prefix + newVersion + suffix);
				}
			}
		}
		TransformerFactory.newInstance().newTransformer().transform(new DOMSource(d), new StreamResult(file));
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
		for (File child : base.listFiles()) { 
			if (child.isFile() && child.getName().equals("pom.xml"))
				handlePOM(child, true);
			if (child.isFile() && child.getName().equals(".factorypath"))
				handleFactoryPath(child);
			if (child.isDirectory())
				recurse(child, i-1);
		}
	}
	
	private static void handlePOM(File child, boolean isPartOfProject) throws XPathExpressionException, SAXException, IOException, ParserConfigurationException, TransformerConfigurationException, TransformerException, TransformerFactoryConfigurationError {
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

	private static void handleFactoryPath(File child) throws XPathExpressionException, SAXException, IOException, ParserConfigurationException, TransformerConfigurationException, TransformerException, TransformerFactoryConfigurationError {
		DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
		//dbf.setNamespaceAware(true);
		Document d = dbf.newDocumentBuilder().parse(child);
		XPathFactory pf = XPathFactory.newInstance();
		XPath p = pf.newXPath();
		NodeList l = (NodeList) p.compile("//factorypath/factorypathentry[@kind='VARJAR' and contains(@id, 'service-proxy-annot')]").evaluate(d, XPathConstants.NODESET);
		for (int i = 0; i < l.getLength(); i++) {
			Element e = (Element) l.item(i);
			// "M2_REPO\org\membrane-soa\service-proxy\service-proxy-annot\4.0.3\service-proxy-annot-4.0.3.jar"
			Matcher m = Pattern.compile("service-proxy-annot-(.*?).jar").matcher(e.getAttribute("id"));
			if (!m.find())
				throw new RuntimeException("Could not match: " + e.getAttribute("id"));
			String newValue = handler.handle(child, m.group(1));
			e.setAttribute("id", "M2_REPO\\org\\membrane-soa\\service-proxy\\service-proxy-annot\\" + newValue + "\\service-proxy-annot-" + newValue + ".jar");
		}
		TransformerFactory.newInstance().newTransformer().transform(new DOMSource(d), new StreamResult(child));
	}

	private static interface Handler {
		public String handle(File file, String old);
	}

}
