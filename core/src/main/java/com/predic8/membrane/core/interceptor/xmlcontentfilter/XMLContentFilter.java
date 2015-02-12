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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import javax.annotation.concurrent.ThreadSafe;
import javax.mail.internet.ParseException;
import javax.xml.namespace.QName;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.stream.FactoryConfigurationError;
import javax.xml.stream.XMLStreamException;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.TransformerFactoryConfigurationError;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;

import org.apache.log4j.Logger;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import com.predic8.membrane.core.http.Header;
import com.predic8.membrane.core.http.Message;
import com.predic8.membrane.core.interceptor.xmlcontentfilter.SimpleXPathParser.ContainerNode;
import com.predic8.membrane.core.multipart.XOPReconstitutor;
import com.predic8.membrane.core.util.EndOfStreamException;

/**
 * Takes action on XML documents based on an XPath expression. The only action
 * as of writing is {@link #removeMatchingElements(Message)}.
 * 
 * As even Java 7 only supports XPath 1.0, this is what this class supports.
 */
@ThreadSafe
public class XMLContentFilter {

	private static final Logger LOG = Logger.getLogger(XMLContentFilter.class);

	private final ThreadLocal<XPathExpression> xpe = new ThreadLocal<XPathExpression>();
	private final ThreadLocal<DocumentBuilder> db = new ThreadLocal<DocumentBuilder>();
	private final ThreadLocal<Transformer> t = new ThreadLocal<Transformer>();
	private final XOPReconstitutor xopReconstitutor = new XOPReconstitutor();

	/**
	 * The XPath expression.
	 */
	private final String xPath;
	
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
	 * @param xPath XPath 1.0 expression
	 */
	public XMLContentFilter(String xPath) throws XPathExpressionException {
		this.xPath = xPath;
		createXPathExpression(); // to throw XPathExpressionException early
		elementFinder = createElementFinder(xPath);
		if (elementFinder == null)
			LOG.warn("The XPath expression \"" + xPath + "\" could not be optimized to use a StAX parser as a first check. This means that for every SOAP message, a DOM tree has to be built to execute the XPath expression. This might degrade performance significantly.");
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
	static XMLElementFinder createElementFinder(String xPath) {
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
	
	private XPathExpression createXPathExpression() throws XPathExpressionException {
		XPathExpression res = xpe.get();
		if (res != null)
			return res;
		XPathFactory xpf = XPathFactory.newInstance();
		XPath xp = xpf.newXPath();
		res = xp.compile(xPath);
		xpe.set(res);
		return res;
	}
	
	private DocumentBuilder createDocumentBuilder() throws ParserConfigurationException {
		DocumentBuilder res = db.get();
		if (res != null)
			return res;
		DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
		dbf.setNamespaceAware(true);
		dbf.setExpandEntityReferences(false);
		res = dbf.newDocumentBuilder();
		db.set(res);
		return res;
	}
	
	private Transformer createTransformer() throws TransformerConfigurationException, TransformerFactoryConfigurationError {
		Transformer res = t.get();
		if (res != null)
			return res;
		res = TransformerFactory.newInstance().newTransformer();
		t.set(res);
		return res;
	}
	
	/**
	 * Removes parts of an XML document based on an XPath expression.
	 * 
	 * If the message is not valid XML, it is left unchanged.
	 */
	public void removeMatchingElements(Message message) {
		try {
			Message xop = null;
			try {
				xop = xopReconstitutor.getReconstitutedMessage(message);
			} catch (ParseException e) {
			} catch (EndOfStreamException e) {
			} catch (FactoryConfigurationError e) {
			}
			
			if (elementFinder != null &&
				!elementFinder.matches(xop != null ? xop.getBodyAsStream() : message.getBodyAsStream())) {
				return;
			}
			DocumentBuilder db = createDocumentBuilder();
			Document d;
			try {
				d = db.parse(xop != null ? xop.getBodyAsStream() : message.getBodyAsStream());
			} finally {
				db.reset();
			}
			removeElementsIfNecessary(message, xop, d);
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
		} catch (TransformerConfigurationException e) {
			throw new RuntimeException(e);
		} catch (TransformerException e) {
			throw new RuntimeException(e);
		} catch (TransformerFactoryConfigurationError e) {
			throw new RuntimeException(e);
		}
	}

	/**
	 * @param originalMessage
	 * @param xopDecodedMessage
	 * @param doc
	 * @throws XPathExpressionException
	 * @throws TransformerException
	 * @throws TransformerConfigurationException
	 * @throws TransformerFactoryConfigurationError
	 */
	private void removeElementsIfNecessary(Message originalMessage,
			Message xopDecodedMessage, Document doc)
			throws XPathExpressionException, TransformerException,
			TransformerConfigurationException,
			TransformerFactoryConfigurationError {
		NodeList toBeDeleted = (NodeList) createXPathExpression().evaluate(doc,
				XPathConstants.NODESET);
		if (toBeDeleted.getLength() > 0) {
			// change is necessary
			originalMessage.getHeader().removeFields(Header.CONTENT_ENCODING);
			if (xopDecodedMessage != null) {
				originalMessage.getHeader().removeFields(Header.CONTENT_TYPE);
				if (xopDecodedMessage.getHeader().getContentType() != null)
					originalMessage.getHeader().setContentType(xopDecodedMessage.getHeader().getContentType());
			}

			for (int i = 0; i < toBeDeleted.getLength(); i++) {
				Node n = toBeDeleted.item(i);
				n.getParentNode().removeChild(n);
			}
			
			ByteArrayOutputStream baos = new ByteArrayOutputStream();
			createTransformer().transform(new DOMSource(doc), new StreamResult(baos));
			originalMessage.setBodyContent(baos.toByteArray());
		}
	}

}
