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
package com.predic8.membrane.core.interceptor.xmlcontentfilter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import javax.xml.namespace.QName;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.stream.XMLStreamException;
import javax.xml.transform.dom.DOMSource;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;

import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import com.predic8.membrane.core.http.Message;
import com.predic8.membrane.core.interceptor.xmlcontentfilter.SimpleXPathParser.ContainerNode;
import com.predic8.membrane.core.multipart.XOPReconstitutor;

/**
 * Takes action on XML documents based on an XPath 2.0 expression. The only action
 * as of writing is {@link #removeMatchingElements(Message)}.
 */
public class XMLContentFilter {

	private final XPathExpression xpe;

	/** 
	 * The elementFinder is only used for improved performance: It can make
	 * a first decision whether the XPath expression has any chance of succeeding
	 * (if the XPath expression is simple enough, see {@link #createElementFinder(String)}).
	 * 
	 * That decision is made (here is the performance gain) using a StAX parser and without 
	 * a DOM.
	 */
	private final XMLElementFinder elementFinder;

	/**
	 * @param xPath XPath 2.0 expression
	 */
	public XMLContentFilter(String xPath) throws XPathExpressionException {
		XPathFactory xpf = XPathFactory.newInstance();
		XPath xp = xpf.newXPath();
		xpe = xp.compile(xPath);
		
		elementFinder = createElementFinder(xPath);
	}

	/**
	 * Constructs an XMLElementFinder which can make a first decision whether a
	 * given XPath expression has any chance of succeeding.
	 * 
	 * This only works if the XPath expression is simple enough. (The XPath
	 * expression must be a UnionExpr consisting of PathExprs, which start with
	 * "//foo", optionally followed by "[namespace-uri()='http://bar/']").
	 * 
	 * @return the xmlElementFinder as described above, or null if the XPath
	 *         expression is too complex.
	 */
	private XMLElementFinder createElementFinder(String xPath) {
		SimpleXPathAnalyzer a = new SimpleXPathAnalyzer();
		List<ContainerNode> intersectExceptExprs = a
				.getIntersectExceptExprs(xPath);
		if (intersectExceptExprs == null)
			return null;
		List<QName> rootElements = new ArrayList<QName>();
		for (ContainerNode node : intersectExceptExprs) {
			QName n = a.getElement(node);
			if (n == null)
				return null;
			rootElements.add(n);
		}
		return new XMLElementFinder(rootElements);
	}

	/**
	 * Removes parts of an XML document based on an XPath expression.
	 * 
	 * If the message is not valid XML, it is left unchanged.
	 */
	public void removeMatchingElements(Message message) {
		try {
			if (elementFinder != null) {
				if (!elementFinder.matches(new XOPReconstitutor()
					.reconstituteIfNecessary(message)))
					return;
			}
			DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
			dbf.setNamespaceAware(true);
			dbf.setExpandEntityReferences(false);
			DocumentBuilder db = dbf.newDocumentBuilder();
			Document d = db.parse(new XOPReconstitutor().reconstituteIfNecessary(message));
			NodeList nl = (NodeList) xpe.evaluate(
					new DOMSource(d.getDocumentElement()),
					XPathConstants.NODESET);
			if (nl.getLength() > 0) {
				// change is necessary
				// TODO: if the message was XOP-reconstituted, adjust content type
				for (int i = 0; i < nl.getLength(); i++) {
					Node n = nl.item(i);
					n.getParentNode().removeChild(n);
				}
			}
		} catch (SAXException e) {
			return;
		} catch (IOException e) {
			throw new RuntimeException(e);
		} catch (XMLStreamException e) {
			return;
		} catch (ParserConfigurationException e) {
			throw new RuntimeException(e);
		} catch (XPathExpressionException e) {
			throw new RuntimeException(e);
		}
	}

}
